package com.bankingeconomy.service.Impl.MapReduce;

import com.bankingeconomy.config.HadoopConfig;
import com.bankingeconomy.hadoop.TransactionMapReduceJob;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapReduceRunnerService {

    private final Configuration hadoopConfig;
    private final FileSystem fileSystem;

    public String runUserMonthlyReport(String province, int year, String quarter) {
        String inputPath = "/data/transactions/province=" + province + "/*/year=" + year + "/quarter=" + quarter + "/*.csv";
        String outputPath = "/banking/reports/user_sum_" + province + "_" + year + "_" + quarter;

        try {
            // Xóa output cũ bằng fileSystem đã được inject
            Path outPath = new Path(outputPath);
            if (fileSystem.exists(outPath)) {
                fileSystem.delete(outPath, true);
            }

            // Dùng hadoopConfig đã inject sẵn
            Job job = Job.getInstance(hadoopConfig, "UserSum_" + province);
            job.setJarByClass(MapReduceRunnerService.class);

            job.setMapperClass(UserTransactionSumMapper.class);
            job.setReducerClass(UserTransactionSumReducer.class);

            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(DoubleWritable.class);

            FileInputFormat.addInputPath(job, new Path(inputPath));
            FileOutputFormat.setOutputPath(job, outPath);

            return job.waitForCompletion(true) ? "Thành công: " + outputPath : "Thất bại";
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }
    public List<Map<String, String>> readMapReduceResult(String folderPath) throws IOException {
        Path resultFilePath = new Path(folderPath + "/part-r-00000");
        List<Map<String, String>> results = new ArrayList<>();

        if (!fileSystem.exists(resultFilePath)) return results;

        try (FSDataInputStream in = fileSystem.open(resultFilePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t"); // Tách bởi dấu Tab của Hadoop
                if (parts.length == 2) {
                    Map<String, String> item = new HashMap<>();
                    item.put("user_period", parts[0]);
                    // Chuyển 6.598E7 thành số bình thường
                    String prettyAmount = new java.math.BigDecimal(parts[1]).toPlainString();
                    item.put("total_amount", prettyAmount);
                    results.add(item);
                }
            }
        }
        return results;
    }
}