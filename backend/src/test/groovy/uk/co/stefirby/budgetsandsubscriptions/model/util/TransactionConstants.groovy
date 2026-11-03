package uk.co.stefirby.budgetsandsubscriptions.model.util

import java.time.LocalDate

class TransactionConstants {

    public static BigDecimal TRANSACTION_AMOUNT_POSITIVE = BigDecimal.valueOf(100)
    public static BigDecimal TRANSACTION_AMOUNT_NEGATIVE = BigDecimal.valueOf(-50)
    public static BigDecimal TRANSACTION_AMOUNT_ZERO = BigDecimal.ZERO

    public static LocalDate TRANSACTION_DATE_AFTER_OPENED_ON = LocalDate.of(2026, 10, 1)
    public static LocalDate TRANSACTION_DATE_SAME_AS_OPENED_ON = LocalDate.of(2026, 1, 1)
    public static LocalDate TRANSACTION_DATE_BEFORE_OPENED_ON = LocalDate.of(1990, 1, 2)

    public static String TRANSACTION_DESCRIPTION = "transaction description"
    public static String TRANSACTION_RAW_DESCRIPTION = "raw description"

}
