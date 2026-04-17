package com.bankingeconomy.service.kafka.consumer;

import com.bankingeconomy.event.TransferEvent;
import com.bankingeconomy.event.TransferEvent.TransferStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TransactionProcessorService {

    public void processTransaction(TransferEvent event) {

        log.info("Start processing eventId={} status={}", event.getEventId(), event.getStatus());

        try {
            // 1. Update status → PROCESSING
            event.setStatus(TransferStatus.PROCESSING);

            // 2. Debit from sender
            handleDebit(event);

            // 3. Credit to receiver
            handleCredit(event);

            // 4. Mark success
            event.setStatus(TransferStatus.COMPLETED);
            log.info("Transaction completed: eventId={}", event.getEventId());

        } catch (Exception e) {
            // 5. Handle failure
            event.setStatus(TransferStatus.FAILED);
            log.error("Transaction failed: eventId={}, error={}", event.getEventId(), e.getMessage(), e);

            // OPTIONAL: rollback / compensate
            handleRollback(event);
        }
    }

    private void handleDebit(TransferEvent event) {
        log.info("Debiting {} from account {}", event.getAmount(), event.getFromAccountId());

        // TODO:
        // 1. Check balance (Redis / BalanceCacheService)
        // 2. Call AccountService.debit()
        // 3. Throw exception if insufficient balance
    }

    private void handleCredit(TransferEvent event) {
        log.info("Crediting {} to account {}", event.getAmount(), event.getToAccountId());

        // TODO:
        // Call AccountService.credit()
    }

    private void handleRollback(TransferEvent event) {
        log.warn("Rolling back transaction: eventId={}", event.getEventId());

        // TODO (important in real system):
        // - If debit succeeded but credit failed → refund
        // - Use saga / compensation pattern
    }
}
