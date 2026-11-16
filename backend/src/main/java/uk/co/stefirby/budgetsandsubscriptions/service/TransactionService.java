package uk.co.stefirby.budgetsandsubscriptions.service;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionCreateRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionResponse;
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionUpdateRequest;
import uk.co.stefirby.budgetsandsubscriptions.exception.TransactionDateException;
import uk.co.stefirby.budgetsandsubscriptions.exception.TransactionNotFoundException;
import uk.co.stefirby.budgetsandsubscriptions.model.Account;
import uk.co.stefirby.budgetsandsubscriptions.model.Transaction;
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository;
import uk.co.stefirby.budgetsandsubscriptions.repository.TransactionRepository;
import uk.co.stefirby.budgetsandsubscriptions.service.common.AccountAccessService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

        // make sure transaction date is not before account opening date
        checkTransactionDateNotBeforeAccountOpeningDate(transactionCreateRequest.date(), userAccount.getOpenedOn());

        Transaction transaction = createTransaction(accountId, transactionCreateRequest);
        Transaction createdTransaction = transactionRepository.saveAndFlush(transaction);

        userAccount.setBalance(userAccount.getBalance().add(createdTransaction.getAmount()));
        accountRepository.saveAndFlush(userAccount);

        return mapTransactionToTransactionResponse(createdTransaction);
    }

    @Transactional
    public List<TransactionResponse> getAllTransactions(String userId, UUID accountId) {
        // first make sure that the account belongs to the owner
        accountAccessService.verifyAccountOwnership(UUID.fromString(userId), accountId);

        List<Transaction> transactionList = transactionRepository.findAllByAccountId(accountId);
        return mapTransactionsToTransactionResponses(transactionList);
    }

    @Transactional
    public TransactionResponse getTransaction(String userId, UUID accountId, UUID transactionId) {
        // first make sure that the account belongs to the owner
        accountAccessService.verifyAccountOwnership(UUID.fromString(userId), accountId);

        Transaction transaction = getTransactionForVerifiedUserAndAccount(transactionId, accountId);

        return mapTransactionToTransactionResponse(transaction);
    }

    @Transactional
    public TransactionResponse updateTransaction(String userId,
                                                 UUID accountId,
                                                 UUID transactionId,
                                                 TransactionUpdateRequest transactionUpdateRequest) {

        // first make sure that the account belongs to the owner - also get it in case balance needs updating
        Account userAccount = accountAccessService.getAccountForUser(UUID.fromString(userId), accountId);

        // get the original transaction
        Transaction transaction = getTransactionForVerifiedUserAndAccount(transactionId, accountId);

        boolean amountProvided = transactionUpdateRequest.amount() != null;

        if (transactionUpdateRequest.date() != null) {
            // make sure transaction date is not before account opening date
            checkTransactionDateNotBeforeAccountOpeningDate(transactionUpdateRequest.date(), userAccount.getOpenedOn());
            transaction.setDate(transactionUpdateRequest.date());
        }
        if (amountProvided) {
            BigDecimal updateAmount = transactionUpdateRequest.amount();
            adjustAccountBalance(updateAmount, transaction.getAmount(), userAccount);
            transaction.setAmount(updateAmount);
        }
        if (transactionUpdateRequest.description() != null && !transactionUpdateRequest.description().isBlank()) {
            transaction.setDescription(transactionUpdateRequest.description());
        }
        if (transactionUpdateRequest.rawDescription() != null) {
            if (transactionUpdateRequest.rawDescription().isBlank()) {
                transaction.setRawDescription(null);
            } else {
                transaction.setRawDescription(transactionUpdateRequest.rawDescription());
            }
        }

        if (amountProvided) {
            accountRepository.saveAndFlush(userAccount);
        }

        return mapTransactionToTransactionResponse(transactionRepository.saveAndFlush(transaction));
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

    private static void adjustAccountBalance(BigDecimal updatedAmount, BigDecimal originalAmount, Account userAccount) {
        // update account balance
        BigDecimal newBalance = userAccount.getBalance()
            .subtract(originalAmount)
            .add(updatedAmount);

        userAccount.setBalance(newBalance);
    }

    private static void checkTransactionDateNotBeforeAccountOpeningDate(
        LocalDate transactionDate, LocalDate accountOpeningDate) {
        if (transactionDate.isBefore(accountOpeningDate)) {
            LOG.debug("Transaction date {} is before Account openedOn {}",
                transactionDate, accountOpeningDate);
            throw new TransactionDateException("Transaction date before Account openedOn");
        }
    }

    private Transaction getTransactionForVerifiedUserAndAccount(UUID transactionId, UUID accountId) {
        Optional<Transaction> checkTransaction = transactionRepository.findByIdAndAccountId(transactionId, accountId);
        if (checkTransaction.isEmpty()) {
            LOG.debug("No transaction found with transactionId {} and accountId {}", transactionId, accountId);
            throw new TransactionNotFoundException("Transaction not found");
        }
        return checkTransaction.get();
    }
}
