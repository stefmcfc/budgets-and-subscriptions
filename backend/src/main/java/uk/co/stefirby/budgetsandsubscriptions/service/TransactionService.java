package uk.co.stefirby.budgetsandsubscriptions.service;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionCreateRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionResponse;
import uk.co.stefirby.budgetsandsubscriptions.exception.TransactionDateException;
import uk.co.stefirby.budgetsandsubscriptions.model.Account;
import uk.co.stefirby.budgetsandsubscriptions.model.Transaction;
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository;
import uk.co.stefirby.budgetsandsubscriptions.repository.TransactionRepository;
import uk.co.stefirby.budgetsandsubscriptions.service.common.AccountAccessService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionService.class);

    private final AccountAccessService accountAccessService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(AccountAccessService accountAccessService,
                              AccountRepository accountRepository,
                              TransactionRepository transactionRepository) {
        this.accountAccessService = accountAccessService;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResponse addTransaction(String userId,
                                              UUID accountId,
                                              TransactionCreateRequest transactionCreateRequest) {
        // first make sure that the account belongs to the owner
        Account userAccount = accountAccessService.getAccountForUser(UUID.fromString(userId), accountId);

        if (transactionCreateRequest.date().isBefore(userAccount.getOpenedOn())) {
            LOG.debug("Transaction date {} is before Account openedOn {}",
                transactionCreateRequest.date(), userAccount.getOpenedOn());
            throw new TransactionDateException("Transaction date before Account openedOn");
        }

        Transaction transaction = createTransaction(accountId, transactionCreateRequest);
        Transaction createdTransaction = transactionRepository.saveAndFlush(transaction);

        userAccount.setBalance(userAccount.getBalance().add(createdTransaction.getAmount()));
        accountRepository.saveAndFlush(userAccount);

        return mapTransactionToTransactionResponse(createdTransaction);
    }

    public List<TransactionResponse> getAllTransactions(String userId, UUID accountId) {
        // first make sure that the account belongs to the owner
        accountAccessService.verifyAccountOwnership(UUID.fromString(userId), accountId);

        List<Transaction> transactionList = transactionRepository.findAllByAccountId(accountId);
        return mapTransactionsToTransactionResponses(transactionList);
    }

    //------------------
    // private methods
    //==================

    private static @NonNull Transaction createTransaction(UUID accountId, TransactionCreateRequest transactionCreateRequest) {
        return new Transaction(
            accountId,
            transactionCreateRequest.amount(),
            transactionCreateRequest.date(),
            transactionCreateRequest.description(),
            transactionCreateRequest.rawDescription()
        );
    }

    private List<TransactionResponse> mapTransactionsToTransactionResponses(List<Transaction> transactionList) {
        return transactionList.stream()
            .map(this::mapTransactionToTransactionResponse)
            .toList();
    }

    private TransactionResponse mapTransactionToTransactionResponse(Transaction createdTransaction) {
        return new TransactionResponse(
            createdTransaction.getId(),
            createdTransaction.getAmount(),
            createdTransaction.getDate(),
            createdTransaction.getDescription(),
            createdTransaction.getRawDescription()
        );
    }

    private TransactionResponse dummyTransactionResponse() {
        return new TransactionResponse(
            UUID.randomUUID(), BigDecimal.TEN, LocalDate.of(2026, 2, 2),
            "description", "raw"
        );
    }
}
