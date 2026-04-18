package com.bankingeconomy.service.consume;

import com.bankingeconomy.event.TransferEvent;
import com.bankingeconomy.event.TransferEvent.TransferStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessorService {

    public void processTransaction(TransferEvent event) {

        log.info("Start processing eventId={} status={}", event.getEventId(), event.getStatus());

        try {
            event.setStatus(TransferStatus.PROCESSING);

            handleDebit(event);
            handleCredit(event);

            event.setStatus(TransferStatus.COMPLETED);
            log.info("Transaction completed: eventId={}", event.getEventId());

        } catch (Exception e) {
            event.setStatus(TransferStatus.FAILED);
            log.error("Transaction failed: eventId={}, error={}", event.getEventId(), e.getMessage(), e);

            handleRollback(event);
        }
    }

    private void handleDebit(TransferEvent event) {
        log.info("Debiting {} from account {}", event.getAmount(), event.getFromAccountId());
    }

    private void handleCredit(TransferEvent event) {
        log.info("Crediting {} to account {}", event.getAmount(), event.getToAccountId());
    }

    private void handleRollback(TransferEvent event) {
        log.warn("Rolling back transaction: eventId={}", event.getEventId());
    }
}
