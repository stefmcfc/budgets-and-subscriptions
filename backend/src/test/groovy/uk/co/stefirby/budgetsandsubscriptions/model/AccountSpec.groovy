package uk.co.stefirby.budgetsandsubscriptions.model

import spock.lang.Specification

class AccountSpec extends Specification {

    def "BACKEND-002-AC-01: rejects a null or blank name"() {
        given: "a new Account with a null or blank name"

        when: "the account is persisted"

        then: "persistence fails validation"
    }

    def "BACKEND-002-AC-02: allows two accounts with the same name for the same owner"() {
        given: "a persisted account owned by a user with a given name"

        when: "a second account with the same name and owner is persisted"

        then: "persistence succeeds for both accounts"
    }

    def "BACKEND-002-AC-03: accepts each of the three v1 account types"() {
        given: "a new Account for each of CURRENT, SAVINGS, and CASH_ISA"

        when: "each account is persisted"

        then: "all three persist successfully with their given type"
    }

    def "BACKEND-002-AC-04: rejects persistence with no owner set"() {
        given: "a new Account with no owner set"

        when: "the account is persisted"

        then: "persistence fails"
    }

    def "BACKEND-002-AC-05: seeds balance equal to opening balance at construction"() {
        given: "a given openingBalance value"

        when: "a new Account is constructed with that openingBalance"

        then: "the account's balance equals the given openingBalance"
    }

    def "BACKEND-002-AC-06: application context loads with a schema-valid accounts table"() {
        given: "the Flyway migration for the accounts table has run"

        when: "the Spring application context loads"

        then: "startup succeeds with no Hibernate schema-validation error"
    }
}
