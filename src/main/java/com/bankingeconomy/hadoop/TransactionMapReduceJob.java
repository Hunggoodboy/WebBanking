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
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * MapReduce Job: Phân mảnh dữ liệu giao dịch và tính toán thống kê.
 *
 * Cấu trúc phân mảnh: /banking/fragments/country={nước}/province={tỉnh}/year={năm}/quarter={quý}/
 * Trong mỗi thư mục có:
 *  - transactions.csv: Danh sách các giao dịch thuộc phân mảnh này.
 *  - stats.csv: Thống kê tổng tiền và số lượng giao dịch.
 */
@Slf4j
public class TransactionMapReduceJob {

    public static class TransactionMapper
            extends Mapper<LongWritable, Text, Text, Text> {

        private final Text outputKey = new Text();

        @Override
        protected void map(LongWritable offset, Text line, Context context)
                throws IOException, InterruptedException {

            String row = line.toString().trim();
            if (row.startsWith("id")) return;

            String[] cols = row.split(",");
            if (cols.length < 8) return;

            // id(0), from(1), to(2), amount(3), status(4), created_at(5), country(6), province(7)
            String createdAtStr = cols[5].trim();
            String country = cols[6].trim();
            String province = cols[7].trim();

            if (country.isEmpty()) country = "Unknown";
            if (province.isEmpty()) province = "Unknown";

            // Parse ngày để lấy Năm và Quý
            try {
                LocalDateTime dateTime = LocalDateTime.parse(createdAtStr);
                int year = dateTime.getYear();
                int month = dateTime.getMonthValue();
                int quarter = (month - 1) / 3 + 1;

                // Key format: country|province|year|Qx
                String keyStr = String.format("%s|%s|%d|Q%d", country, province, year, quarter);
                outputKey.set(keyStr);
                context.write(outputKey, line);
            } catch (Exception e) {
                // Bỏ qua nếu không parse được ngày
            }
        }
    }

    public static class TransactionReducer
            extends Reducer<Text, Text, Text, Text> {

        private MultipleOutputs<Text, Text> mos;

        @Override
        protected void setup(Context context) {
            mos = new MultipleOutputs<>(context);
        }

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            String[] parts = key.toString().split("\\|");
            String country = parts[0];
            String province = parts[1];
            String year = parts[2];
            String quarter = parts[3];

            // Đường dẫn cơ sở cho phân mảnh này
            String baseDir = String.format("country=%s/province=%s/year=%s/quarter=%s/",
                    country, province, year, quarter);

            double totalAmount = 0;
            long count = 0;

            for (Text val : values) {
                String row = val.toString();
                String[] cols = row.split(",");
                if (cols.length >= 4) {
                    try {
                        totalAmount += Double.parseDouble(cols[3].trim());
                        count++;
                    } catch (NumberFormatException ignored) {}
                }

                // Ghi giao dịch vào file transactions
                mos.write("transactions", null, val, baseDir + "transactions");
            }

            // Ghi thống kê vào file stats
            String statsContent = String.format("Total Amount: %.2f, Transaction Count: %d", totalAmount, count);
            mos.write("stats", null, new Text(statsContent), baseDir + "stats");
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            mos.close();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Dùng: <month> (ví dụ: 2024_01)");
            System.exit(1);
        }

        String month = args[0];
        Configuration conf = new Configuration();

        Job job = Job.getInstance(conf, "TransactionFragmentation_" + month);
        job.setJarByClass(TransactionMapReduceJob.class);

        job.setMapperClass(TransactionMapper.class);
        job.setReducerClass(TransactionReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        // Đăng ký MultipleOutputs
        MultipleOutputs.addNamedOutput(job, "transactions", TextOutputFormat.class, Text.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "stats", TextOutputFormat.class, Text.class, Text.class);

        String inputPath = "/banking/transactions/transactions_" + month + ".csv";
        String outputPath = "/banking/fragments_" + month; // Output chính của YARN

        FileInputFormat.addInputPath(job, new Path(inputPath));
        FileOutputFormat.setOutputPath(job, new Path(outputPath));

        log.info("Đang chạy Job phân mảnh cho tháng {}...", month);
        boolean success = job.waitForCompletion(true);

        if (success) {
            log.info("Job hoàn tất. Kết quả phân mảnh tại: /banking/fragments_{}/", month);
        }

        System.exit(success ? 0 : 1);
    }
}