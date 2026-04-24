package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.response.ProvinceFlowAmountDTO;
import com.bankingeconomy.hadoop.JoinTransactionFlowMapReduceJob;
import com.bankingeconomy.hadoop.ProvinceFlowSumJob;
import lombok.AllArgsConstructor;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class TransactionProvinceFlowService {
    private ObjectProvider<FileSystem> fileSystemProvider;

    public List<ProvinceFlowAmountDTO> getTopProvinceFlowAmount(int size) throws IOException, InterruptedException, ClassNotFoundException {
        String joinInput = "/data/transactions/province=*/district=*/year=*/quarter=*/";
        Path inputPath = new Path(joinInput);
        String joinOutput = "/data/report/temp/join_transaction/";
        Path joinOutputPath = new Path(joinOutput);
        String jobOutput = "/data/report/province_flow_total_amount/";
        Path jobOuputPath = new Path(jobOutput);

        FileSystem fs = fileSystemProvider.getIfAvailable();
        if (fs == null) return List.of();
        if (fs.exists(joinOutputPath)) {
            fs.delete(joinOutputPath, true);
        }
        if (fs.exists(jobOuputPath)) {
            fs.delete(jobOuputPath, true);
        }

        FileStatus[] files = fs.globStatus(inputPath);
        if (files == null || files.length == 0) {
            return List.of();
        }

        if (JoinTransactionFlowMapReduceJob.run(joinInput, joinOutput) && ProvinceFlowSumJob.run(jobOutput, jobOutput)) {
            return parseOutput(fs,  jobOuputPath);
        } else return List.of();

    }

    private List<ProvinceFlowAmountDTO> parseOutput(FileSystem fs, Path outputPath) throws IOException {
        List<ProvinceFlowAmountDTO> out = new ArrayList<>();

        for (FileStatus status : fs.listStatus(outputPath)) {
            if (!status.getPath().getName().startsWith("part-")) continue;

            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    fs.open(status.getPath()), StandardCharsets.UTF_8))) {

                String line;
                while ((line = r.readLine()) != null) {
                    String[] parts = line.split("\\t");
                    if (parts.length != 2) {
                        // Invalid data
                        continue;
                    }
                    String[] provinces = parts[0].split("-");
                    double amount = 0;
                    try {
                        amount = Double.parseDouble(parts[1]);
                    } catch (Exception e) {
                        // Invalid data;
                        continue;
                    }
                    out.add(new ProvinceFlowAmountDTO(
                            provinces[0],
                            provinces[1],
                            amount
                    ));
                }
            }
        }

        return out;
    }
}
