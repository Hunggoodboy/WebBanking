package com.bankingeconomy.controller;

import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.repository.TransactionRepository;
import com.bankingeconomy.service.HDFSReadWriteService;
import com.bankingeconomy.service.HdfsService;
import com.bankingeconomy.service.MapReduceRunnerService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
public class HadoopController {

    private final HdfsService hdfsService;
    private final HDFSReadWriteService  hdfsReadWriteService;
    private final TransactionRepository transactionRepository;
    private final MapReduceRunnerService mapReduceRunnerService;

    // API này nhận vào một cái tên và tạo thư mục trên HDFS
    @GetMapping("/api/hadoop/create-dir")
    public String createDirectory(@RequestParam String dirName) {
        try {
            // Đường dẫn gốc trên HDFS, nối thêm tên thư mục bạn truyền vào
            String path = "/user/hao/" + dirName;
            hdfsService.createDirectory(path);
            return "Đã tạo thành công thư mục: " + path + " trên HDFS!";
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }

    @GetMapping("/api/hadoop/export-to-hdfs")
    public String exportTransactionsToHDFS() {
        try {
            List<Transaction> transactions = transactionRepository.findAll();

            // Gán dữ liệu mẫu cho các giao dịch chưa có thông tin địa lý (để test phân mảnh)
            for (Transaction tx : transactions) {
                if (tx.getCountry() == null || tx.getCountry().isEmpty()) {
                    tx.setCountry("Vietnam");
                }
                if (tx.getProvince() == null || tx.getProvince().isEmpty()) {
                    // Phân bổ ngẫu nhiên một vài tỉnh để thấy được sự phân mảnh
                    String[] provinces = {"Hanoi", "HoChiMinh", "DaNang", "CanTho"};
                    tx.setProvince(provinces[Math.abs(tx.getId().hashCode()) % provinces.length]);
                }
            }
            transactionRepository.saveAll(transactions);

            String month = "2026_04";
            hdfsReadWriteService.writeTransactions(month, transactions);

            return "Đã cập nhật dữ liệu mẫu và xuất giao dịch lên HDFS cho tháng " + month;
        } catch (Exception e) {
            e.printStackTrace();
            return " Lỗi: " + e.getMessage();
        }
    }
    @GetMapping("/api/hadoop/run-mapreduce")
    public String runMapReduce() {
        // Kích hoạt hàm chạy MapReduce bạn đã viết sẵn
        return mapReduceRunnerService.runTransactionTotalJob("2026_04");
    }
}