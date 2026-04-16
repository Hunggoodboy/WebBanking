package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.event.TransferEvent;
import com.bankingeconomy.dto.request.TransferRequest;
import com.bankingeconomy.dto.response.TransferResponse;
import com.bankingeconomy.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public TransferResponse initiateTransfer(TransferRequest request) {
        // Tạo các biến thông tin giao dịch
        String fromAccount = "CURRENT_USER_ACC_NUMBER"; 
        String txId = UUID.randomUUID().toString();

        // Xây dựng đối tượng Event để gửi qua Kafka
        TransferEvent event = TransferEvent.builder()
                .transactionId(txId)
                .fromAccountNumber(fromAccount)
                .toAccountNumber(request.getToAccountNumber())
                .amount(request.getAmount())
                .description(request.getDescription())
                .status("PENDING")
                .build();

        // Gửi tin nhắn vào topic "transfer-topic"
        kafkaTemplate.send("transfer-topic", txId, event);

        // Trả về kết quả thông báo cho người dùng
        return TransferResponse.builder()
                .message("Yêu cầu chuyển khoản đang được xử lý")
                .transactionId(txId)
                .build();
    }
}