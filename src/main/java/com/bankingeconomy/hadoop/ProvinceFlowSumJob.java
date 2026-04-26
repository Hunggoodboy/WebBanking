package com.bankingeconomy.hadoop;

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

public class ProvinceFlowSumJob {
    private static class JobMapper extends Mapper<LongWritable, Text, Text, DoubleWritable> {
        private final Text out = new Text();
        private final DoubleWritable outValue = new DoubleWritable();

        @Override
        protected void map(LongWritable offset, Text line, Context context)
                throws IOException, InterruptedException {
            String[] data = line.toString().split("\\t");
            String flow = data[0];
            double amount = Double.parseDouble(data[1]);

            out.set(flow);
            outValue.set(amount);
            context.write(out, outValue);
        }
    }

    private static class JobReducer extends Reducer<Text, DoubleWritable, Text, DoubleWritable> {
        private final DoubleWritable total = new DoubleWritable();

        @Override
        protected void reduce(Text flow, Iterable<DoubleWritable> values, Context context)
                throws IOException, InterruptedException {
            double amount = 0;
            for(var value : values) {
                amount += value.get();
            }

            total.set(amount);
            context.write(flow, total);
        }
    }

    public static boolean run(String input, String output) throws IOException, InterruptedException, ClassNotFoundException {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");
        conf.set("mapreduce.framework.name", "local");
        conf.set("dfs.client.use.datanode.hostname", "true");
        conf.set("dfs.datanode.use.datanode.hostname", "true");
        Job job = Job.getInstance(conf, "Sum Flow");

        job.setJarByClass(ProvinceFlowSumJob.class);

        job.setMapperClass(JobMapper.class);
        job.setReducerClass(JobReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(DoubleWritable.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(DoubleWritable.class);

        FileInputFormat.addInputPath(job, new Path(input));
        FileOutputFormat.setOutputPath(job, new Path(output));

        return job.waitForCompletion(true);
    }
}
