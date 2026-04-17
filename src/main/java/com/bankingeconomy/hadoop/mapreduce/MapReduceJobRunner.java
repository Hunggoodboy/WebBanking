package com.bankingeconomy.hadoop.mapreduce;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;

public class MapReduceJobRunner {
    public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
        if (args.length < 3) {
            System.err.println("Tham số không hợp lệ, phải bao gồm: <input_partition> <output_file>");
            System.exit(1);
        }
        String inputPartition = args[1];
        String outputFile = args[2];

        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");

        Job job = Job.getInstance(conf, "Thực hiện tính tổng tiền giao dịch của người dùng theo tháng");
        job.setJarByClass(MapReduceJobRunner.class);

        job.setMapperClass(UserTransactionSumMapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(DoubleWritable.class);
        job.setReducerClass(UserTransactionSumReducer.class);

        // Đọc toàn bộ dữ liệu csv trong partition chỉ định
        FileInputFormat.addInputPath(job, new Path(inputPartition));
        FileInputFormat.setInputDirRecursive(job, true);

        FileOutputFormat.setOutputPath(job, new Path(outputFile));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
