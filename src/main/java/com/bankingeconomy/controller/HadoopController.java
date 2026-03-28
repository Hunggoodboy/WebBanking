package com.bankingeconomy.controller;

import com.bankingeconomy.service.HdfsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HadoopController {

    @Autowired
    private HdfsService hdfsService;

    // API này nhận vào một cái tên và tạo thư mục trên HDFS
    @GetMapping("/api/hadoop/create-dir")
    public String createDirectory(@RequestParam String dirName) {
        try {
            // Đường dẫn gốc trên HDFS, nối thêm tên thư mục bạn truyền vào
            String path = "/user/hao/" + dirName;
            hdfsService.createDirectory(path);
            return "✅ Tuyệt vời! Đã tạo thành công thư mục: " + path + " trên HDFS!";
        } catch (Exception e) {
            return "❌ Lỗi rồi: " + e.getMessage();
        }
    }
}