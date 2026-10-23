package uk.co.stefirby.budgetsandsubscriptions.controller

import spock.lang.Specification

class AccountControllerSpec extends Specification {

    //---------------------------------
    // POST /api/accounts tests
    //=================================

    def "BACKEND-002-AC-07: creates and returns a new account for the authenticated user"() {
        given: "an authenticated user and a valid account creation request"

        when: "POST /api/accounts is requested"

        then: "the response is 201 Created with the created account"

        and: "the created account's balance equals the given openingBalance"

        and: "the created account is owned by the authenticated user"
    }

    def "BACKEND-002-AC-08: ignores a client-supplied owner id"() {
        given: "an authenticated user and a creation request body containing a different user's id as owner"

        when: "POST /api/accounts is requested"

        then: "the created account's owner is the authenticated user, not the id from the request body"
    }

    def "BACKEND-002-AC-09: rejects account creation with a blank or missing name"() {
        given: "a creation request with a blank or missing name"

        when: "POST /api/accounts is requested"

        then: "the response is 400 Bad Request"

        and: "no account is created"
    }

    def "BACKEND-002-AC-10: rejects account creation with a missing or invalid type"() {
        given: "a creation request with either a missing type or a type value outside the allowed enum"

        when: "POST /api/accounts is requested"

        then: "the response is 400 Bad Request"

        and: "no account is created"
    }

    def "BACKEND-002-AC-11: rejects account creation with a missing opening balance"() {
        given: "a creation request with a missing openingBalance"

        when: "POST /api/accounts is requested"

        then: "the response is 400 Bad Request"

        and: "no account is created"
    }

    def "BACKEND-002-AC-12: rejects account creation with a missing or future-dated openedOn"() {
        given: "a creation request with either a missing openedOn or an openedOn after today"

        when: "POST /api/accounts is requested"

        then: "the response is 400 Bad Request"

        and: "no account is created"
    }

    //---------------------------------
    // GET /api/accounts tests
    //=================================

    def "BACKEND-002-AC-13: lists only the authenticated user's own accounts"() {
        given: "two users, each owning at least one account"

        when: "GET /api/accounts is requested by the first user"

        then: "the response is 200 OK containing only the first user's accounts"
    }

    def "BACKEND-002-AC-14: returns an empty list for a user with no accounts"() {
        given: "an authenticated user who owns no accounts"

        when: "GET /api/accounts is requested"

        then: "the response is 200 OK with an empty list"
    }

    //---------------------------------
    // GET /api/accounts/{id} tests
    //=================================

    def "BACKEND-002-AC-15: returns a single owned account"() {
        given: "an account owned by the authenticated user"

        when: "GET /api/accounts/{id} is requested with that account's id"

        then: "the response is 200 OK with that account"
    }

    def "BACKEND-002-AC-16: returns 404 uniformly for a non-existent or non-owned account"() {
        given: "either a non-existent account id, or an account id owned by a different user"

        when: "GET /api/accounts/{id} is requested with that id"

        then: "the response is 404 Not Found in both cases"
    }

    //---------------------------------
    // PATCH /api/accounts/{id} tests
    //=================================

    def "BACKEND-002-AC-17: updates an owned account's name and/or type"() {
        given: "an account owned by the authenticated user and a valid update request"

        when: "PATCH /api/accounts/{id} is requested with that account's id"

        then: "the response is 200 OK with the updated account"
    }

    def "BACKEND-002-AC-18: returns 404 uniformly for a non-existent or non-owned account on update"() {
        given: "either a non-existent account id, or an account id owned by a different user"

        when: "PATCH /api/accounts/{id} is requested with that id"

        then: "the response is 404 Not Found in both cases"

        and: "no account is modified"
    }

    def "BACKEND-002-AC-19: rejects an update with a blank name"() {
        given: "an account owned by the authenticated user and an update request with a blank name"

        when: "PATCH /api/accounts/{id} is requested"

        then: "the response is 400 Bad Request"

        and: "the account is not modified"
    }

    def "BACKEND-002-AC-20: leaves openingBalance, openedOn, and balance unchanged on update"() {
        given: "an account owned by the authenticated user and an update request body that also includes openingBalance, openedOn, and balance values"

        when: "PATCH /api/accounts/{id} is requested"

        then: "the response is 200 OK"

        and: "the account's openingBalance, openedOn, and balance are unchanged from before the request"

        and: "only name and/or type reflect the request"
    }

    //---------------------------------
    // DELETE /api/accounts/{id} tests
    //=================================

    def "BACKEND-002-AC-21: deletes an owned account"() {
        given: "an account owned by the authenticated user"

        when: "DELETE /api/accounts/{id} is requested with that account's id"

        then: "the response is 204 No Content"

        and: "the account no longer exists"
    }

    def "BACKEND-002-AC-22: returns 404 uniformly for a non-existent or non-owned account on delete"() {
        given: "either a non-existent account id, or an account id owned by a different user"

        when: "DELETE /api/accounts/{id} is requested with that id"

        then: "the response is 404 Not Found in both cases"

        and: "no account is deleted"
    }

    //---------------------------------
    // Authentication regression guard
    //=================================

    def "BACKEND-002-AC-23: rejects an unauthenticated request before the controller runs"() {
        given: "a request to an /api/accounts endpoint with no valid access-token cookie"

        when: "the request is made"

        then: "the response is 401 Unauthorized"

        and: "AccountController is never invoked"
    }
}
