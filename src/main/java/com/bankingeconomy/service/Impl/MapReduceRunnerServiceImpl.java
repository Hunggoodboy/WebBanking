package com.bankingeconomy.service.Impl;

import com.bankingeconomy.hadoop.TopTransferTimeMapReduceJob;
import com.bankingeconomy.hadoop.TransactionMapReduceJob;
import com.bankingeconomy.service.MapReduceRunnerService;
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

import java.time.YearMonth;

@Slf4j
@Service
public class MapReduceRunnerServiceImpl implements MapReduceRunnerService {

    @Override
    public String runTransactionTotalJob(String month) {
        String inputPath = "/banking/transactions/transactions_" + month + ".csv";
        String outputPath = "/banking/reports/total_by_account_" + month;

        try {
            Configuration conf = buildBaseConfiguration();
            FileSystem fs = FileSystem.get(conf);
            Path outPath = new Path(outputPath);
            if (fs.exists(outPath)) {
                fs.delete(outPath, true);
            }

            Job job = Job.getInstance(conf, "TotalByAccount_" + month);
            job.setJarByClass(TransactionMapReduceJob.class);
            job.setMapperClass(TransactionMapReduceJob.TransactionMapper.class);
            job.setReducerClass(TransactionMapReduceJob.TransactionReducer.class);
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(DoubleWritable.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(DoubleWritable.class);

            FileInputFormat.addInputPath(job, new Path(inputPath));
            FileOutputFormat.setOutputPath(job, outPath);

            log.info("Bắt đầu chạy MapReduce Job cho tháng: {}", month);
            boolean success = job.waitForCompletion(true);
            return success
                    ? "MapReduce Job chạy thành công! Kết quả lưu tại: " + outputPath
                    : "MapReduce Job thất bại. Vui lòng check log.";
        } catch (Exception e) {
            log.error("Lỗi khi chạy MapReduce", e);
            return "Lỗi Server: " + e.getMessage();
        }
    }

    @Override
    public String runTopTransferTimeJob(String month) {
        try {
            YearMonth yearMonth = YearMonth.parse(month);
            int quarter = ((yearMonth.getMonthValue() - 1) / 3) + 1;

            Configuration conf = buildBaseConfiguration();
            String outputPath = "/reports/top_sender_by_time/month=" + month;
            Path inputPath = new Path(String.format(
                    "/data/transactions/province=*/district=*/year=%d/quarter=Q%d/*.csv",
                    yearMonth.getYear(),
                    quarter
            ));
            Path outPath = new Path(outputPath);

            FileSystem fs = FileSystem.get(conf);
            if (fs.exists(outPath)) {
                fs.delete(outPath, true);
            }

            Job job = TopTransferTimeMapReduceJob.buildJob(conf, month, inputPath, outPath);
            log.info("Bắt đầu chạy job thống kê người dùng chuyển tiền theo giờ/ngày cho tháng {}", month);
            boolean success = job.waitForCompletion(true);
            return success
                    ? outputPath
                    : "JOB_FAILED";
        } catch (Exception e) {
            log.error("Lỗi khi chạy job thống kê giờ/ngày cao điểm", e);
            return "ERROR: " + e.getMessage();
        }
    }

    private Configuration buildBaseConfiguration() {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");
        conf.set("mapreduce.framework.name", "local");
        conf.set("dfs.client.use.datanode.hostname", "true");
        conf.set("dfs.datanode.use.datanode.hostname", "true");
        return conf;
    }
}
