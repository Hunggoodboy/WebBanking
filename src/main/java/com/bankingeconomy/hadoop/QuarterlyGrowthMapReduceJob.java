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
    // ----------------------------------------------------------
    public static class QuarterlyMapper extends Mapper<LongWritable, Text, Text, Text> {

        private final Text quarterKey = new Text();
        private final Text valueOut   = new Text();
        private String targetYear;

        @Override
        protected void setup(Context context) {
            targetYear = context.getConfiguration().get("targetYear", "").trim();
        }

        @Override
        protected void map(LongWritable offset, Text line, Context context) throws IOException, InterruptedException {
            String row = line.toString().trim();

            if (row.isEmpty() || row.startsWith("transaction_id")) return;

            String[] cols = row.split(",");

            if (cols.length < 13) return;

            String amountStr = cols[5].trim();
            String status    = cols[6].trim();
            String rowYear   = cols[10].trim();
            String quarter   = cols[11].trim().toUpperCase();

            if (!rowYear.equals(targetYear)) return;

            if (quarter.equals("1")) quarter = "Q1";
            if (quarter.equals("2")) quarter = "Q2";

            if (!quarter.equals("Q1") && !quarter.equals("Q2")) return;

            try {
                Double.parseDouble(amountStr); // Validate số tiền
                quarterKey.set(targetYear + "_" + quarter);
                valueOut.set(amountStr + "," + status);
                context.write(quarterKey, valueOut);
            } catch (NumberFormatException ignored) {
                // Bỏ qua nếu giá trị tiền không hợp lệ
            }
        }
    }

    // ----------------------------------------------------------
    // REDUCER
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

            result.set(totalAmount + "," + txCount + "," + successCount);
            context.write(quarter, result);
        }
    }

    // ----------------------------------------------------------
    // MAIN (Giữ nguyên như cũ)
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