package com.bankingeconomy.service.Impl;

import com.bankingeconomy.hadoop.FailedTransactionByDistrictJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Chạy MapReduce FailedTransactionByDistrictJob và trả kết quả về controller.
 *
 * Input HDFS:  /data/transactions/province=* /district=* /year={year}/quarter={quarter}/
 * Output HDFS: /data/reports/failed_by_district_{year}_{quarter}/
 */
@Slf4j
@Service
public class FailedByDistrictService {

    // Dùng ObjectProvider như ReportServiceImpl để tránh crash khi HDFS không available
    private final ObjectProvider<FileSystem> fileSystemProvider;

    public FailedByDistrictService(ObjectProvider<FileSystem> fileSystemProvider) {
        this.fileSystemProvider = fileSystemProvider;
    }

    public record DistrictRiskDTO(
            String district,
            int    totalTx,
            int    failedTx,
            double failRate
    ) {}

    private Configuration buildConf() {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");
        conf.set("mapreduce.framework.name", "local");
        conf.set("dfs.client.use.datanode.hostname", "true");
        return conf;
    }

    public List<DistrictRiskDTO> runAndGetResult(String year, String quarter) throws Exception {
        String inputGlob  = "/data/transactions/province=*/district=*/year="
                            + year + "/quarter=" + quarter + "/";
        String outputPath = "/data/reports/failed_by_district_" + year + "_" + quarter;

        Configuration conf = buildConf();
        FileSystem    fs   = FileSystem.get(conf);
        Path          out  = new Path(outputPath);

        // Xóa output cũ nếu có
        if (fs.exists(out)) fs.delete(out, true);

        // Kiểm tra có dữ liệu input không — FIX: dùng globStatus đúng cách
        Path        inputPath   = new Path(inputGlob);
        FileStatus[] inputFiles = fs.globStatus(inputPath);
        if (inputFiles == null || inputFiles.length == 0) {
            log.warn("Không có dữ liệu HDFS tại: {}", inputGlob);
            return List.of();
        }

        // Khởi tạo và chạy MapReduce job
        Job job = Job.getInstance(conf, "FailedByDistrict_" + year + "_" + quarter);
        job.setJarByClass(FailedTransactionByDistrictJob.class);
        job.setMapperClass(FailedTransactionByDistrictJob.FailedMapper.class);
        job.setReducerClass(FailedTransactionByDistrictJob.FailedReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job, inputPath);
        FileOutputFormat.setOutputPath(job, out);

        log.info("Chạy FailedByDistrict job: year={} quarter={} inputFiles={}",
                year, quarter, inputFiles.length);

        if (!job.waitForCompletion(true))
            throw new RuntimeException("MapReduce job thất bại");

        return parseAndSort(fs, out);
    }

    // ── Parse output: "district|TOTAL \t count" và "district|FAILED \t count" ──
    private List<DistrictRiskDTO> parseAndSort(FileSystem fs, Path outDir) throws Exception {
        Map<String, Integer> totalMap  = new HashMap<>();
        Map<String, Integer> failedMap = new HashMap<>();

        for (FileStatus status : fs.listStatus(outDir)) {
            if (!status.getPath().getName().startsWith("part-")) continue;

            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    fs.open(status.getPath()), StandardCharsets.UTF_8))) {

                String line;
                while ((line = r.readLine()) != null) {
                    // Format: "Quận 3|TOTAL \t 120"
                    String[] parts = line.split("\t");
                    if (parts.length < 2) continue;

                    String[] kp = parts[0].split("\\|");
                    if (kp.length < 2) continue;

                    String district = kp[0].trim();
                    String type     = kp[1].trim();

                    int count;
                    try {
                        count = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        log.warn("Không parse được count từ dòng: {}", line);
                        continue;
                    }

                    if ("TOTAL".equals(type))  totalMap.merge(district,  count, Integer::sum);
                    if ("FAILED".equals(type)) failedMap.merge(district, count, Integer::sum);
                }
            }
        }

        // Tính failRate và tạo DTO
        List<DistrictRiskDTO> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : totalMap.entrySet()) {
            String d      = e.getKey();
            int    total  = e.getValue();
            int    failed = failedMap.getOrDefault(d, 0);
            // Round 2 chữ số thập phân
            double rate = total > 0
                    ? Math.round(failed * 10000.0 / total) / 100.0
                    : 0.0;
            result.add(new DistrictRiskDTO(d, total, failed, rate));
        }

        // Sort: failRate cao nhất lên đầu
        result.sort(Comparator.comparingDouble(DistrictRiskDTO::failRate).reversed());
        log.info("FailedByDistrict kết quả: {} district", result.size());
        return result;
    }
}