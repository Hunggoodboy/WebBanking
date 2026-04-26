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
 * MapReduce Job: Thống kê mật độ giao dịch theo tỉnh/thành phố (Province).
 *
 * Gộp dữ liệu từ tất cả các mảnh province trên HDFS,
 * đếm tổng số giao dịch theo từng tỉnh và sắp xếp từ cao đến thấp.
 *
 * Input HDFS (phân mảnh từ HDFSReadWriteServiceImpl):
 *   /data/transactions/province={p}/district={d}/year={y}/quarter={q}/batch_*.csv
 *
 * CSV format (buildFactGeoLine):
 *   col[0]  transaction_id
 *   col[1]  owner_user_id
 *   col[2]  owner_account_id
 *   col[3]  direction          (IN / OUT)
 *   col[4]  counterparty_account_id
 *   col[5]  amount
 *   col[6]  status
 *   col[7]  created_at
 *   col[8]  province           ← dùng làm key
 *   col[9]  district
 *   col[10] year
 *   col[11] quarter
 *   col[12] month
 *
 * Output HDFS:
 *   /data/reports/province_density_{year}_{quarter}/part-r-00000
 *   Format: "province \t count"
 */
@Slf4j
public class TransactionDensityByProvinceJob {

    private static final int COL_PROVINCE = 8;

    // ── MAPPER ────────────────────────────────────────────────────────────
    // Mỗi dòng CSV → emit: (province, 1)
    public static class ProvinceDensityMapper
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
            if (cols.length <= COL_PROVINCE) return;

            String province = cols[COL_PROVINCE].trim();
            if (province.isEmpty()) province = "UNKNOWN";

            // Emit (province, 1)
            outKey.set(province);
            ctx.write(outKey, one);
        }
    }

    // ── REDUCER ───────────────────────────────────────────────────────────
    // Sum tất cả count cho mỗi province
    public static class ProvinceDensityReducer
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
    // Khi chạy từ Spring Boot thì dùng ProvinceDensityService
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: TransactionDensityByProvinceJob <year> <quarter>");
            System.err.println("Example: TransactionDensityByProvinceJob 2026 Q1");
            System.exit(1);
        }

        String year    = args[0];
        String quarter = args[1];

        String inputPath  = "/data/transactions/province=*/district=*/year=" + year + "/quarter=" + quarter + "/";
        String outputPath = "/data/reports/province_density_" + year + "_" + quarter;

        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");

        FileSystem fs = FileSystem.get(conf);
        Path outPath  = new Path(outputPath);
        if (fs.exists(outPath)) fs.delete(outPath, true);

        Job job = Job.getInstance(conf, "ProvinceDensity_" + year + "_" + quarter);
        job.setJarByClass(TransactionDensityByProvinceJob.class);
        job.setMapperClass(ProvinceDensityMapper.class);
        job.setReducerClass(ProvinceDensityReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        FileInputFormat.addInputPath(job, new Path(inputPath));
        FileOutputFormat.setOutputPath(job, outPath);

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
