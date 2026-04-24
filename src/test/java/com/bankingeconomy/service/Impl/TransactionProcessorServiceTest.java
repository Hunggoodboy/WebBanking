package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.event.TransferEvent;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.TransactionRepository;
import com.bankingeconomy.service.BalanceCacheService;
import com.bankingeconomy.service.kafka.producer.TransactionResultPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionProcessorServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceCacheService balanceCacheService;

    @Mock
    private TransactionResultPublisher transactionResultPublisher;

    @InjectMocks
    private TransactionProcessorService transactionProcessorService;

    @Test
    void processTransactionShouldNotRollbackWhenDebitFailsImmediately() {
        UUID txId = UUID.randomUUID();
        UUID fromAccountId = UUID.randomUUID();

        Account fromAccount = Account.builder()
                .id(fromAccountId)
                .accountNumber("123456789012")
                .balance(1_000)
                .status(Account.AccountStatus.ACTIVE)
                .build();

        Transaction transaction = Transaction.builder()
                .id(txId)
                .fromAccount(fromAccount)
                .amount(5_000)
                .status("PENDING")
                .build();

        TransferEvent event = TransferEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .transactionId(txId.toString())
                .build();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(transaction));
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));

        transactionProcessorService.processTransaction(event);

        verify(transactionRepository).updateStatus(txId, "PROCESSING");
        verify(transactionRepository).updateStatus(txId, "FAILED");
        verify(accountRepository, never()).save(fromAccount);
        verify(balanceCacheService, never()).evictBalance(fromAccountId);
    }
}
