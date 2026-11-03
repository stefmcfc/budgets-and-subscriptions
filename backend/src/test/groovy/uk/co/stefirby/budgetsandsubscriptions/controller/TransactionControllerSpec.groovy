package uk.co.stefirby.budgetsandsubscriptions.controller

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import spock.lang.Unroll
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionResponse
import uk.co.stefirby.budgetsandsubscriptions.exception.AccountNotFoundException
import uk.co.stefirby.budgetsandsubscriptions.exception.TransactionDateException
import uk.co.stefirby.budgetsandsubscriptions.security.SecurityConfig
import uk.co.stefirby.budgetsandsubscriptions.service.TransactionService

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static uk.co.stefirby.budgetsandsubscriptions.model.util.TransactionConstants.*
import static uk.co.stefirby.budgetsandsubscriptions.model.util.TransactionEntityHelper.*
import static uk.co.stefirby.budgetsandsubscriptions.security.util.CookieHelper.accessTokenCookie

@WebMvcTest(controllers = TransactionController)
@Import(SecurityConfig)
class TransactionControllerSpec extends BaseControllerSpec {

    private static final String ENDPOINT_TRANSACTIONS = "/api/accounts/{accountId}/transactions"

    @SpringBean
    TransactionService mockTransactionService = Mock()

    String constructTransactionUri(String userId) {
        return ENDPOINT_TRANSACTIONS.replace("{accountId}", userId)
    }

    //---------------------------------
    // POST /api/accounts/{accountId}/transactions tests
    //=================================

