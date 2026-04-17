package com.bankingeconomy.kafka;

import com.bankingeconomy.event.TransferEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/test-kafka")
public class TestEventService {

    private static final Logger log = LoggerFactory.getLogger(TestEventService.class);
    private static final String TOPIC = "test-events";
    private static final String TRANSFER_TOPIC = "transfer-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public TestEventService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // ── PRODUCER (test cũ) ────────────────────────────

    @GetMapping("/send")
    public String sendTestMessage(@RequestParam(defaultValue = "Hello Kafka!") String message) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC, message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Gui OK → topic={} partition={} offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Gui that bai: {}", ex.getMessage());
            }
        });

        return "Da gui message: " + message;
    }

    // ── TEST NOTIFICATION: COMPLETED ─────────────────

    @GetMapping("/send-transfer")
    public String sendTransferCompleted() throws Exception {

        TransferEvent event = TransferEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .fromAccountId("acc-001")
                .toAccountId("acc-002")
                .senderUserId("1")
                .receiverUserId("2")
                .amount(new BigDecimal("500000"))
                .currency("VND")
                .description("Test chuyển tiền thành công")
                .status(TransferEvent.TransferStatus.COMPLETED)
                .timestamp(Instant.now())
                .build();

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(TRANSFER_TOPIC, json);

        log.info("Đã gửi COMPLETED TransferEvent lên Kafka");
        return "Đã gửi COMPLETED event: " + json;
    }

    // ── TEST NOTIFICATION: FAILED ─────────────────────

    @GetMapping("/send-transfer-failed")
    public String sendTransferFailed() throws Exception {

        TransferEvent event = TransferEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .fromAccountId("acc-001")
                .toAccountId("acc-002")
                .senderUserId("1")
                .receiverUserId("2")
                .amount(new BigDecimal("1000000"))
                .currency("VND")
                .description("Test chuyển tiền thất bại")
                .status(TransferEvent.TransferStatus.FAILED)
                .timestamp(Instant.now())
                .build();

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(TRANSFER_TOPIC, json);

        log.info("Đã gửi FAILED TransferEvent lên Kafka");
        return "Đã gửi FAILED event: " + json;
    }

    // ── TEST NOTIFICATION: REVERSED ──────────────────

    @GetMapping("/send-transfer-reversed")
    public String sendTransferReversed() throws Exception {

        TransferEvent event = TransferEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .fromAccountId("acc-001")
                .toAccountId("acc-002")
                .senderUserId("1")
                .receiverUserId("2")
                .amount(new BigDecimal("250000"))
                .currency("VND")
                .description("Test hoàn tiền")
                .status(TransferEvent.TransferStatus.REVERSED)
                .timestamp(Instant.now())
                .build();

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(TRANSFER_TOPIC, json);

        log.info("Đã gửi REVERSED TransferEvent lên Kafka");
        return "Đã gửi REVERSED event: " + json;
    }

    // ── CONSUMER  ────────────────────────────

    @KafkaListener(topics = TOPIC, groupId = "test-group")
    public void consumeTestEvent(String message) {
        log.info("Nhan duoc tu [{}]: {}", TOPIC, message);
    }
}