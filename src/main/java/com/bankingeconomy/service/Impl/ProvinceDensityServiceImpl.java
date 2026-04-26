package com.bankingeconomy.service.Impl;

import com.bankingeconomy.hadoop.TransactionDensityByProvinceJob;
import com.bankingeconomy.service.ProvinceDensityService;
import com.bankingeconomy.dto.response.ProvinceDensityDTO;
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
public class ProvinceDensityServiceImpl implements ProvinceDensityService {

    private final ObjectProvider<FileSystem> fileSystemProvider;

    public ProvinceDensityServiceImpl(ObjectProvider<FileSystem> fileSystemProvider) {
        this.fileSystemProvider = fileSystemProvider;
    }

    @Override
    public List<ProvinceDensityDTO> runAndGetResult(String year, String quarter) throws Exception {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");
        conf.set("mapreduce.framework.name", "local");
        conf.set("dfs.client.use.datanode.hostname", "true");

        // Input glob path
        String inputGlob = "/data/transactions/province=*/district=*/year=" + year + "/quarter=" + quarter + "/";
        // Output path duy nhất mỗi lần chạy để tránh lỗi FileAlreadyExists
        String outputPath = "/data/reports/province_density_" + year + "_" + quarter + "_" + System.currentTimeMillis();

        FileSystem fs = fileSystemProvider.getIfAvailable();
        if (fs == null) return List.of();

        Path outPath = new Path(outputPath);
        Path inputPath = new Path(inputGlob);

        // Kiểm tra input có dữ liệu không
        FileStatus[] inputFiles = fs.globStatus(inputPath);
        if (inputFiles == null || inputFiles.length == 0) {
            log.warn("Không tìm thấy dữ liệu MapReduce tại: {}", inputGlob);
            return List.of();
        }

        Job job = Job.getInstance(conf, "ProvinceDensity_" + year + "_" + quarter);
        job.setJarByClass(TransactionDensityByProvinceJob.class);
        job.setMapperClass(TransactionDensityByProvinceJob.ProvinceDensityMapper.class);
        job.setReducerClass(TransactionDensityByProvinceJob.ProvinceDensityReducer.class);
        
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        FileInputFormat.addInputPath(job, inputPath);
        FileOutputFormat.setOutputPath(job, outPath);

        log.info("=== BẮT ĐẦU CHẠY MAPREDUCE JOB (DÀNH CHO GIÁO VIÊN) ===");
        if (!job.waitForCompletion(true)) {
            throw new RuntimeException("MapReduce Job failed!");
        }
        log.info("=== MAPREDUCE JOB HOÀN TẤT ===");

        return parseResult(fs, outPath);
    }

    private List<ProvinceDensityDTO> parseResult(FileSystem fs, Path outPath) throws Exception {
        Map<String, Integer> countMap = new HashMap<>();
        for (FileStatus status : fs.listStatus(outPath)) {
            if (status.getPath().getName().startsWith("part-")) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(fs.open(status.getPath()), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String[] parts = line.split("\t");
                        if (parts.length >= 2) {
                            countMap.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
                        }
                    }
                }
            }
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(countMap.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        List<ProvinceDensityDTO> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            result.add(new ProvinceDensityDTO(entry.getKey(), entry.getValue(), i + 1));
        }
        return result;
    }
}
