package com.bankingeconomy.service.Impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.bankingeconomy.hadoop.QuarterlyGrowthMapReduceJob;
import com.bankingeconomy.service.QuarterlyGrowthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuarterlyGrowthServiceImpl implements QuarterlyGrowthService {

    private final ObjectProvider<FileSystem> fileSystemProvider;

    // ----------------------------------------------------------------
    // CHẠY MAPREDUCE JOB
    // ----------------------------------------------------------------
    @Override
    public String runQuarterlyGrowthJob(String year) {
        String inputGlob  = "/data/transactions/*/*/*/*/*.csv";
        String outputPath = "/banking/reports/quarterly_growth_" + year;

        try {
            // lấy FileSystem từ Spring bean thay vì tạo mới bằng
            //            FileSystem.get(conf) — tránh mở connection thừa, dùng
            //            cùng config đã được Spring quản lý
            FileSystem fs = fileSystemProvider.getIfAvailable();
            if (fs == null) {
                log.error("HDFS FileSystem bean không khả dụng");
                return "Lỗi: HDFS không khả dụng";
            }

            Configuration conf = new Configuration();
            conf.set("fs.defaultFS",                       "hdfs://localhost:9000");
            conf.set("mapreduce.framework.name",           "local");
            conf.set("dfs.client.use.datanode.hostname",   "true");
            conf.set("dfs.datanode.use.datanode.hostname", "true");

            Path outPath = new Path(outputPath);

            // Xóa output cũ nếu đã tồn tại (tránh lỗi job)
            if (fs.exists(outPath)) {
                fs.delete(outPath, true);
                log.info("Đã xóa output cũ tại: {}", outputPath);
            }

            Job job = Job.getInstance(conf, "QuarterlyGrowth_" + year);
            job.setJarByClass(QuarterlyGrowthMapReduceJob.class);
            job.setMapperClass(QuarterlyGrowthMapReduceJob.QuarterlyMapper.class);
            job.setReducerClass(QuarterlyGrowthMapReduceJob.QuarterlyReducer.class);

            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(Text.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);

            // FIX Bug 2: dùng biến inputGlob thay vì hardcode path
            FileInputFormat.addInputPath(job, new Path(inputGlob));
            FileOutputFormat.setOutputPath(job, outPath);   // Bug 1 đã được fix trước đó

            log.info("Bắt đầu chạy MapReduce QuarterlyGrowth cho năm: {}", year);
            boolean success = job.waitForCompletion(true);

            if (success) {
                return "Job chạy thành công! Kết quả lưu tại: " + outputPath;
            } else {
                return "Job thất bại. Vui lòng kiểm tra log Hadoop.";
            }

        } catch (Exception e) {
            log.error("Lỗi khi chạy MapReduce QuarterlyGrowth", e);
            return "Lỗi Server: " + e.getMessage();
        }
    }

    // ----------------------------------------------------------------
    // ĐỌC KẾT QUẢ TỪ HDFS
    // Output file mỗi dòng có dạng: "2024_Q1\t150000000.0,320,300"
    // ----------------------------------------------------------------
    @Override
    public Map<String, Object> getQuarterlyGrowthResult(String year) {
        FileSystem fileSystem = fileSystemProvider.getIfAvailable();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);

        if (fileSystem == null) {
            result.put("error", "HDFS không khả dụng");
            return result;
        }

        Path reportPath = new Path("/banking/reports/quarterly_growth_" + year + "/part-r-00000");

        // Map lưu kết quả mỗi quarter: Q1, Q2
        Map<String, Map<String, Object>> quarterData = new LinkedHashMap<>();

        try {
            if (!fileSystem.exists(reportPath)) {
                result.put("error", "Chưa có dữ liệu. Hãy chạy job trước.");
                return result;
            }

            try (FSDataInputStream inputStream = fileSystem.open(reportPath);
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;

                    // Mỗi dòng: "2024_Q1\t150000000.0,320,300"
                    String[] parts = trimmed.split("\\t");
                    if (parts.length < 2) continue;

                    String quarterKey = parts[0].trim(); // "2024_Q1"
                    String[] values   = parts[1].trim().split(",");
                    if (values.length < 3) continue;

                    // Chỉ lấy đúng năm được yêu cầu
                    if (!quarterKey.startsWith(year)) continue;

                    double totalAmount  = Double.parseDouble(values[0]);
                    long   txCount      = Long.parseLong(values[1]);
                    long   successCount = Long.parseLong(values[2]);
                    long   failCount    = txCount - successCount;
                    double successRate  = txCount > 0
                            ? Math.round((successCount * 100.0 / txCount) * 100.0) / 100.0
                            : 0;

                    Map<String, Object> qData = new LinkedHashMap<>();
                    qData.put("totalAmount",  totalAmount);
                    qData.put("txCount",      txCount);
                    qData.put("successCount", successCount);
                    qData.put("failCount",    failCount);
                    qData.put("successRate",  successRate);

                    // Key chỉ lấy phần "Q1" hoặc "Q2"
                    String label = quarterKey.contains("_")
                            ? quarterKey.split("_")[1]
                            : quarterKey;
                    quarterData.put(label, qData);
                }
            }

        } catch (Exception e) {
            log.error("Lỗi đọc kết quả MapReduce QuarterlyGrowth", e);
            result.put("error", "Lỗi đọc HDFS: " + e.getMessage());
            return result;
        }

        // Tính % tăng trưởng Q1 → Q2
        if (quarterData.containsKey("Q1") && quarterData.containsKey("Q2")) {
            double amountQ1 = (double) quarterData.get("Q1").get("totalAmount");
            double amountQ2 = (double) quarterData.get("Q2").get("totalAmount");
            long   countQ1  = (long)   quarterData.get("Q1").get("txCount");
            long   countQ2  = (long)   quarterData.get("Q2").get("txCount");

            double growthAmount = amountQ1 > 0
                    ? Math.round(((amountQ2 - amountQ1) / amountQ1 * 100) * 100.0) / 100.0
                    : 0;
            double growthCount = countQ1 > 0
                    ? Math.round(((countQ2 - countQ1) / (double) countQ1 * 100) * 100.0) / 100.0
                    : 0;

            result.put("growthAmountPercent", growthAmount);
            result.put("growthCountPercent",  growthCount);
        }

        result.put("quarters", quarterData);
        return result;
    }
}