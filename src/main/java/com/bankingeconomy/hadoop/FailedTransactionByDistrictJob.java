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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * MapReduce Job: Phát hiện rủi ro giao dịch theo district.
 *
 * CSV format (HDFSReadWriteServiceImpl.buildFactGeoLine):
 *   col[0]  transaction_id
 *   col[1]  owner_user_id
 *   col[2]  owner_account_id
 *   col[3]  direction           (IN / OUT)
 *   col[4]  counterparty_account_id
 *   col[5]  amount
 *   col[6]  status
 *   col[7]  created_at          (2026-04-01T10:30:00)
 *   col[8]  province
 *   col[9]  district
 *   col[10] year
 *   col[11] quarter
 *   col[12] month
 *
 * Output keys (district|TYPE → count):
 *   TOTAL        — tổng giao dịch trong district
 *   FAILED       — giao dịch FAILED hoặc REVERSED
 *   REVERSED     — chỉ REVERSED (High Reversal Area)
 *   LARGE_AMOUNT — cùng account OUT >= 500 triệu, >= 3 lần trong 1 giờ (Pattern Shift / High Frequency Large Value)
 *   RAPID_FIRE   — cùng account OUT >= 2 lần trong 1 phút (Velocity Check)
 *   FAN_OUT      — cùng account OUT tới >= 5 counterparty khác nhau/giờ
 */
@Slf4j
public class FailedTransactionByDistrictJob {

    // ── Ngưỡng phân loại ──────────────────────────────────────────────────
    /** Pattern Shift / High Frequency Large Value: >= 500 triệu VND */
    private static final double LARGE_AMOUNT_THRESHOLD = 500_000_000.0;

