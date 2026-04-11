package com.bankingeconomy.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bankingeconomy.service.HdfsService;

@RestController
@ConditionalOnProperty(name = "hadoop.enabled", havingValue = "true") 
public class HadoopController {

    @Autowired
    private HdfsService hdfsService;

    @GetMapping("/api/hadoop/create-dir")
    public String createDirectory(@RequestParam String dirName) {
        try {
            String path = "/user/hao/" + dirName;
            hdfsService.createDirectory(path);
            return "✅ Đã tạo thành công thư mục: " + path;
        } catch (Exception e) {
            return "❌ Lỗi: " + e.getMessage();
        }
    }
}