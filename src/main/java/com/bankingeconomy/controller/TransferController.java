package com.bankingeconomy.controller;

import com.bankingeconomy.dto.request.TransferRequest;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.dto.response.TransferResponse;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService; // Gọi Service khởi tạo

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> initiate(@AuthenticationPrincipal User user,
                                                     @RequestBody TransferRequest request) {
        TransferResponse response = transferService.initiateTransfer(user, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}