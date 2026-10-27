package uk.co.stefirby.budgetsandsubscriptions.controller

import jakarta.servlet.http.Cookie
import uk.co.stefirby.budgetsandsubscriptions.exception.AccountNotFoundException

import java.time.Clock
import java.time.LocalDate
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.databind.ObjectMapper
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountResponse
import uk.co.stefirby.budgetsandsubscriptions.dto.JwtClaim
import uk.co.stefirby.budgetsandsubscriptions.model.AccountType
import uk.co.stefirby.budgetsandsubscriptions.security.JwtService
import uk.co.stefirby.budgetsandsubscriptions.security.SecurityConfig
import uk.co.stefirby.budgetsandsubscriptions.security.util.JwtHelper
import uk.co.stefirby.budgetsandsubscriptions.service.AccountService

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static uk.co.stefirby.budgetsandsubscriptions.common.Constants.COOKIE_ACCESS_TOKEN
import static uk.co.stefirby.budgetsandsubscriptions.common.TestConstants.ROLE_USER
import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountConstants.*

@WebMvcTest(controllers = AccountController)
@Import(SecurityConfig)
class AccountControllerSpec extends Specification {

    private static final String ENDPOINT_ACCOUNTS = "/api/accounts"

    private static AccountResponse ACCOUNT_RESPONSE_1 = new AccountResponse(UUID.randomUUID(), ACCOUNT_BANK_NAME,
        ACCOUNT_ACCOUNT_NAME, AccountType.CURRENT, ACCOUNT_OPENING_BALANCE, ACCOUNT_OPENED_ON, ACCOUNT_OPENING_BALANCE)
    private static AccountResponse ACCOUNT_RESPONSE_2 = new AccountResponse(UUID.randomUUID(), ACCOUNT_BANK_NAME_1,
        ACCOUNT_ACCOUNT_NAME_1, AccountType.SAVINGS, ACCOUNT_OPENING_BALANCE_1, ACCOUNT_OPENED_ON_1, ACCOUNT_OPENING_BALANCE_1)

    @Autowired
    MockMvc mockMvc

    @Autowired
    ObjectMapper objectMapper

    @SpringBean
    AccountService mockAccountService = Mock()

    @SpringBean
    JwtService mockJwtService = Mock()

    @Shared
    JwtHelper jwtHelper = new JwtHelper()

    static class AuthenticatedUser {
        UUID id
        String jwt
    }

    private AuthenticatedUser createAuthenticatedUser(JwtService mockJwtService) {
        UUID authenticatedUuid = UUID.randomUUID()
        String jwt = jwtHelper.buildJwt(authenticatedUuid.toString(), ROLE_USER)
        mockJwtService.getClaim(jwt) >> new JwtClaim(authenticatedUuid.toString(), ROLE_USER)
        return new AuthenticatedUser(id: authenticatedUuid, jwt: jwt)
    }

    //---------------------------------
    // POST /api/accounts tests
    //=================================

    def "BACKEND-002-AC-07: creates and returns a new account for the authenticated user"() {
        given: "an authenticated user and a valid account creation request"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)

            AccountCreateRequest accountCreateRequest = new AccountCreateRequest(
                ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME, AccountType.CURRENT, ACCOUNT_OPENING_BALANCE, ACCOUNT_OPENED_ON)

        when: "POST /api/accounts is requested"
            def body = objectMapper.writeValueAsString(accountCreateRequest)
            def result = mockMvc.perform(post(ENDPOINT_ACCOUNTS)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 201 Created with the created account"
            result.andExpect(status().isCreated())
            String responseJson = result.andReturn().getResponse().getContentAsString()
            AccountResponse response = objectMapper.readValue(responseJson, AccountResponse)

        and: "the created account is owned by the authenticated user"
            1 * mockAccountService.createAccount(user.id.toString(), accountCreateRequest) >> ACCOUNT_RESPONSE_1

        and: "the created account's balance equals the given openingBalance"
            response.balance() == ACCOUNT_OPENING_BALANCE
    }

    def "BACKEND-002-AC-08: ignores a client-supplied owner id"() {
        given: "an authenticated user and a creation request body containing a different user's id as owner"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)

            AccountCreateRequest accountCreateRequest = new AccountCreateRequest(
                ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME, AccountType.CURRENT, ACCOUNT_OPENING_BALANCE, ACCOUNT_OPENED_ON)

