package uk.co.stefirby.budgetsandsubscriptions.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionCreateRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionResponse;
import uk.co.stefirby.budgetsandsubscriptions.service.TransactionService;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts/{accountId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> addTransaction(@PathVariable("accountId") UUID accountId,
                                                              @Valid @RequestBody TransactionCreateRequest requestBody,
                                                              @AuthenticationPrincipal String userId) {
        TransactionResponse response = transactionService.addTransaction(userId, accountId, requestBody);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(response);
    }

}
