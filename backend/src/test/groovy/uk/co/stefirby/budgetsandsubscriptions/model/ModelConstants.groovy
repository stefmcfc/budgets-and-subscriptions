package uk.co.stefirby.budgetsandsubscriptions.model

import java.time.LocalDate

class ModelConstants {

    public static final String USER_EMAIL_VALID = "test@test.com"
    public static final String USER_EMAIL_UPPERCASE = "TEST@TEST.COM"
    public static final String USER_PASSWORD = "dummyPassword"
    public static final String USER_DISPLAY_NAME = "Test"

    public static String ACCOUNT_BANK_NAME = "Bank Of Ste"
    public static String ACCOUNT_BANK_NAME_1 = "Ste's Saving Bank"
    public static String ACCOUNT_ACCOUNT_NAME = "Super Savings Account"
    public static String ACCOUNT_ACCOUNT_NAME_1 = "Huge Savings"
    public static LocalDate ACCOUNT_OPENED_ON = LocalDate.of(2026, 1, 1)
    public static BigDecimal ACCOUNT_OPENING_BALANCE = 1234.56

}
