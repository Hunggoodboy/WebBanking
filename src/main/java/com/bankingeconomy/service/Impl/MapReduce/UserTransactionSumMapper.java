package com.bankingeconomy.service.Impl.MapReduce;

import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class UserTransactionSumMapper extends Mapper<LongWritable, Text, Text, DoubleWritable> {
    private final Text outKey = new Text();
    private final DoubleWritable outValue = new DoubleWritable();

    @Override
    public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        String record = value.toString();
        // 1. Bỏ qua header để không bị lỗi parse số
        if (record.startsWith("transaction_id")) return;

        String[] recordDatas = record.split(",");
        // CSV của bạn có 13 cột, cần kiểm tra độ dài
        if (recordDatas.length < 13) return;

        try {
            // Index 1: owner_user_id (UUID), Index 12: month
            String userId = recordDatas[1].trim();
            String month = recordDatas[12].trim();
            // Index 5: amount (Double)
            double amount = Double.parseDouble(recordDatas[5].trim());

            outKey.set(userId + "_" + month);
            outValue.set(amount);
            context.write(outKey, outValue);
        } catch (Exception e) {
            // Bỏ qua dòng lỗi
        }
    }
}
