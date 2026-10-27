package uk.co.stefirby.budgetsandsubscriptions.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotBlank
    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @NotBlank
    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "opened_on", nullable = false)
    private LocalDate openedOn;

    @Column(name = "opening_balance", nullable = false)
    private BigDecimal openingBalance;

    @Column(nullable = false)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name="type", nullable = false)
    private AccountType accountType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // JPA
    }

    public Account(UUID userId, String bankName, String accountName,
                   LocalDate openedOn, BigDecimal openingBalance, AccountType accountType) {
        this.userId = userId;
        this.bankName = bankName;
        this.accountName = accountName;
        this.openedOn = openedOn;
        this.openingBalance = openingBalance;
        this.balance = openingBalance;
        this.accountType = accountType;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBankName() {
        return bankName;
    }

    public String getAccountName() {
        return accountName;
    }

    public LocalDate getOpenedOn() {
        return openedOn;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }
}
