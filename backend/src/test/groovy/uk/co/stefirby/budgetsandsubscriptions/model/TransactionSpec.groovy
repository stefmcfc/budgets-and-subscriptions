package uk.co.stefirby.budgetsandsubscriptions.model

import spock.lang.Specification

class TransactionSpec extends Specification {

    def "BACKEND-003-AC-01: rejects a null amount"() {
        given: "a new Transaction with a null amount"

        when: "the transaction is persisted"

        then: "persistence fails validation"
    }

    def "BACKEND-003-AC-01: accepts both a positive and a negative amount"() {
        given: "a new Transaction with a positive amount, and a separate one with a negative amount"

        when: "each transaction is persisted"

        then: "both persist successfully with their given amount"
    }

    def "BACKEND-003-AC-02: rejects a null date"() {
        given: "a new Transaction with a null date"

        when: "the transaction is persisted"

        then: "persistence fails validation"
    }

    def "BACKEND-003-AC-03: rejects a null or blank description"() {
        given: "a new Transaction with a null or blank description"

        when: "the transaction is persisted"

        then: "persistence fails validation"
    }

    def "BACKEND-003-AC-04: rejects persistence with no owning account set"() {
        given: "a new Transaction with no Account set"

        when: "the transaction is persisted"

        then: "persistence fails"
    }

    def "BACKEND-003-AC-05: application context loads with a schema-valid transactions table"() {
        given: "the Flyway migration for the transactions table has run"

        when: "the Spring application context loads"

        then: "startup succeeds with no Hibernate schema-validation error"
    }
}
