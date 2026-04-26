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

/**
 * Job ghép giao dịch vào ra cùng 1 id, trả về (ProvinceIn-ProvinceOut) -> amount
 */
public class JoinTransactionFlowMapReduceJob {
    private static class JobMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text out = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(LongWritable offset, Text line, Context context)
                throws IOException, InterruptedException {
            String data = line.toString();
            if(data.isEmpty() || data.startsWith("transaction_id")) return;

            String[] columns = data.split(",");
            if (columns.length < 13) return;
            String transactionId = columns[0];
            String province = columns[8];
            String direction = columns[3];
            String amount = columns[5];

            out.set(transactionId);
            outValue.set(String.format("%s-%s-%s", province, direction, amount));

            context.write(out, outValue);
        }
    }

    private static class JobReducer extends Reducer<Text, Text, Text, DoubleWritable> {
        private final DoubleWritable total = new DoubleWritable();
        private final Text outKey = new Text();
        @Override
        protected void reduce(Text transactionId, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            String senderProvince = null;
            String receiverProvince = null;
            double amount = 0;

            for (Text val : values) {
                String[] parts = val.toString().split("-");

                String direction = parts[1];
                String province = parts[0];
                double amt = Double.parseDouble(parts[2]);

                if (direction.equals("OUT")) {
                    senderProvince = province;
                    amount = amt;
                } else if (direction.equals("IN")) {
                    receiverProvince = province;
                }
            }

            if (senderProvince != null && receiverProvince != null) {
                String flow = senderProvince + "-" + receiverProvince;

                outKey.set(flow);
                total.set(amount);

                context.write(outKey, total);
            }
        }
    }

    public static boolean run(String input, String output) throws IOException, InterruptedException, ClassNotFoundException {
        Configuration conf = new Configuration();

        conf.set("fs.defaultFS", "hdfs://localhost:9000");
        conf.set("mapreduce.framework.name", "local");
        conf.set("dfs.client.use.datanode.hostname", "true");
        conf.set("dfs.datanode.use.datanode.hostname", "true");

        Job job = Job.getInstance(conf, "JoinTransactionFlow");

        job.setJarByClass(JoinTransactionFlowMapReduceJob.class);

        job.setMapperClass(JobMapper.class);
        job.setReducerClass(JobReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(DoubleWritable.class);

        FileInputFormat.addInputPath(job, new Path(input));
        FileOutputFormat.setOutputPath(job, new Path(output));

        return job.waitForCompletion(true);
    }
}
