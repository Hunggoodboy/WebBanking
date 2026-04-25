package com.bankingeconomy.hadoop;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QuarterlyGrowthMapReduceJob {

    // ----------------------------------------------------------
    // MAPPER
    // Input: mỗi dòng CSV từ HDFS
    // Output: key = "2024_Q1", value = "500000.0,SUCCESS"
    // ----------------------------------------------------------
    public static class QuarterlyMapper
            extends Mapper<LongWritable, Text, Text, Text> {

        private final Text quarterKey = new Text();
        private final Text valueOut   = new Text();

        @Override
        protected void map(LongWritable offset, Text line, Context context)
                throws IOException, InterruptedException {

            String row = line.toString().trim();
            if (row.startsWith("transaction_id") || row.isEmpty()) return;

            // CSV columns:
            // 0:transaction_id, 1:owner_user_id, 2:owner_account_id, 3:direction,
            // 4:counterparty_account_id, 5:amount, 6:status, 7:created_at,
            // 8:province, 9:district, 10:year, 11:quarter, 12:month
            String[] cols = row.split(",");
            if (cols.length < 13) return;

            String amount  = cols[5].trim();
            String status  = cols[6].trim();
            String year    = cols[10].trim();
            String quarter = cols[11].trim(); // "1", "2", "3", "4"

            // Chỉ lấy Q1 và Q2
            if (!quarter.equals("1") && !quarter.equals("2")) return;

            try {
                Double.parseDouble(amount); // validate
            } catch (NumberFormatException e) {
                return;
            }

            quarterKey.set(year + "_Q" + quarter);       // "2024_Q1"
            valueOut.set(amount + "," + status);          // "500000.0,SUCCESS"
            context.write(quarterKey, valueOut);
        }
    }

    // ----------------------------------------------------------
    // REDUCER
    // Input:  key = "2024_Q1", values = ["500000.0,SUCCESS", ...]
    // Output: key = "2024_Q1", value = "totalAmount,txCount,successCount"
    // ----------------------------------------------------------
    public static class QuarterlyReducer
            extends Reducer<Text, Text, Text, Text> {

        private final Text result = new Text();

        @Override
        protected void reduce(Text quarter, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            double totalAmount   = 0;
            long   txCount       = 0;
            long   successCount  = 0;

            for (Text val : values) {
                String[] parts = val.toString().split(",");
                if (parts.length < 2) continue;

                try {
                    totalAmount += Double.parseDouble(parts[0]);
                } catch (NumberFormatException ignored) {}

                txCount++;
                if ("SUCCESS".equalsIgnoreCase(parts[1])) {
                    successCount++;
                }
            }

            // Output: "totalAmount,txCount,successCount"
            result.set(totalAmount + "," + txCount + "," + successCount);
            context.write(quarter, result);
        }
    }

    // ----------------------------------------------------------
    // MAIN
    // Input HDFS:  /data/transactions/
    // Output HDFS: /banking/reports/quarterly_growth_{year}/
    // ----------------------------------------------------------
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Dùng: <year> (ví dụ: 2024)");
            System.exit(1);
        }

        String year       = args[0];
        String inputPath = "/data/transactions/*/*/*/*/*.csv";
        String outputPath = "/banking/reports/quarterly_growth_" + year;

        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");

        Job job = Job.getInstance(conf, "QuarterlyGrowth_" + year);
        job.setJarByClass(QuarterlyGrowthMapReduceJob.class);

        job.setMapperClass(QuarterlyMapper.class);
        job.setReducerClass(QuarterlyReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, new Path(inputPath));
        FileOutputFormat.setOutputPath(job, new Path(outputPath));

        log.info("Đang chạy MapReduce QuarterlyGrowth cho năm {}...", year);
        boolean success = job.waitForCompletion(true);
        System.exit(success ? 0 : 1);
    }
}