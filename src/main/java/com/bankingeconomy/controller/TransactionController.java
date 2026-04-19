package com.bankingeconomy.controller;

import com.bankingeconomy.event.TransferEvent;
import com.bankingeconomy.service.kafka.consumer.TransactionProcessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionProcessorService transactionProcessorService;

    // Test API không cần Kafka
    @PostMapping("/process")
    public String processTransaction(@RequestBody TransferEvent event) {

        transactionProcessorService.processTransaction(event);

        return "Transaction processed successfully!";
    }
}
