package uk.co.stefirby.budgetsandsubscriptions.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountCreateRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountResponse;
import uk.co.stefirby.budgetsandsubscriptions.service.AccountService;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountCreateRequest requestBody,
                                                         @AuthenticationPrincipal String userId) {
        AccountResponse response = accountService.createAccount(userId, requestBody);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(response);
    }

}
