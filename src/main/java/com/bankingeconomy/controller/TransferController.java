package com.bankingeconomy.controller;

import com.bankingeconomy.dto.request.TransferRequest;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.dto.response.TransferResponse;
import com.bankingeconomy.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/execute")
    public ResponseData<TransferResponse> transfer(@RequestBody TransferRequest request) {
        TransferResponse result = transferService.initiateTransfer(request);
        return new ResponseData<>(200, "Success", result);
    }
}