    // ── Chỉ số cột CSV ────────────────────────────────────────────────────
    private static final int COL_OWNER_ACCOUNT = 2;
    private static final int COL_DIRECTION     = 3;
    private static final int COL_COUNTERPARTY  = 4;
    private static final int COL_AMOUNT        = 5;
    private static final int COL_STATUS        = 6;
    private static final int COL_CREATED_AT    = 7;
    private static final int COL_DISTRICT      = 9;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ─────────────────────────────────────────────────────────────────────
    // MAPPER
    // ─────────────────────────────────────────────────────────────────────
    public static class FailedMapper
            extends Mapper<LongWritable, Text, Text, IntWritable> {

        private final Text        outKey = new Text();
        private final IntWritable one    = new IntWritable(1);

        @Override
        protected void map(LongWritable offset, Text line, Context ctx)
                throws IOException, InterruptedException {

            String row = line.toString().trim();
            if (row.isEmpty()) return;

            String[] cols = row.split(",", -1);
            if (cols.length <= COL_DISTRICT) return;

            String district     = cols[COL_DISTRICT].trim();
            String status       = cols[COL_STATUS].trim().toUpperCase();
            String direction    = cols[COL_DIRECTION].trim().toUpperCase();
            String accountId    = cols[COL_OWNER_ACCOUNT].trim();
            String counterparty = cols[COL_COUNTERPARTY].trim();
            String createdAtRaw = cols[COL_CREATED_AT].trim();

            if (district.isEmpty()) district = "UNKNOWN";

            double amount;
            try {
                amount = Double.parseDouble(cols[COL_AMOUNT].trim());
            } catch (NumberFormatException e) {
                return;
            }

            // ── 1. TOTAL — luôn đếm ───────────────────────────────────────
            emit(ctx, district + "|TOTAL", one);

            // ── 2. FAILED — giao dịch thất bại hoặc hoàn tiền ────────────
            if ("FAILED".equals(status) || "REVERSED".equals(status)) {
                emit(ctx, district + "|FAILED", one);
            }
            // ── 3. REVERSED — tách riêng để detect High Reversal Area ─────
            if ("REVERSED".equals(status)) {
                emit(ctx, district + "|REVERSED", one);
            }

            // Các loại dưới chỉ tính giao dịch OUT
            if (!"OUT".equals(direction)) return;

            // ── 4. LARGE AMOUNT nhiều lần — Pattern Shift (>= 500 triệu, nhiều lần/giờ)
            // Key trung gian: district|LARGE_AMOUNT|accountId|hour
            if (amount >= LARGE_AMOUNT_THRESHOLD) {
                try {
                    LocalDateTime dt   = LocalDateTime.parse(createdAtRaw, DT_FMT);
                    String        hour = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH"));
                    emit(ctx, district + "|LARGE_AMOUNT|" + accountId + "|" + hour, one);
                } catch (DateTimeParseException e) {
                    // bỏ qua nếu parse lỗi
                }
            }

            // ── 5. RAPID FIRE — Velocity Check (>= 2 lần/phút) ───────────
            // Key trung gian: district|RAPID_FIRE|accountId|yyyy-MM-ddTHH:mm
            try {
                LocalDateTime dt     = LocalDateTime.parse(createdAtRaw, DT_FMT);
                String        minute = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
                emit(ctx, district + "|RAPID_FIRE|" + accountId + "|" + minute, one);

                // ── 6. FAN-OUT — chuyển tới nhiều người lạ/giờ ───────────
                // Key trung gian: district|FAN_OUT|accountId|hour|counterparty
                String hour = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH"));
                emit(ctx, district + "|FAN_OUT|" + accountId + "|" + hour + "|" + counterparty, one);

            } catch (DateTimeParseException e) {
                // bỏ qua nếu parse lỗi
            }

        }

        private void emit(Context ctx, String key, IntWritable val)
                throws IOException, InterruptedException {
            outKey.set(key);
            ctx.write(outKey, val);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // REDUCER
    // ─────────────────────────────────────────────────────────────────────
    public static class FailedReducer
            extends Reducer<Text, IntWritable, Text, IntWritable> {

        private final IntWritable result = new IntWritable();

        private static final int RAPID_FIRE_THRESHOLD    = 2; // >= 2 lần/phút
        private static final int FAN_OUT_THRESHOLD       = 5; // >= 5 counterparty/giờ
        private static final int LARGE_AMOUNT_THRESHOLD  = 3; // >= 3 GD lớn/giờ từ cùng account

        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context ctx)
                throws IOException, InterruptedException {

            int sum = 0;
            for (IntWritable v : values) sum += v.get();

            String keyStr = key.toString();

            if (keyStr.contains("|LARGE_AMOUNT|")) {
                // Cùng account, cùng giờ >= 3 GD >= 500 triệu → cảnh báo Pattern Shift
                if (sum >= LARGE_AMOUNT_THRESHOLD) {
                    String district = keyStr.split("\\|LARGE_AMOUNT\\|")[0];
                    result.set(sum);
                    ctx.write(new Text(district + "|LARGE_AMOUNT"), result);
                }

            } else if (keyStr.contains("|RAPID_FIRE|")) {
                // Cùng account, cùng phút >= 2 lần → cảnh báo Velocity
                if (sum >= RAPID_FIRE_THRESHOLD) {
                    String district = keyStr.split("\\|RAPID_FIRE\\|")[0];
                    result.set(sum);
                    ctx.write(new Text(district + "|RAPID_FIRE"), result);
                }

            } else if (keyStr.contains("|FAN_OUT|")) {
                // Mỗi counterparty unique = 1 key → emit district|FAN_OUT với value=1
                // Service cộng tổng = số counterparty unique trong giờ đó
                String district = keyStr.split("\\|FAN_OUT\\|")[0];
                result.set(1);
                ctx.write(new Text(district + "|FAN_OUT"), result);

            } else {
                // TOTAL, FAILED, REVERSED → cộng bình thường
                result.set(sum);
                ctx.write(key, result);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: FailedTransactionByDistrictJob <year> <quarter>");
            System.exit(1);
        }

        String year      = args[0];
        String quarter   = args[1];
        String inputPath  = "/data/transactions/province=*/district=*/year="
                          + year + "/quarter=" + quarter + "/";
        String outputPath = "/data/reports/failed_by_district_" + year + "_" + quarter;

        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");

        FileSystem fs    = FileSystem.get(conf);
        Path       out   = new Path(outputPath);
        if (fs.exists(out)) fs.delete(out, true);

        Job job = Job.getInstance(conf, "FailedByDistrict_" + year + "_" + quarter);
        job.setJarByClass(FailedTransactionByDistrictJob.class);
        job.setMapperClass(FailedMapper.class);
        job.setReducerClass(FailedReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job, new Path(inputPath));
        FileOutputFormat.setOutputPath(job, out);

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}