package com.bankingeconomy.controller;

import com.bankingeconomy.dto.request.TransferRequest;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.dto.response.TransferResponse;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/execute")
    public ResponseData<TransferResponse> transfer(@AuthenticationPrincipal User currentUser, @RequestBody TransferRequest request) {
        System.out.println(request.toString());
        TransferResponse result = transferService.initiateTransfer(currentUser, request);
        return new ResponseData<>(200, "Success", result);
    }
}

