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

@Slf4j
@Service
public class FailedByDistrictService {

    private final ObjectProvider<FileSystem> fileSystemProvider;

    public FailedByDistrictService(ObjectProvider<FileSystem> fileSystemProvider) {
        this.fileSystemProvider = fileSystemProvider;
    }

    /**
     * DTO đầy đủ các loại rủi ro theo district.
     *
     * failedTx      — FAILED + REVERSED (Nhóm 4: District Failure Rate)
     * reversedTx    — chỉ REVERSED (High Reversal Area)
     * largeAmountTx — giao dịch OUT >= 200 triệu (Pattern Shift / High Value)
     * rapidFireTx   — cùng account >= 2 lần/phút (Velocity Check)
     * fanOutTx      — số lượt fan-out counterparty unique/giờ (Fan-out pattern)
     * smallTestTx   — cùng account >= 3 giao dịch nhỏ <= 2.000đ (Small Amount Testing)
     * failRate      — failedTx / totalTx * 100
     * reversalRate  — reversedTx / totalTx * 100
     * riskLevel     — CAO / TRUNG_BINH / THAP (tổng hợp)
     * action        — hành động đề xuất cho admin
     */
    public record DistrictRiskDTO(
            String district,
            int    totalTx,
            int    failedTx,
            int    reversedTx,
            int    largeAmountTx,
            int    rapidFireTx,
            int    fanOutTx,
            double failRate,
            double reversalRate,
            String riskLevel,
            String action
    ) {}

    public List<DistrictRiskDTO> runAndGetResult(String year, String quarter) throws Exception {
        String inputGlob  = "/data/transactions/province=*/district=*/year="
                          + year + "/quarter=" + quarter + "/";
        String outputPath = "/data/reports/failed_by_district_" + year + "_" + quarter;

        // Dùng FileSystem bean đã inject — cùng connection với app
        FileSystem fs = fileSystemProvider.getIfAvailable();
        if (fs == null) throw new RuntimeException("HDFS FileSystem chưa sẵn sàng");

        Configuration conf = fs.getConf();
        // Bật local MapReduce
        conf.set("mapreduce.framework.name", "local");
        conf.set("dfs.client.use.datanode.hostname", "true");

        Path out = new Path(outputPath);
        if (fs.exists(out)) fs.delete(out, true);

        FileStatus[] inputFiles = fs.globStatus(new Path(inputGlob));
        if (inputFiles == null || inputFiles.length == 0) {
            log.warn("Không có dữ liệu HDFS tại: {}", inputGlob);
            return List.of();
        }

        Job job = Job.getInstance(conf, "FailedByDistrict_" + year + "_" + quarter);
        job.setJarByClass(FailedTransactionByDistrictJob.class);
        job.setMapperClass(FailedTransactionByDistrictJob.FailedMapper.class);
        job.setReducerClass(FailedTransactionByDistrictJob.FailedReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job, new Path(inputGlob));
        FileOutputFormat.setOutputPath(job, out);

        log.info("Chạy FailedByDistrict: year={} quarter={}", year, quarter);
        if (!job.waitForCompletion(true))
            throw new RuntimeException("MapReduce job thất bại");

        return parseAndSort(fs, out);
    }

    private List<DistrictRiskDTO> parseAndSort(FileSystem fs, Path outDir) throws Exception {
        Map<String, Integer> totalMap       = new HashMap<>();
        Map<String, Integer> failedMap      = new HashMap<>();
        Map<String, Integer> reversedMap    = new HashMap<>();
        Map<String, Integer> largeAmountMap = new HashMap<>();
        Map<String, Integer> rapidFireMap   = new HashMap<>();
        Map<String, Integer> fanOutMap      = new HashMap<>();

        for (FileStatus status : fs.listStatus(outDir)) {
            if (!status.getPath().getName().startsWith("part-")) continue;

            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    fs.open(status.getPath()), StandardCharsets.UTF_8))) {

                String line;
                while ((line = r.readLine()) != null) {
                    String[] parts = line.split("\t");
                    if (parts.length < 2) continue;

                    String[] kp = parts[0].split("\\|");
                    if (kp.length < 2) continue;

                    String district = kp[0].trim();
                    String type     = kp[1].trim();
                    int count;
                    try { count = Integer.parseInt(parts[1].trim()); }
                    catch (NumberFormatException e) { continue; }

                    if ("TOTAL".equals(type)) {
                        totalMap.merge(district, count, Integer::sum);
                    } else if ("FAILED".equals(type)) {
                        failedMap.merge(district, count, Integer::sum);
                    } else if ("REVERSED".equals(type)) {
                        reversedMap.merge(district, count, Integer::sum);
                    } else if ("LARGE_AMOUNT".equals(type)) {
                        largeAmountMap.merge(district, count, Integer::sum);
                    } else if ("RAPID_FIRE".equals(type)) {
                        rapidFireMap.merge(district, count, Integer::sum);
                    } else if ("FAN_OUT".equals(type)) {
                        fanOutMap.merge(district, count, Integer::sum);
                    }
                }
            }
        }

        List<DistrictRiskDTO> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : totalMap.entrySet()) {
            String d           = e.getKey();
            int    total       = e.getValue();
            int    failed      = failedMap.getOrDefault(d, 0);
            int    reversed    = reversedMap.getOrDefault(d, 0);
            int    largeAmount = largeAmountMap.getOrDefault(d, 0);
            int    rapidFire   = rapidFireMap.getOrDefault(d, 0);
            int    fanOut      = fanOutMap.getOrDefault(d, 0);

            double failRate    = total > 0 ? Math.round(failed   * 10000.0 / total) / 100.0 : 0.0;
            double reversalRate= total > 0 ? Math.round(reversed * 10000.0 / total) / 100.0 : 0.0;

            String riskLevel;
            if (failRate >= 20 || reversalRate >= 15 || rapidFire > 0 || fanOut >= 5) {
                riskLevel = "CAO";
            } else if (failRate >= 10 || reversalRate >= 5 || largeAmount >= 5 || fanOut >= 2) {
                riskLevel = "TRUNG_BINH";
            } else {
                riskLevel = "THAP";
            }

            String action;
            if (rapidFire > 0) {
                action = "Tam khoa GD 15 phut + Gui OTP";
            } else if (reversalRate >= 15 || fanOut >= 5) {
                action = "Bat buoc xac thuc khuon mat";
            } else if (failRate >= 20) {
                action = "Hien thi canh bao 'Mang yeu khu vuc'";
            } else if (largeAmount >= 5) {
                action = "Yeu cau xac thuc bo sung";
            } else {
                action = "Theo doi";
            }

            result.add(new DistrictRiskDTO(
                    d, total, failed, reversed,
                    largeAmount, rapidFire, fanOut,
                    failRate, reversalRate, riskLevel, action));
        }

        // Sort: CAO → TRUNG_BINH → THAP, cùng level thì theo failRate
        result.sort(Comparator
                .<DistrictRiskDTO, Integer>comparing(dto -> riskScore(dto.riskLevel()))
                .reversed()
                .thenComparing(Comparator.comparingDouble(DistrictRiskDTO::failRate).reversed()));

        log.info("FailedByDistrict kết quả: {} district", result.size());
        return result;
    }

    private int riskScore(String level) {
        if ("CAO".equals(level))        return 3;
        if ("TRUNG_BINH".equals(level)) return 2;
        return 1;
    }
}