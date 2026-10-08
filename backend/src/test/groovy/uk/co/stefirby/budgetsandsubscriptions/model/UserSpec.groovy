package uk.co.stefirby.budgetsandsubscriptions.model

import spock.lang.Specification

class UserSpec extends Specification {

    def "AUTH-001-AC-01: rejects a second user whose email differs only by case"() {
        given: "a persisted user with a given email"

        when: "a second user is persisted with the same email in different case"

        then: "persistence fails on the unique constraint"
    }

    def "AUTH-001-AC-02: defaults role to USER when not set"() {
        given: "a new User built without an explicit role"

        when: "the user is persisted"

        then: "the persisted user's role is USER"
    }

    def "AUTH-001-AC-03: rejects a blank display name"() {
        given: "a new User with a null or blank displayName"

        when: "the user is persisted"

        then: "persistence fails validation"
    }

    def "AUTH-001-AC-04: application context loads with a schema-valid users table"() {
        given: "the Flyway migration for the users table has run"

        when: "the Spring application context loads"

        then: "startup succeeds with no Hibernate schema-validation error"
    }
}