    def "BACKEND-003-AC-06: creates a transaction"() {
        given: "an authenticated user, an owned account, and a valid transaction creation request"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            TransactionCreateRequest request = positiveTransactionCreateRequest()
            UUID accountId = UUID.randomUUID()

        when: "POST /api/accounts/{accountId}/transactions is requested"
            def body = objectMapper.writeValueAsString(request)
            def result = mockMvc.perform(post(constructTransactionUri(accountId.toString()))
                .cookie(accessTokenCookie(user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 201 Created with the created transaction"
            result.andExpect(status().isCreated())
            String responseJson = result.andReturn().getResponse().getContentAsString()
            TransactionResponse response = objectMapper.readValue(responseJson, TransactionResponse)

        and: "the created transaction is owned by the authenticated user"
            1 * mockTransactionService.addTransaction(user.id.toString(), accountId, request) >> transactionResponse()

        and: "the response is in the expected format"
            response.amount() == transactionResponse().amount()
            response.date() == transactionResponse().date()
            response.description() == transactionResponse().description()
            response.rawDescription() == transactionResponse().rawDescription()
    }

    def "BACKEND-003-AC-07: returns 404 uniformly for a non-existent or non-owned account on create"() {
        given: "either a non-existent account id, or an account id owned by a different user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            TransactionCreateRequest request = positiveTransactionCreateRequest()
            UUID accountId = UUID.randomUUID()

            mockTransactionService.addTransaction(user.id.toString(), accountId, request) >>
                { throw new AccountNotFoundException("Account not found") }

        when: "POST /api/accounts/{accountId}/transactions is requested with that id"
            def body = objectMapper.writeValueAsString(request)
            def result = mockMvc.perform(post(constructTransactionUri(accountId.toString()))
                .cookie(accessTokenCookie(user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 404 Not Found in both cases"
            result.andExpect(status().isNotFound())
    }

    @Unroll
    def "BACKEND-003-AC-08: rejects creation with a missing or invalid amount - #scenario"() {
        given: "a creation request with either a missing amount or a non-numeric amount value"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()
            def payload = [
                "amount" : payloadAmount,
                "date" : TRANSACTION_DATE_AFTER_OPENED_ON,
                "description" : TRANSACTION_DESCRIPTION,
                "rawDescription" : TRANSACTION_RAW_DESCRIPTION
            ]

        when: "POST /api/accounts/{accountId}/transactions is requested"
            def body = objectMapper.writeValueAsString(payload)
            def result = mockMvc.perform(post(constructTransactionUri(accountId.toString()))
                .cookie(accessTokenCookie(user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "service is not called"
            0 * mockTransactionService.addTransaction(user.id.toString(), accountId, _ as TransactionCreateRequest)

        where:
            scenario      | payloadAmount
            "null"        | null
            "empty"       | ""
            "blank"       | "     "
            "non-numeric" | "£10.53"

    }

    @Unroll
    def "BACKEND-003-AC-09: rejects creation with description #scenario"() {
        given: "a creation request with a missing or blank description"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()
            TransactionCreateRequest request = new TransactionCreateRequest(
                TRANSACTION_AMOUNT_POSITIVE,
                TRANSACTION_DATE_AFTER_OPENED_ON,
                description,
                TRANSACTION_RAW_DESCRIPTION
            )

        when: "POST /api/accounts/{accountId}/transactions is requested"
            def body = objectMapper.writeValueAsString(request)
            def result = mockMvc.perform(post(constructTransactionUri(accountId.toString()))
                .cookie(accessTokenCookie(user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "service is not called"
            0 * mockTransactionService.addTransaction(user.id.toString(), accountId, _ as TransactionCreateRequest)

        where:
            scenario | description
            "null"   | null
            "empty"  | ""
            "blank"  | "     "
    }

    @Unroll
    def "BACKEND-003-AC-10: rejects creation with a missing date - #scenario"() {
        given: "a creation request with an invalid date"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()
            def payload = [
                "amount" : TRANSACTION_AMOUNT_POSITIVE,
                "date" : payloadDate,
                "description" : TRANSACTION_DESCRIPTION,
                "rawDescription" : TRANSACTION_RAW_DESCRIPTION
            ]

        when: "POST /api/accounts/{accountId}/transactions is requested"
            def body = objectMapper.writeValueAsString(payload)
            def result = mockMvc.perform(post(constructTransactionUri(accountId.toString()))
                .cookie(accessTokenCookie(user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "service is not called"
            0 * mockTransactionService.addTransaction(user.id.toString(), accountId, _ as TransactionCreateRequest)

        where:
            scenario         | payloadDate
            "null"           | null
            "empty"          | ""
            "blank"          | "     "
            "invalid format" | "1st January 2026"
    }

    def "BACKEND-003-AC-10: rejects creation with date too early"() {
        given: "a creation request with date before openedOn"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()
            TransactionCreateRequest request = new TransactionCreateRequest(
                TRANSACTION_AMOUNT_POSITIVE,
                TRANSACTION_DATE_BEFORE_OPENED_ON,
                TRANSACTION_DESCRIPTION,
                TRANSACTION_RAW_DESCRIPTION
            )

        and: "service throws date exception"
            mockTransactionService.addTransaction(user.id.toString(), accountId, request) >>
                { throw new TransactionDateException("Invalid date") }

        when: "POST /api/accounts/{accountId}/transactions is requested"
            def body = objectMapper.writeValueAsString(request)
            def result = mockMvc.perform(post(constructTransactionUri(accountId.toString()))
                .cookie(accessTokenCookie(user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())
    }

    @Unroll
    def "BACKEND-003-AC-11a: returns 400 for a supplied-but-invalid accountId on create - #scenario"() {
        given: "an authenticated user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            TransactionCreateRequest request = positiveTransactionCreateRequest()

        when: "POST /api/accounts/{accountId}/transactions is requested with an accountId that is not a UUID"
            def body = objectMapper.writeValueAsString(request)
            def result = mockMvc.perform(post("/api/accounts/" + accountId + "/transactions")
                .cookie(accessTokenCookie(user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "service is not called"
            0 * mockTransactionService.addTransaction(_ as String, _ as UUID, _ as TransactionCreateRequest)

        where:
            scenario   | accountId
            "null"     | null
            "non-uuid" | "not a uuid"
    }

    @Unroll
    def "BACKEND-003-AC-11b: returns 404 for an absent accountId on create - #scenario"() {
        given: "an authenticated user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            TransactionCreateRequest request = positiveTransactionCreateRequest()

        when: "POST /api/accounts/{accountId}/transactions is requested with an accountId that is empty or blank"
            def body = objectMapper.writeValueAsString(request)
            def result = mockMvc.perform(post("/api/accounts/" + accountId + "/transactions")
                .cookie(accessTokenCookie(user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 404 Not Found"
            result.andExpect(status().isNotFound())

        and: "service is not called"
            0 * mockTransactionService.addTransaction(_ as String, _ as UUID, _ as TransactionCreateRequest)

        where:
            scenario     | accountId
            "empty"      | ""
            "blank"      | "     "
    }

    //---------------------------------
    // GET /api/accounts/{accountId}/transactions tests
    //=================================

    def "BACKEND-003-AC-12: lists every transaction for an owned account"() {
        given: "an account owned by the authenticated user with several transactions recorded against it"

        when: "GET /api/accounts/{accountId}/transactions is requested with that account's id"

        then: "the response is 200 OK containing every transaction for that account"
    }

    def "BACKEND-003-AC-13: returns an empty list for an owned account with no transactions"() {
        given: "an account owned by the authenticated user with no transactions"

        when: "GET /api/accounts/{accountId}/transactions is requested with that account's id"

        then: "the response is 200 OK with an empty list"
    }

    def "BACKEND-003-AC-14: returns 404 uniformly for a non-existent or non-owned account on list"() {
        given: "either a non-existent account id, or an account id owned by a different user"

        when: "GET /api/accounts/{accountId}/transactions is requested with that id"

        then: "the response is 404 Not Found in both cases"
    }

    def "BACKEND-003-AC-11c: returns 400 for a supplied-but-invalid accountId on list"() {
        given: "an authenticated user"

        when: "GET /api/accounts/{accountId}/transactions is requested with an accountId that is not a UUID"

        then: "the response is 400 Bad Request"
    }

    def "BACKEND-003-AC-11d: returns 404 for an absent accountId on list"() {
        given: "an authenticated user"

        when: "GET /api/accounts/{accountId}/transactions is requested with an accountId that is empty or blank"

        then: "the response is 404 Not Found"
    }

    //---------------------------------
    // GET /api/accounts/{accountId}/transactions/{transactionId} tests
    //=================================

    def "BACKEND-003-AC-15: returns a single owned transaction"() {
        given: "a transaction owned (via its account) by the authenticated user"

        when: "GET /api/accounts/{accountId}/transactions/{transactionId} is requested with those ids"

        then: "the response is 200 OK with that transaction"
    }

    def "BACKEND-003-AC-16: returns 404 uniformly for not-found/not-owned account or cross-account/not-found transaction"() {
        given: "a non-existent or non-owned accountId, or a transactionId that does not exist or belongs to a different account"

        when: "GET /api/accounts/{accountId}/transactions/{transactionId} is requested with that combination"

        then: "the response is 404 Not Found in every case"
    }

    def "BACKEND-003-AC-11e: returns 400 for a supplied-but-invalid accountId on view"() {
        given: "an authenticated user"

        when: "GET /api/accounts/{accountId}/transactions/{transactionId} is requested with an accountId that is not a UUID"

        then: "the response is 400 Bad Request"
    }

    def "BACKEND-003-AC-11f: returns 404 for an absent accountId on view"() {
        given: "an authenticated user"

        when: "GET /api/accounts/{accountId}/transactions/{transactionId} is requested with an accountId that is empty or blank"

        then: "the response is 404 Not Found"
    }

    def "BACKEND-003-AC-17a: returns 400 for a supplied-but-invalid transactionId on view"() {
        given: "an authenticated user and an owned account"

        when: "GET /api/accounts/{accountId}/transactions/{transactionId} is requested with a transactionId that is not a UUID"

        then: "the response is 400 Bad Request"
    }

    def "BACKEND-003-AC-17b: returns 404 for an absent transactionId on view"() {
        given: "an authenticated user and an owned account"

        when: "GET /api/accounts/{accountId}/transactions/{transactionId} is requested with a transactionId that is empty or blank"

        then: "the response is 404 Not Found"
    }

    //---------------------------------
    // PATCH /api/accounts/{accountId}/transactions/{transactionId} tests
    //=================================

    def "BACKEND-003-AC-18: updates an owned transaction's amount, date, description, or rawDescription and re-derives the balance"() {
        given: "a transaction owned (via its account) by the authenticated user, and an update request with at least one of amount, date, description, or rawDescription present"

        when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested with those ids"

        then: "the response is 200 OK with the updated transaction"

        and: "any field omitted from the request is unchanged on the transaction"

        and: "the account's balance reflects the old amount reversed and the new amount applied"
    }

    def "BACKEND-003-AC-19: returns 404 uniformly for not-found/not-owned account or cross-account/not-found transaction on update"() {
        given: "a non-existent or non-owned accountId, or a transactionId that does not exist or belongs to a different account"

        when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested with that combination"

        then: "the response is 404 Not Found in every case"

        and: "no transaction is modified"
    }

    def "BACKEND-003-AC-20: rejects an update with no fields provided"() {
        given: "a transaction owned by the authenticated user and an update request with amount, date, description, and rawDescription all absent"

        when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested"

        then: "the response is 400 Bad Request"

        and: "the transaction is not modified"
    }

    def "BACKEND-003-AC-21: rejects an update with a present-but-blank description"() {
        given: "a transaction owned by the authenticated user and an update request where description is present but blank"

        when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested"

        then: "the response is 400 Bad Request"

        and: "the transaction is not modified"
    }

    def "BACKEND-003-AC-22: rejects an update with a present-but-too-early date"() {
        given: "a transaction owned by the authenticated user and an update request where date is present but earlier than the account's openedOn"

        when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested"

        then: "the response is 400 Bad Request"

        and: "the transaction is not modified"
    }

    def "BACKEND-003-AC-23: leaves the account association unchanged on update"() {
        given: "a transaction owned by the authenticated user and an update request body that also includes an account-identifying value"

        when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested"

        then: "the response is 200 OK"

        and: "the transaction's owning account is unchanged from before the request"
    }

    def "BACKEND-003-AC-23a: clears rawDescription to null when present but blank on update"() {
        given: "a transaction owned by the authenticated user with a previously-set rawDescription, and an update request where rawDescription is present but blank"

        when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested"

        then: "the response is 200 OK"

        and: "the transaction's rawDescription is null"
    }

    def "BACKEND-003-AC-11g: returns 400 for a supplied-but-invalid accountId on update"() {
        given: "an authenticated user"

        when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested with an accountId that is not a UUID"

        then: "the response is 400 Bad Request"

        and: "no transaction is modified"
    }

    def "BACKEND-003-AC-11h: returns 404 for an absent accountId on update"() {
        given: "an authenticated user"

        when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested with an accountId that is empty or blank"

        then: "the response is 404 Not Found"

        and: "no transaction is modified"
    }

    def "BACKEND-003-AC-17c: returns 400 for a supplied-but-invalid transactionId on update"() {
        given: "an authenticated user and an owned account"

        when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested with a transactionId that is not a UUID"

        then: "the response is 400 Bad Request"

        and: "no transaction is modified"
    }

    def "BACKEND-003-AC-17d: returns 404 for an absent transactionId on update"() {
        given: "an authenticated user and an owned account"

        when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested with a transactionId that is empty or blank"

        then: "the response is 404 Not Found"

        and: "no transaction is modified"
    }

    //---------------------------------
    // DELETE /api/accounts/{accountId}/transactions/{transactionId} tests
    //=================================

    def "BACKEND-003-AC-24: deletes an owned transaction and reverses the balance"() {
        given: "a transaction owned (via its account) by the authenticated user"

        when: "DELETE /api/accounts/{accountId}/transactions/{transactionId} is requested with those ids"

        then: "the response is 204 No Content"

        and: "the transaction no longer exists"

        and: "the account's balance has the transaction's amount reversed out of it"
    }

    def "BACKEND-003-AC-25: returns 404 uniformly for not-found/not-owned account or cross-account/not-found transaction on delete"() {
        given: "a non-existent or non-owned accountId, or a transactionId that does not exist or belongs to a different account"

        when: "DELETE /api/accounts/{accountId}/transactions/{transactionId} is requested with that combination"

        then: "the response is 404 Not Found in every case"

        and: "no transaction is deleted"
    }

    def "BACKEND-003-AC-11i: returns 400 for a supplied-but-invalid accountId on delete"() {
        given: "an authenticated user"

        when: "DELETE /api/accounts/{accountId}/transactions/{transactionId} is requested with an accountId that is not a UUID"

        then: "the response is 400 Bad Request"

        and: "no transaction is deleted"
    }

    def "BACKEND-003-AC-11j: returns 404 for an absent accountId on delete"() {
        given: "an authenticated user"

        when: "DELETE /api/accounts/{accountId}/transactions/{transactionId} is requested with an accountId that is empty or blank"

        then: "the response is 404 Not Found"

        and: "no transaction is deleted"
    }

    def "BACKEND-003-AC-17e: returns 400 for a supplied-but-invalid transactionId on delete"() {
        given: "an authenticated user and an owned account"

        when: "DELETE /api/accounts/{accountId}/transactions/{transactionId} is requested with a transactionId that is not a UUID"

        then: "the response is 400 Bad Request"

        and: "no transaction is deleted"
    }

    def "BACKEND-003-AC-17f: returns 404 for an absent transactionId on delete"() {
        given: "an authenticated user and an owned account"

        when: "DELETE /api/accounts/{accountId}/transactions/{transactionId} is requested with a transactionId that is empty or blank"

        then: "the response is 404 Not Found"

        and: "no transaction is deleted"
    }

    //---------------------------------
    // Authentication regression guard
    //=================================

    def "BACKEND-003-AC-27: rejects an unauthenticated request before the controller runs"() {
        given: "a request to a /api/accounts/{accountId}/transactions endpoint with no valid access-token cookie"

        when: "the request is made"

        then: "the response is 401 Unauthorized"

        and: "TransactionController is never invoked"
    }
}
