package com.bankingeconomy.controller;

import com.bankingeconomy.dto.response.ReportDTO;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.repository.TransactionRepository;
import com.bankingeconomy.service.Impl.WriteReadHDFS.HDFSReadWriteService;
import com.bankingeconomy.service.Impl.WriteReadHDFS.HdfsService;
import com.bankingeconomy.service.Impl.MapReduce.MapReduceRunnerService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.bankingeconomy.dto.response.ResponseData;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/hadoop")
@AllArgsConstructor
public class HadoopController {

    private final HDFSReadWriteService hdfsReadWriteService;
    private final TransactionRepository transactionRepository;
    private final MapReduceRunnerService mapReduceRunnerService;

    /**
     * URL: /api/hadoop/export-to-hdfs?month=2026_05
     */
    @GetMapping("/export-to-hdfs")
    public ResponseData<String> exportTransactionsToHDFS(@RequestParam(defaultValue = "2026_04") String month) {
        try {
            // Lấy tất cả giao dịch từ DB
            List<Transaction> transactions = transactionRepository.findAll();

            // Ghi lên HDFS (Lúc này service sẽ tự phân mảnh theo tỉnh/huyện của User)
            hdfsReadWriteService.writeTransactions(month, transactions);

            return new ResponseData<>(HttpStatus.OK.value(), "Thành công", "Đã xuất dữ liệu lên HDFS cho tháng " + month);
        } catch (Exception e) {
            return new ResponseData<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Lỗi export: " + e.getMessage());
        }
    }

    /**
     * URL ĐỘNG: /api/hadoop/run-mapreduce?province=Hanoi&year=2026&quarter=Q2
     * API này sẽ tính tổng tiền cho một tỉnh cụ thể trong một quý
     */
    @GetMapping("/run-mapreduce")
    public ResponseData<List<ReportDTO>> runMapReduce(
            @RequestParam String province,
            @RequestParam int year,
            @RequestParam String quarter) {
        try {
            // 1. Chạy Job MapReduce thông qua Service
            String status = mapReduceRunnerService.runUserMonthlyReport(province, year, quarter);

            if (status.contains("Thành công")) {
                String outputPath = "/banking/reports/user_sum_" + province + "_" + year + "_" + quarter;

                // 2. Lấy dữ liệu Map thô từ Service
                List<Map<String, String>> rawData = mapReduceRunnerService.readMapReduceResult(outputPath);

                // 3. Chuyển đổi List<Map<String, String>> sang List<ReportDTO>
                List<ReportDTO> reportData = rawData.stream()
                        .map(item -> new ReportDTO(
                                item.get("user_period"),
                                item.get("total_amount")
                        ))
                        .collect(Collectors.toList());

                return new ResponseData<>(HttpStatus.OK.value(), "Thành công", reportData);
            }

            return new ResponseData<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Job thất bại: " + status, null);
        } catch (Exception e) {
            return new ResponseData<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Lỗi: " + e.getMessage(), null);
        }
    }
}