        when: "POST /api/accounts is requested"
            UUID differentUuid = UUID.randomUUID()
            def payload = [
                "bankName" : ACCOUNT_BANK_NAME,
                "accountName" : ACCOUNT_ACCOUNT_NAME,
                "type" : AccountType.CURRENT.name(),
                "openingBalance" : ACCOUNT_OPENING_BALANCE,
                "openedOn" : ACCOUNT_OPENED_ON,
                "userId": differentUuid
            ]
            def body = objectMapper.writeValueAsString(payload)
            def result = mockMvc.perform(post(ENDPOINT_ACCOUNTS)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the created account's owner is the authenticated user, not the id from the request body"
            1 * mockAccountService.createAccount(user.id.toString(), accountCreateRequest)
    }

    @Unroll
    def "BACKEND-002-AC-09: rejects account creation with a #scenario"() {
        given: "a creation request with a blank or missing bankName or accountName"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            AccountCreateRequest accountCreateRequest = new AccountCreateRequest(
                bankName, accountName, AccountType.CURRENT, ACCOUNT_OPENING_BALANCE, ACCOUNT_OPENED_ON)

        when: "POST /api/accounts is requested"
            def body = objectMapper.writeValueAsString(accountCreateRequest)
            def result = mockMvc.perform(post(ENDPOINT_ACCOUNTS)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "no account is created"
            0 * mockAccountService.createAccount(user.id.toString(), _ as AccountCreateRequest)

        where:
            scenario             | bankName          | accountName
            "null bank name"     | null              | ACCOUNT_ACCOUNT_NAME
            "empty bank name"    | ""                | ACCOUNT_ACCOUNT_NAME
            "blank bank name"    | "     "           | ACCOUNT_ACCOUNT_NAME
            "null account name"  | ACCOUNT_BANK_NAME | null
            "empty account name" | ACCOUNT_BANK_NAME | ""
            "blank account name" | ACCOUNT_BANK_NAME | "     "
    }

    @Unroll
    def "BACKEND-002-AC-10: rejects account creation where account type is #scenario"() {
        given: "a creation request with either a missing type or a type value outside the allowed enum"
            def payload = [
                "bankName" : ACCOUNT_BANK_NAME,
                "accountName" : ACCOUNT_ACCOUNT_NAME,
                "type" : accountType,
                "openingBalance" : ACCOUNT_OPENING_BALANCE,
                "openedOn" : ACCOUNT_OPENED_ON
            ]
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)

        when: "POST /api/accounts is requested"
            def body = objectMapper.writeValueAsString(payload)
            def result = mockMvc.perform(post(ENDPOINT_ACCOUNTS)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "no account is created"
            0 * mockAccountService.createAccount(user.id.toString(), _ as AccountCreateRequest)

        where:
            scenario  | accountType
            "null"    | null
            "empty"   | ""
            "blank"   | "     "
            "unknown" | "UNKNOWN"
    }

    def "BACKEND-002-AC-11: rejects account creation with a missing opening balance"() {
        given: "a creation request with a missing openingBalance"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)

            AccountCreateRequest accountCreateRequest = new AccountCreateRequest(
                ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME, AccountType.CURRENT, null, ACCOUNT_OPENED_ON)

        when: "POST /api/accounts is requested"
            def body = objectMapper.writeValueAsString(accountCreateRequest)
            def result = mockMvc.perform(post(ENDPOINT_ACCOUNTS)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "no account is created"
            0 * mockAccountService.createAccount(user.id.toString(), _ as AccountCreateRequest)
    }

    @Unroll
    def "BACKEND-002-AC-12: rejects account creation with a #scenario openedOn"() {
        given: "a creation request with either a missing openedOn or an openedOn after today"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)

            AccountCreateRequest accountCreateRequest = new AccountCreateRequest(
                ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME, AccountType.CURRENT, ACCOUNT_OPENING_BALANCE, openedOn)

        when: "POST /api/accounts is requested"
            def body = objectMapper.writeValueAsString(accountCreateRequest)
            def result = mockMvc.perform(post(ENDPOINT_ACCOUNTS)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "no account is created"
            0 * mockAccountService.createAccount(user.id.toString(), _ as AccountCreateRequest)

        where:
            scenario       | openedOn
            "missing"      | null
            "future-dated" | LocalDate.of(2099, 12, 31)
    }

    @Unroll
    def "BACKEND-002-AC-12: accepts account creation with an openedOn date set #scenario"() {
        given: "a creation request with openedOn in the past or today"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)

            AccountCreateRequest accountCreateRequest = new AccountCreateRequest(
                ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME, AccountType.CURRENT, ACCOUNT_OPENING_BALANCE, openedOn)

        when: "POST /api/accounts is requested"
            def body = objectMapper.writeValueAsString(accountCreateRequest)
            def result = mockMvc.perform(post(ENDPOINT_ACCOUNTS)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 201 Created"
            result.andExpect(status().isCreated())

        where:
            scenario      | openedOn
            "in the past" | LocalDate.of(2020, 01, 01)
            "on today"    | LocalDate.now(Clock.systemDefaultZone())
    }

    //---------------------------------
    // GET /api/accounts tests
    //=================================

    def "BACKEND-002-AC-13 / BACKEND-002-AC-14: lists only the authenticated user's own accounts"() {
        given: "authenticated user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)

        when: "GET /api/accounts is requested"
            def result = mockMvc.perform(get(ENDPOINT_ACCOUNTS)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json"))

        then: "the response is 200 OK"
            result.andExpect(status().isOk())

        and: "the service got called with the authenticated user"
            1 * mockAccountService.getAccounts(user.id.toString()) >> List.of(ACCOUNT_RESPONSE_1, ACCOUNT_RESPONSE_2)

        and: "two accounts found"
            String responseJson = result.andReturn().getResponse().getContentAsString()
            List<AccountResponse> response = objectMapper.readValue(responseJson, AccountResponse[].class).toList()
            response.size() == 2
    }

    def "BACKEND-002-AC-14: returns an empty list for a user with no accounts"() {
        given: "authenticated user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)

        when: "GET /api/accounts is requested"
            def result = mockMvc.perform(get(ENDPOINT_ACCOUNTS)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json"))

        then: "the response is 200 OK"
            result.andExpect(status().isOk())

        and: "the service got called with the authenticated user"
            1 * mockAccountService.getAccounts(user.id.toString()) >> List.of()

        and: "no accounts found"
            String responseJson = result.andReturn().getResponse().getContentAsString()
            List<AccountResponse> response = objectMapper.readValue(responseJson, AccountResponse[].class).toList()
            response.size() == 0

    }

    //---------------------------------
    // GET /api/accounts/{id} tests
    //=================================

    def "BACKEND-002-AC-15: returns a single owned account"() {
        given: "an account owned by the authenticated user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()

        when: "GET /api/accounts/{id} is requested with that account's id"
            def result = mockMvc.perform(get(ENDPOINT_ACCOUNTS + "/" + accountId.toString())
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json"))

        then: "the response is 200 OK with that account"
            result.andExpect(status().isOk())

        and: "the service got called with the authenticated user"
            1 * mockAccountService.getAccount(user.id.toString(), accountId) >> ACCOUNT_RESPONSE_1
    }

    def "BACKEND-002-AC-16: returns 404 uniformly for a non-existent or non-owned account"() {
        given: "either a non-existent account id, or an account id owned by a different user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()

        when: "GET /api/accounts/{id} is requested with that id"
            def result = mockMvc.perform(get(ENDPOINT_ACCOUNTS + "/" + accountId.toString())
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json"))

        then: "the response is 404 Not Found in both cases"
            result.andExpect(status().isNotFound())

        and: "the service got called with the authenticated user"
            1 * mockAccountService.getAccount(user.id.toString(), accountId) >>
                { throw new AccountNotFoundException("Account not found") }
    }

    @Unroll
    def "BACKEND-002-AC-24a: returns 400 for a malformed (#scenario) id"() {
        given: "an authenticated user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)

        when: "GET /api/accounts/{id} is requested with an id that is null, empty, blank, or not a UUID"
            def result = mockMvc.perform(get(ENDPOINT_ACCOUNTS + "/" + accountId)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json"))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        where:
            scenario   | accountId
            "null"     | null
            "non-uuid" | "not a uuid"
    }

    @Unroll
    def "BACKEND-002-AC-24b: returns 404 for an absent (#scenario) id"() {
        given: "an authenticated user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)

        when: "GET /api/accounts/{id} is requested with an id that is empty or blank"
            def result = mockMvc.perform(get(ENDPOINT_ACCOUNTS + "/" + accountId)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json"))

        then: "the response is 404 Not Found"
            result.andExpect(status().isNotFound())

        where:
            scenario     | accountId
            "empty"      | ""
            "blank"      | "     "
    }

    //---------------------------------
    // PATCH /api/accounts/{id} tests
    //=================================

    def "BACKEND-002-AC-17: updates an owned account's bank name, account name, and/or type"() {
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

    def "BACKEND-002-AC-19: rejects an update with a blank bank name or account name"() {
        given: "an account owned by the authenticated user and an update request with a blank bankName or accountName"

        when: "PATCH /api/accounts/{id} is requested"

        then: "the response is 400 Bad Request"

        and: "the account is not modified"
    }

    def "BACKEND-002-AC-20: leaves openingBalance, openedOn, and balance unchanged on update"() {
        given: "an account owned by the authenticated user and an update request body that also includes openingBalance, openedOn, and balance values"

        when: "PATCH /api/accounts/{id} is requested"

        then: "the response is 200 OK"

        and: "the account's openingBalance, openedOn, and balance are unchanged from before the request"

        and: "only bankName, accountName, and/or type reflect the request"
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
