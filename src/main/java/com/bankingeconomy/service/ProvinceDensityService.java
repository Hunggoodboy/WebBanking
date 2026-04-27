package com.bankingeconomy.service;

import java.util.List;

import com.bankingeconomy.dto.response.ProvinceDensityDTO;

/**
 * Service xử lý thống kê mật độ giao dịch theo tỉnh.
 *
 * Gộp dữ liệu từ tất cả các mảnh province trên HDFS,
 * đếm tổng giao dịch theo tỉnh và sắp xếp từ cao đến thấp.
 */
public interface ProvinceDensityService {

    List<ProvinceDensityDTO> runAndGetResult(String year, String quarter) throws Exception;
}
