package com.bankingeconomy.service;

import com.bankingeconomy.hadoop.TransactionMapReduceJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MapReduceRunnerService {

    public String runTransactionTotalJob(String month) {
        String inputPath = "/banking/transactions/transactions_" + month + ".csv";
        String outputPath = "/banking/reports/total_by_account_" + month;

        try {
            Configuration conf = new Configuration();
            conf.set("fs.defaultFS", "hdfs://localhost:9000");

            conf.set("mapreduce.framework.name", "local");

            conf.set("dfs.client.use.datanode.hostname", "true");
            conf.set("dfs.datanode.use.datanode.hostname", "true");

            FileSystem fs = FileSystem.get(conf);
            Path outPath = new Path(outputPath);
            if (fs.exists(outPath)) {
                fs.delete(outPath, true);
            }

            // Khởi tạo Job
            Job job = Job.getInstance(conf, "TransactionFragmentation_" + month);

            job.setJarByClass(TransactionMapReduceJob.class);
            job.setMapperClass(TransactionMapReduceJob.TransactionMapper.class);
            job.setReducerClass(TransactionMapReduceJob.TransactionReducer.class);

            // Cấu hình kiểu dữ liệu Output (Dùng Text cho cả Key và Value)
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(Text.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);

            // Đăng ký MultipleOutputs (Rất quan trọng cho logic phân mảnh)
            org.apache.hadoop.mapreduce.lib.output.MultipleOutputs.addNamedOutput(job, "transactions", org.apache.hadoop.mapreduce.lib.output.TextOutputFormat.class, Text.class, Text.class);
            org.apache.hadoop.mapreduce.lib.output.MultipleOutputs.addNamedOutput(job, "stats", org.apache.hadoop.mapreduce.lib.output.TextOutputFormat.class, Text.class, Text.class);

            // Set đường dẫn Input/Output
            FileInputFormat.addInputPath(job, new Path(inputPath));
            FileOutputFormat.setOutputPath(job, outPath);

            log.info("Bắt đầu chạy MapReduce Job phân mảnh cho tháng: {}", month);

            // waitForCompletion(true) sẽ block luồng cho đến khi Job chạy xong
            boolean success = job.waitForCompletion(true);

            if (success) {
                return "MapReduce Job phân mảnh thành công! Dữ liệu tại: /banking/fragments_" + month;
            } else {
                return "MapReduce Job thất bại. Vui lòng check Hadoop logs.";
            }

        } catch (Exception e) {
            log.error("Lỗi khi chạy MapReduce", e);
            return "Lỗi Server: " + e.getMessage();
        }
    }
}