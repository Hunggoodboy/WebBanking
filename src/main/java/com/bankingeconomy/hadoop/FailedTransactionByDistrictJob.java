package com.bankingeconomy.hadoop;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;

/**
 * MapReduce Job: Thống kê tỉ lệ FAILED / REVERSED theo district.
 *
 * Input HDFS (phân mảnh từ HDFSReadWriteServiceImpl):
 *   /data/transactions/province={p}/district={d}/year={y}/quarter={q}/part-00001.csv
 *
 * CSV format (buildFactGeoLine):
 *   col[0]  transaction_id
 *   col[1]  owner_user_id
 *   col[2]  owner_account_id
 *   col[3]  direction          (IN / OUT)
 *   col[4]  counterparty_account_id
 *   col[5]  amount
 *   col[6]  status             ← dùng để lọc FAILED/REVERSED
 *   col[7]  created_at
 *   col[8]  province
 *   col[9]  district           ← dùng làm key
 *   col[10] year
 *   col[11] quarter
 *   col[12] month
 *
 * Output HDFS:
 *   /data/reports/failed_by_district_{quarter}/part-r-00000
 *   Format: "district|TOTAL \t count" và "district|FAILED \t count"
 */
@Slf4j
public class FailedTransactionByDistrictJob {

    private static final int COL_STATUS   = 6;
    private static final int COL_DISTRICT = 9;

    // ── MAPPER ────────────────────────────────────────────────────────────
    // Mỗi dòng CSV → emit:
    //   (district|TOTAL, 1)          — luôn emit
    //   (district|FAILED, 1)         — chỉ khi FAILED hoặc REVERSED
    public static class FailedMapper
            extends Mapper<LongWritable, Text, Text, IntWritable> {

        private final Text        outKey = new Text();
        private final IntWritable one    = new IntWritable(1);

        @Override
        protected void map(LongWritable offset, Text line, Context ctx)
                throws IOException, InterruptedException {

            String row = line.toString().trim();
            // Bỏ qua dòng trống
            if (row.isEmpty()) return;

            String[] cols = row.split(",", -1);
            if (cols.length <= COL_DISTRICT) return;

            String status   = cols[COL_STATUS].trim().toUpperCase();
            String district = cols[COL_DISTRICT].trim();
            if (district.isEmpty()) district = "UNKNOWN";

            // Luôn đếm TOTAL
            outKey.set(district + "|TOTAL");
            ctx.write(outKey, one);

            // Chỉ đếm FAILED khi status FAILED hoặc REVERSED
            if ("FAILED".equals(status) || "REVERSED".equals(status)) {
                outKey.set(district + "|FAILED");
                ctx.write(outKey, one);
            }
        }
    }

    // ── REDUCER ───────────────────────────────────────────────────────────
    public static class FailedReducer
            extends Reducer<Text, IntWritable, Text, IntWritable> {

        private final IntWritable result = new IntWritable();

        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context ctx)
                throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable v : values) sum += v.get();
            result.set(sum);
            ctx.write(key, result);
        }
    }

    // ── MAIN ──────────────────────────────────────────────────────────────
    // Dùng khi chạy standalone trên Hadoop cluster
    // Khi chạy từ Spring Boot thì dùng FailedByDistrictService
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: FailedTransactionByDistrictJob <year> <quarter>");
            System.err.println("Example: FailedTransactionByDistrictJob 2026 Q1");
            System.exit(1);
        }

        String year    = args[0];
        String quarter = args[1];

        // Đọc toàn bộ province/district trong year và quarter này
        String inputPath  = "/data/transactions/province=*/district=*/year=" + year + "/quarter=" + quarter + "/";
        String outputPath = "/data/reports/failed_by_district_" + year + "_" + quarter;

        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");

        FileSystem fs = FileSystem.get(conf);
        Path outPath  = new Path(outputPath);
        if (fs.exists(outPath)) fs.delete(outPath, true);

        Job job = Job.getInstance(conf, "FailedByDistrict_" + year + "_" + quarter);
        job.setJarByClass(FailedTransactionByDistrictJob.class);
        job.setMapperClass(FailedMapper.class);
        job.setReducerClass(FailedReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        FileInputFormat.addInputPath(job, new Path(inputPath));
        FileOutputFormat.setOutputPath(job, outPath);

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}