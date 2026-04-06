//package com.bankingeconomy.kafka;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.kafka.support.SendResult;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.concurrent.CompletableFuture;
//
//@RestController
//@RequestMapping("/api/test-kafka")
//public class TestEventService {
//
//    private static final Logger log = LoggerFactory.getLogger(TestEventService.class);
//    private static final String TOPIC = "test-events";
//
//    private final KafkaTemplate<String, Object> kafkaTemplate;
//
//    // Constructor injection thay cho @RequiredArgsConstructor
//    public TestEventService(KafkaTemplate<String, Object> kafkaTemplate) {
//        this.kafkaTemplate = kafkaTemplate;
//    }
//
//    // ── PRODUCER ──────────────────────────────────────
//
//    @GetMapping("/send")
//    public String sendTestMessage(@RequestParam(defaultValue = "Hello Kafka!") String message) {
//        CompletableFuture<SendResult<String, Object>> future =
//                kafkaTemplate.send(TOPIC, message);
//
//        future.whenComplete((result, ex) -> {
//            if (ex == null) {
//                log.info("Gui OK → topic={} partition={} offset={}",
//                        result.getRecordMetadata().topic(),
//                        result.getRecordMetadata().partition(),
//                        result.getRecordMetadata().offset());
//            } else {
//                log.error("Gui that bai: {}", ex.getMessage());
//            }
//        });
//
//        return "Da gui message: " + message;
//    }
//
//    // ── CONSUMER ──────────────────────────────────────
//
//    @KafkaListener(topics = TOPIC, groupId = "test-group")
//    public void consumeTestEvent(String message) {
//        log.info("Nhan duoc tu [{}]: {}", TOPIC, message);
//    }
//}