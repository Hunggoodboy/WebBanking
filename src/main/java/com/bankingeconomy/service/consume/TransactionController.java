package com.bankingeconomy.service.consume;

import com.bankingeconomy.event.TransferEvent;
import com.bankingeconomy.service.consume.TransactionProcessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
