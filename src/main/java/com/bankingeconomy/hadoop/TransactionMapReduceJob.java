package com.bankingeconomy.hadoop;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;

/**
 * MapReduce Job: Tính tổng tiền giao dịch theo từng tài khoản.
 *
 * Input  (HDFS): /banking/transactions/transactions_2024_01.csv
 * Output (HDFS): /banking/reports/total_by_account_2024_01/
 *
 * Kết quả: mỗi dòng là  "ACC001  \t  700000.00"
 *
 * Chạy bằng lệnh:
 *   hadoop jar banking-app.jar com.bankingeconomy.hadoop.TransactionMapReduceJob 2024_01
 */
@Slf4j
public class TransactionMapReduceJob {

    // ----------------------------------------------------------------
    // MAPPER
    // Đọc từng dòng CSV → phát ra (account_number, amount)
    // ----------------------------------------------------------------

    public static class TransactionMapper
            extends Mapper<LongWritable, Text, Text, DoubleWritable> {

        private final Text account = new Text();
        private final DoubleWritable amount = new DoubleWritable();

        /**
         * Mỗi lần gọi = 1 dòng CSV trong block HDFS mà Mapper này được giao.
         *
         * Input  : "tx001,ACC001,ACC002,500000.00,SUCCESS,2024-01-01"
         * Output : (ACC001, 500000.0)
         */
        @Override
        protected void map(LongWritable offset, Text line, Context context)
                throws IOException, InterruptedException {

            String row = line.toString().trim();

            // Bỏ qua dòng header
            if (row.startsWith("id")) return;

            String[] cols = row.split(",");
            if (cols.length < 4) return;

            String fromAccount = cols[1].trim();
            double txAmount;
            try {
                txAmount = Double.parseDouble(cols[3].trim());
            } catch (NumberFormatException e) {
                return;
            }

            account.set(fromAccount);
            amount.set(txAmount);
            context.write(account, amount);
        }
    }

    // ----------------------------------------------------------------
    // REDUCER
    // Nhận (account_number, [amount1, amount2, ...]) → cộng lại
    // ----------------------------------------------------------------

    public static class TransactionReducer
            extends Reducer<Text, DoubleWritable, Text, DoubleWritable> {

        private final DoubleWritable total = new DoubleWritable();

        @Override
        protected void reduce(Text account, Iterable<DoubleWritable> amounts, Context context)
                throws IOException, InterruptedException {

            double sum = 0;
            for (DoubleWritable amt : amounts) {
                sum += amt.get();
            }

            total.set(sum);
            context.write(account, total);
        }
    }

    // ----------------------------------------------------------------
    // MAIN – Cấu hình và chạy Job
    // ----------------------------------------------------------------

    /**
     * Chạy job:
     *   hadoop jar banking-app.jar com.bankingeconomy.hadoop.TransactionMapReduceJob 2024_01
     *
     * YARN sẽ nhận job này, phân bổ Container cho các Map/Reduce task,
     * theo dõi tiến độ và báo kết quả về khi xong.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Thiếu tham số. Dùng: <month> (ví dụ: 2024_01)");
            System.exit(1);
        }

        String month      = args[0];
        String inputPath  = "/banking/transactions/transactions_" + month + ".csv";
        String outputPath = "/banking/reports/total_by_account_" + month;

        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");

        Job job = Job.getInstance(conf, "TotalByAccount_" + month);
        job.setJarByClass(TransactionMapReduceJob.class);

        // Chỉ định Mapper và Reducer
        job.setMapperClass(TransactionMapper.class);
        job.setReducerClass(TransactionReducer.class);

        // Kiểu output của Mapper
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(DoubleWritable.class);

        // Kiểu output của Reducer (kết quả cuối)
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(DoubleWritable.class);

        FileInputFormat.addInputPath(job,   new Path(inputPath));
        FileOutputFormat.setOutputPath(job, new Path(outputPath));

        log.info("Đang chạy MapReduce job cho tháng {}...", month);
        boolean success = job.waitForCompletion(true);

        if (success) {
            log.info("Job hoàn tất. Kết quả tại: {}", outputPath);
        }

        System.exit(success ? 0 : 1);
    }
}