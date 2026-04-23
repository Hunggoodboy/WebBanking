package com.bankingeconomy.hadoop;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Locale;

/**
 * Job MapReduce cho phần Nhật:
 * - Thống kê khung giờ có lượng chuyển tiền OUT thành công nhiều nhất trong tháng
 * - Thống kê ngày có lượng chuyển tiền OUT thành công nhiều nhất trong tháng
 * - Lưu chi tiết theo từng user để Spring Boot đọc lại và render ra dashboard admin
 */
@Slf4j
public class TopTransferTimeMapReduceJob {

    private static final int IDX_OWNER_USER_ID = 1;
    private static final int IDX_OWNER_ACCOUNT_ID = 2;
    private static final int IDX_DIRECTION = 3;
    private static final int IDX_AMOUNT = 5;
    private static final int IDX_STATUS = 6;
    private static final int IDX_CREATED_AT = 7;
    private static final int IDX_MONTH = 12;

    public static class TransferTimeMapper extends Mapper<LongWritable, Text, Text, Text> {

        private final Text outputKey = new Text();
        private final Text outputValue = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String row = value.toString().trim();
            if (row.isEmpty() || row.startsWith("transaction_id")) {
                return;
            }

            String[] columns = row.split(",", -1);
            if (columns.length <= IDX_MONTH) {
                return;
            }

            String targetMonth = context.getConfiguration().get("analysis.month", "").trim();
            String direction = columns[IDX_DIRECTION].trim().toUpperCase(Locale.ROOT);
            String status = columns[IDX_STATUS].trim().toUpperCase(Locale.ROOT);
            String rowMonth = columns[IDX_MONTH].trim();

            if (!"OUT".equals(direction) || !"SUCCESS".equals(status) || !targetMonth.equals(rowMonth)) {
                return;
            }

            String userId = columns[IDX_OWNER_USER_ID].trim();
            String accountNumber = columns[IDX_OWNER_ACCOUNT_ID].trim();
            double amount;
            try {
                amount = Double.parseDouble(columns[IDX_AMOUNT].trim());
            } catch (NumberFormatException ex) {
                return;
            }

            LocalDateTime createdAt;
            try {
                createdAt = LocalDateTime.parse(columns[IDX_CREATED_AT].trim());
            } catch (Exception ex) {
                return;
            }

            int hour = createdAt.getHour();
            int day = createdAt.getDayOfMonth();
            String aggregateValue = "1|" + amount;
            outputValue.set(aggregateValue);

            outputKey.set("HOUR_TOTAL|" + hour);
            context.write(outputKey, outputValue);

            outputKey.set("HOUR_USER|" + hour + "|" + userId + "|" + accountNumber);
            context.write(outputKey, outputValue);

            outputKey.set("DAY_TOTAL|" + day);
            context.write(outputKey, outputValue);

            outputKey.set("DAY_USER|" + day + "|" + userId + "|" + accountNumber);
            context.write(outputKey, outputValue);
        }
    }

    public static class TransferTimeReducer extends Reducer<Text, Text, Text, Text> {

        private final Text outputValue = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            long totalTransactions = 0;
            double totalAmount = 0;

            for (Text value : values) {
                String[] parts = value.toString().split("\\|");
                if (parts.length < 2) {
                    continue;
                }
                try {
                    totalTransactions += Long.parseLong(parts[0].trim());
                    totalAmount += Double.parseDouble(parts[1].trim());
                } catch (NumberFormatException ignored) {
                    // bỏ qua dòng lỗi định dạng
                }
            }

            outputValue.set(totalTransactions + "|" + totalAmount);
            context.write(key, outputValue);
        }
    }

    public static Job buildJob(Configuration conf, String month, Path inputPath, Path outputPath) throws IOException {
        conf.set("analysis.month", month);

        Job job = Job.getInstance(conf, "TopTransferTime_" + month);
        job.setJarByClass(TopTransferTimeMapReduceJob.class);

        job.setMapperClass(TransferTimeMapper.class);
        job.setReducerClass(TransferTimeReducer.class);
        job.setNumReduceTasks(1);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, inputPath);
        FileOutputFormat.setOutputPath(job, outputPath);
        return job;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Thiếu tham số month. Ví dụ: 2026-04");
            System.exit(1);
        }

        String month = args[0].trim();
        YearMonth yearMonth = YearMonth.parse(month);
        int quarter = ((yearMonth.getMonthValue() - 1) / 3) + 1;

        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");

        Path inputPath = new Path(String.format(
                "/data/transactions/province=*/district=*/year=%d/quarter=Q%d/*.csv",
                yearMonth.getYear(),
                quarter
        ));
        Path outputPath = new Path("/reports/top_sender_by_time/month=" + month);

        Job job = buildJob(conf, month, inputPath, outputPath);
        log.info("Bắt đầu chạy job thống kê giờ/ngày cao điểm cho tháng {}", month);
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
