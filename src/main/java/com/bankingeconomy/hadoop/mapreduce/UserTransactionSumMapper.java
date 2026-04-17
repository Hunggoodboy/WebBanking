package com.bankingeconomy.hadoop.mapreduce;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class UserTransactionSumMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
    private final Text outKey = new Text();
    private final IntWritable outValue = new IntWritable();

    @Override
    public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        // Tách dữ liệu đầu vào và kiểm tra có đủ thông tin không
        String record = value.toString();
        String[] recordDatas = record.split(",");
        if (recordDatas.length < 5) return;

        String userId = recordDatas[0].trim();
        String date = recordDatas[3].trim();
        String yearMonth = date.substring(0, 7);
        int amount = Integer.parseInt(recordDatas[4].trim());

        // Tạo key theo (userid_tháng)
        outKey.set(userId+"_"+yearMonth);
        outValue.set(amount);
        context.write(outKey, outValue);
    }
}
