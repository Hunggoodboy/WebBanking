package com.bankingeconomy.controller;

import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.repository.TransactionRepository;
import com.bankingeconomy.service.HDFSReadWriteService;
import com.bankingeconomy.service.HdfsService;
import com.bankingeconomy.service.MapReduceRunnerService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hadoop")
@AllArgsConstructor
public class HadoopController {

    private final HdfsService hdfsService;
    private final HDFSReadWriteService hdfsReadWriteService;
    private final MapReduceRunnerService mapReduceRunnerService;

    // API này nhận vào một cái tên và tạo thư mục trên HDFS
    @GetMapping("/create-dir")
    public ResponseData<String> createDirectory(@RequestParam String dirName) {
        try {
            // Đường dẫn gốc trên HDFS, nối thêm tên thư mục bạn truyền vào
            String path = "/user/hao/" + dirName;
            hdfsService.createDirectory(path);
            return new ResponseData<>(HttpStatus.OK.value(), "Thành công", "Đã tạo thành công thư mục: " + path + " trên HDFS!");
        } catch (Exception e) {
            return new ResponseData<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Lỗi: " + e.getMessage());
        }
    }

    @GetMapping("/export-to-hdfs")
    public ResponseData<?> exportTransactionsToHDFS() {
        try {
            hdfsReadWriteService.clearAllTransactionData();
            System.out.println("Đã xóa dữ liệu cũ trên HDFS trước khi ghi mới.");
            return hdfsReadWriteService.excuteWriteToHdfs();
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseData<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Lỗi: " + e.getMessage());
        }
    }

    @GetMapping("/run-mapreduce")
    public ResponseData<String> runMapReduce() {
        try {
            // Kích hoạt hàm chạy MapReduce bạn đã viết sẵn
            String result = mapReduceRunnerService.runTransactionTotalJob("2026_04");
            return new ResponseData<>(HttpStatus.OK.value(), "Thành công", result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseData<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Lỗi: " + e.getMessage());
        }
    }
}