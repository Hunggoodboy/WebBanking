package com.bankingeconomy.controller;

import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.repository.TransactionRepository;
import com.bankingeconomy.service.HDFSReadWriteService;
import com.bankingeconomy.service.HdfsService;
import com.bankingeconomy.service.MapReduceRunnerService;
import com.bankingeconomy.service.Impl.FailedByDistrictService;
import com.bankingeconomy.service.Impl.FailedByDistrictService.DistrictRiskDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hadoop")
@AllArgsConstructor
@Slf4j
public class HadoopController {

    private final HdfsService             hdfsService;
    private final HDFSReadWriteService    hdfsReadWriteService;
    private final TransactionRepository   transactionRepository;
    private final MapReduceRunnerService  mapReduceRunnerService;
    private final FailedByDistrictService failedByDistrictService;

    // API này nhận vào một cái tên và tạo thư mục trên HDFS
    @GetMapping("/create-dir")
    public ResponseData<String> createDirectory(@RequestParam String dirName) {
        try {
            String path = "/user/hao/" + dirName;
            hdfsService.createDirectory(path);
            return new ResponseData<>(HttpStatus.OK.value(), "Thành công",
                    "Đã tạo thư mục: " + path);
        } catch (Exception e) {
            return new ResponseData<>(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Lỗi: " + e.getMessage());
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
            return new ResponseData<>(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Lỗi: " + e.getMessage());
        }
    }

    @GetMapping("/run-mapreduce")
    public ResponseData<String> runMapReduce() {
        try {
            String result = mapReduceRunnerService.runTransactionTotalJob("2026_04");
            return new ResponseData<>(HttpStatus.OK.value(), "Thành công", result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseData<>(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Lỗi: " + e.getMessage());
        }
    }

    /**
     * GET /api/hadoop/district-risk?year=2026&quarter=Q1
     *
     * Chạy MapReduce đọc từ HDFS phân mảnh theo province/district/year/quarter.
     * Tính tỉ lệ FAILED + REVERSED theo từng district.
     * Trả về list sort theo failRate giảm dần.
     */
    @GetMapping("/district-risk")
    public ResponseData<List<DistrictRiskDTO>> districtRisk(
            @RequestParam(defaultValue = "2026") String year,
            @RequestParam(defaultValue = "Q1")   String quarter) {
        try {
            List<DistrictRiskDTO> data = failedByDistrictService.runAndGetResult(year, quarter);
            return new ResponseData<>(HttpStatus.OK.value(),
                    "Thống kê rủi ro district " + year + "-" + quarter, data);
        } catch (Exception e) {
            log.error("district-risk error: {}", e.getMessage(), e);
            return new ResponseData<>(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Lỗi: " + e.getMessage());
        }
    }
}