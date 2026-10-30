package uk.co.stefirby.budgetsandsubscriptions.controller

import jakarta.servlet.http.Cookie
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountUpdateRequest
import uk.co.stefirby.budgetsandsubscriptions.exception.AccountNotFoundException
import uk.co.stefirby.budgetsandsubscriptions.exception.JwtParserException

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static uk.co.stefirby.budgetsandsubscriptions.common.Constants.COOKIE_ACCESS_TOKEN
import static uk.co.stefirby.budgetsandsubscriptions.common.TestConstants.ROLE_USER
import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountConstants.*
import static uk.co.stefirby.budgetsandsubscriptions.security.util.CookieHelper.accessTokenCookie

@WebMvcTest(controllers = AccountController)
@Import(SecurityConfig)
class AccountControllerSpec extends Specification {

    private static final String ENDPOINT_ACCOUNTS = "/api/accounts"

    private static AccountCreateRequest ACCOUNT_CREATE_REQUEST = new AccountCreateRequest(
        ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME, AccountType.CURRENT, ACCOUNT_OPENING_BALANCE, ACCOUNT_OPENED_ON)

    private static AccountResponse ACCOUNT_RESPONSE_1 = new AccountResponse(UUID.randomUUID(), ACCOUNT_BANK_NAME,
        ACCOUNT_ACCOUNT_NAME, AccountType.CURRENT, ACCOUNT_OPENING_BALANCE, ACCOUNT_OPENED_ON, ACCOUNT_OPENING_BALANCE)
    private static AccountResponse ACCOUNT_RESPONSE_2 = new AccountResponse(UUID.randomUUID(), ACCOUNT_BANK_NAME_1,
        ACCOUNT_ACCOUNT_NAME_1, AccountType.SAVINGS, ACCOUNT_OPENING_BALANCE_1, ACCOUNT_OPENED_ON_1, ACCOUNT_OPENING_BALANCE_1)

    private static final AccountUpdateRequest ACCOUNT_UPDATE_REQUEST = new AccountUpdateRequest(
        ACCOUNT_BANK_NAME_1, ACCOUNT_ACCOUNT_NAME_1, AccountType.SAVINGS)

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

            AccountCreateRequest accountCreateRequest = ACCOUNT_CREATE_REQUEST

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

            AccountCreateRequest accountCreateRequest = ACCOUNT_CREATE_REQUEST

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

    @Unroll
    def "BACKEND-002-AC-17: updates an owned account's bank name, account name, and/or type"() {
        given: "an account owned by the authenticated user and an update request with at least one of bankName, accountName, or type present"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()

        when: "PATCH /api/accounts/{id} is requested with that account's id"
            def body = objectMapper.writeValueAsString(payload)
            def result = mockMvc.perform(patch(ENDPOINT_ACCOUNTS + "/" + accountId.toString())
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 200 OK with the updated account"
            result.andExpect(status().isOk())

        and: "the service got called with the authenticated user"
            1 * mockAccountService.updateAccount(user.id.toString(), accountId, expectedRequest) >> ACCOUNT_RESPONSE_1

        where:
            scenario                              | payload                                                     | expectedRequest
            "only bank name"                      | [bankName: "bank"]                                          | new AccountUpdateRequest("bank", null, null)
            "only account name"                   | [accountName: "account"]                                    | new AccountUpdateRequest(null, "account", null)
            "only valid account type"             | [type: "SAVINGS"]                                           | new AccountUpdateRequest(null, null, AccountType.SAVINGS)
            "bank name and account name"          | [bankName: "bank", accountName: "account"]                  | new AccountUpdateRequest("bank", "account", null)
            "bank name and valid account type"    | [bankName: "bank", type: "SAVINGS"]                         | new AccountUpdateRequest("bank", null, AccountType.SAVINGS)
            "account name and valid account type" | [accountName: "account", type: "SAVINGS"]                   | new AccountUpdateRequest(null, "account", AccountType.SAVINGS)
            "three valid fields"                  | [bankName: "bank", accountName: "account", type: "SAVINGS"] | new AccountUpdateRequest("bank", "account", AccountType.SAVINGS)
    }

    def "BACKEND-002-AC-18: returns 404 uniformly for a non-existent or non-owned account on update"() {
        given: "either a non-existent account id, or an account id owned by a different user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()
            AccountUpdateRequest request = ACCOUNT_UPDATE_REQUEST

        when: "PATCH /api/accounts/{id} is requested with that id"
            def body = objectMapper.writeValueAsString(request)
            def result = mockMvc.perform(patch(ENDPOINT_ACCOUNTS + "/" + accountId.toString())
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 404 Not Found in both cases"
            result.andExpect(status().isNotFound())

        and: "the service got called with the authenticated user"
            1 * mockAccountService.updateAccount(user.id.toString(), accountId, request) >>
                { throw new AccountNotFoundException("Account not found") }
    }

    @Unroll
    def "BACKEND-002-AC-19: rejects an update with #scenario"() {
        given: "an account owned by the authenticated user and an update request where bankName or accountName is present but blank"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()

        when: "PATCH /api/accounts/{id} is requested"
            def body = objectMapper.writeValueAsString(payload)
            def result = mockMvc.perform(patch(ENDPOINT_ACCOUNTS + "/" + accountId.toString())
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "no account is modified"
            0 * mockAccountService.updateAccount(_ as String, _ as UUID, _ as AccountUpdateRequest)

        where:
            scenario               | payload
            "empty bank name"      | [bankName: ""]
            "blank bank name"      | [bankName: "      "]
            "empty account name"   | [accountName: ""]
            "blank account name"   | [accountName: "      "]
    }

    @Unroll
    def "BACKEND-002-AC-19a: rejects an update with (#scenario)"() {
        given: "an account owned by the authenticated user and an update request with bankName, accountName, and type all absent"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()

        when: "PATCH /api/accounts/{id} is requested"
            def body = objectMapper.writeValueAsString(payload)
            def result = mockMvc.perform(patch(ENDPOINT_ACCOUNTS + "/" + accountId.toString())
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "no account is modified"
            0 * mockAccountService.updateAccount(_ as String, _ as UUID, _ as AccountUpdateRequest)

        where:
            scenario               | payload
            "null bank name"       | [bankName: null]
            "null account name"    | [accountName: null]
            "null account type"    | [type: null]
            "empty payload"        | [:]
    }

    @Unroll
    def "BACKEND-002-AC-19b: rejects an update with an unrecognised type value (#scenario)"() {
        given: "an account owned by the authenticated user and an update request where type is present but not a valid AccountType"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()

        when: "PATCH /api/accounts/{id} is requested"
            def body = objectMapper.writeValueAsString(payload)
            def result = mockMvc.perform(patch(ENDPOINT_ACCOUNTS + "/" + accountId.toString())
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "no account is modified"
            0 * mockAccountService.updateAccount(_ as String, _ as UUID, _ as AccountUpdateRequest)

        where:
            scenario               | payload
            "empty account type"   | [type: ""]
            "blank account type"   | [type: "      "]
            "unknown account type" | [type: "UNKNOWN"]
    }

    def "BACKEND-002-AC-20: leaves openingBalance, openedOn, and balance unchanged on update"() {
        given: "an account owned by the authenticated user and an update request body that also includes openingBalance, openedOn, and balance values"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()
            AccountUpdateRequest request = ACCOUNT_UPDATE_REQUEST

        when: "PATCH /api/accounts/{id} is requested"
            def payload = [
                "bankName" : ACCOUNT_BANK_NAME_1,
                "accountName" : ACCOUNT_ACCOUNT_NAME_1,
                "type" : AccountType.SAVINGS.name(),
                "openingBalance" : ACCOUNT_OPENING_BALANCE,
                "openedOn" : ACCOUNT_OPENED_ON,
                "balance": ACCOUNT_OPENING_BALANCE
            ]
            def body = objectMapper.writeValueAsString(payload)
            def result = mockMvc.perform(patch(ENDPOINT_ACCOUNTS + "/" + accountId.toString())
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 200 OK"
            result.andExpect(status().isOk())

        and: "the request to the service only contains the allowed fields (bankName, accountName and type)"
            1 * mockAccountService.updateAccount(user.id.toString(), accountId, request) >> ACCOUNT_RESPONSE_1
    }

    @Unroll
    def "BACKEND-002-AC-24c: returns 400 for a malformed (#scenario) id on update"() {
        given: "an authenticated user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            AccountUpdateRequest request = ACCOUNT_UPDATE_REQUEST

        when: "PATCH /api/accounts/{id} is requested with an malformed id"
            def body = objectMapper.writeValueAsString(request)
            def result = mockMvc.perform(patch(ENDPOINT_ACCOUNTS + "/" + accountId)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "no account is modified"
            0 * mockAccountService.updateAccount(_ as String, _ as UUID, _ as AccountUpdateRequest)

        where:
            scenario   | accountId
            "null"     | null
            "non-uuid" | "not a uuid"
    }

    @Unroll
    def "BACKEND-002-AC-24d: returns 404 for an absent (#scenario) account id on update"() {
        given: "an authenticated user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            AccountUpdateRequest request = ACCOUNT_UPDATE_REQUEST

        when: "PATCH /api/accounts/{id} is requested with an absent id"
            def body = objectMapper.writeValueAsString(request)
            def result = mockMvc.perform(patch(ENDPOINT_ACCOUNTS + "/" + accountId)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt))
                .contentType("application/json")
                .content(body))

        then: "the response is 404 Not Found"
            result.andExpect(status().isNotFound())

        and: "no account is modified"
            0 * mockAccountService.updateAccount(_ as String, _ as UUID, _ as AccountUpdateRequest)

        where:
            scenario     | accountId
            "empty"      | ""
            "blank"      | "     "
    }

    //---------------------------------
    // DELETE /api/accounts/{id} tests
    //=================================

    def "BACKEND-002-AC-21: deletes an owned account"() {
        given: "an account owned by the authenticated user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()

        when: "DELETE /api/accounts/{id} is requested with that account's id"
            def result = mockMvc.perform(delete(ENDPOINT_ACCOUNTS + "/" + accountId.toString())
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt)))

        then: "the response is 204 No Content"
            noExceptionThrown()
            result.andExpect(status().isNoContent())

        and: "the service got called with the authenticated user"
            1 * mockAccountService.deleteAccount(user.id.toString(), accountId) >> {}
    }

    def "BACKEND-002-AC-22: returns 404 uniformly for a non-existent or non-owned account on delete"() {
        given: "either a non-existent account id, or an account id owned by a different user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)
            UUID accountId = UUID.randomUUID()

        when: "DELETE /api/accounts/{id} is requested with that id"
            def result = mockMvc.perform(delete(ENDPOINT_ACCOUNTS + "/" + accountId.toString())
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt)))

        then: "the response is 404 Not Found in both cases"
            result.andExpect(status().isNotFound())

        and: "the service got called with the authenticated user"
            1 * mockAccountService.deleteAccount(user.id.toString(), accountId)  >>
                { throw new AccountNotFoundException("Account not found") }
    }

    @Unroll
    def "BACKEND-002-AC-24e: returns 400 for a supplied-but-invalid (non-UUID) id on delete"() {
        given: "an authenticated user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)

        when: "DELETE /api/accounts/{id} is requested with an id that is the string \"null\" or not a UUID"
            def result = mockMvc.perform(delete(ENDPOINT_ACCOUNTS + "/" + accountId)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt)))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "no account is deleted"
            0 * mockAccountService.deleteAccount(_ as String, _ as UUID)

        where:
            scenario   | accountId
            "null"     | null
            "non-uuid" | "not a uuid"
    }

    @Unroll
    def "BACKEND-002-AC-24f: returns 404 for an absent (empty or blank) id on delete"() {
        given: "an authenticated user"
            AuthenticatedUser user = createAuthenticatedUser(mockJwtService)

        when: "DELETE /api/accounts/{id} is requested with an id that is empty or blank"
            def result = mockMvc.perform(delete(ENDPOINT_ACCOUNTS + "/" + accountId)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, user.jwt)))

        then: "the response is 404 Not Found"
            result.andExpect(status().isNotFound())

        and: "no account is deleted"
            0 * mockAccountService.deleteAccount(_ as String, _ as UUID)

        where:
            scenario     | accountId
            "empty"      | ""
            "blank"      | "     "
    }

    //---------------------------------
    // Authentication regression guard
    //=================================

    @Unroll
    def "BACKEND-002-AC-23: CREATE ACCOUNT - rejects an unauthenticated request before the controller runs - #scenario"() {
        given: "a request to an /api/accounts endpoint with no valid access-token cookie"
            AccountCreateRequest accountCreateRequest = ACCOUNT_CREATE_REQUEST

            if (cookie != null) {
                mockJwtService.getClaim(_ as String) >> { throw new JwtParserException(exceptionMessage) }
            }

        when: "the request is made"
            def body = objectMapper.writeValueAsString(accountCreateRequest)
            def mvcRequest = post(ENDPOINT_ACCOUNTS)
                .contentType("application/json")
                .content(body)

            if (cookie != null) {
                mvcRequest = mvcRequest.cookie(accessTokenCookie(cookie))
            }

            def result = mockMvc.perform(mvcRequest)

        then: "the response is 401 Unauthorized"
            result.andExpect(status().isUnauthorized())

        and: "AccountController is never invoked"
            0 * mockAccountService.createAccount(_ as String, _ as AccountCreateRequest)

        where:
            scenario           | cookie           | exceptionMessage
            "no cookie"        | null             | "no cookie"
            "malformed cookie" | "bad cookie"     | "bad token"
            "expired cookie"   | "expired cookie" | "expired token"
    }

    @Unroll
    def "BACKEND-002-AC-23: GET ACCOUNTS - rejects an unauthenticated request before the controller runs - #scenario"() {
        given: "a request to an /api/accounts endpoint with no valid access-token cookie"
            if (cookie != null) {
                mockJwtService.getClaim(_ as String) >> { throw new JwtParserException(exceptionMessage) }
            }

        when: "the request is made"
            def mvcRequest = get(ENDPOINT_ACCOUNTS)
                .contentType("application/json")

            if (cookie != null) {
                mvcRequest = mvcRequest.cookie(accessTokenCookie(cookie))
            }

            def result = mockMvc.perform(mvcRequest)

        then: "the response is 401 Unauthorized"
            result.andExpect(status().isUnauthorized())

        and: "AccountController is never invoked"
            0 * mockAccountService.getAccounts(_ as String)

        where:
            scenario           | cookie           | exceptionMessage
            "no cookie"        | null             | "no cookie"
            "malformed cookie" | "bad cookie"     | "bad token"
            "expired cookie"   | "expired cookie" | "expired token"
    }

    @Unroll
    def "BACKEND-002-AC-23: GET ACCOUNT - rejects an unauthenticated request before the controller runs - #scenario"() {
        given: "a request to an /api/accounts endpoint with no valid access-token cookie"
            if (cookie != null) {
                mockJwtService.getClaim(_ as String) >> { throw new JwtParserException(exceptionMessage) }
            }

        when: "the request is made"
            def mvcRequest = get(ENDPOINT_ACCOUNTS + "/" + UUID.randomUUID().toString())
                .contentType("application/json")

            if (cookie != null) {
                mvcRequest = mvcRequest.cookie(accessTokenCookie(cookie))
            }

            def result = mockMvc.perform(mvcRequest)

        then: "the response is 401 Unauthorized"
            result.andExpect(status().isUnauthorized())

        and: "AccountController is never invoked"
            0 * mockAccountService.getAccount(_ as String, _ as UUID)

        where:
            scenario           | cookie           | exceptionMessage
            "no cookie"        | null             | "no cookie"
            "malformed cookie" | "bad cookie"     | "bad token"
            "expired cookie"   | "expired cookie" | "expired token"
    }

    @Unroll
    def "BACKEND-002-AC-23: UPDATE ACCOUNT - rejects an unauthenticated request before the controller runs - #scenario"() {
        given: "a request to an /api/accounts endpoint with no valid access-token cookie"
            AccountUpdateRequest accountUpdateRequest = ACCOUNT_UPDATE_REQUEST

            if (cookie != null) {
                mockJwtService.getClaim(_ as String) >> { throw new JwtParserException(exceptionMessage) }
            }

        when: "the request is made"
            def body = objectMapper.writeValueAsString(accountUpdateRequest)
            def mvcRequest = patch(ENDPOINT_ACCOUNTS + "/" + UUID.randomUUID().toString())
                .contentType("application/json")
                .content(body)

            if (cookie != null) {
                mvcRequest = mvcRequest.cookie(accessTokenCookie(cookie))
            }

            def result = mockMvc.perform(mvcRequest)

        then: "the response is 401 Unauthorized"
            result.andExpect(status().isUnauthorized())

        and: "AccountController is never invoked"
            0 * mockAccountService.updateAccount(_ as String, _ as UUID, _ as AccountUpdateRequest)

        where:
            scenario           | cookie           | exceptionMessage
            "no cookie"        | null             | "no cookie"
            "malformed cookie" | "bad cookie"     | "bad token"
            "expired cookie"   | "expired cookie" | "expired token"
    }

    @Unroll
    def "BACKEND-002-AC-23: DELETE ACCOUNT - rejects an unauthenticated request before the controller runs - #scenario"() {
        given: "a request to an /api/accounts endpoint with no valid access-token cookie"
            if (cookie != null) {
                mockJwtService.getClaim(_ as String) >> { throw new JwtParserException(exceptionMessage) }
            }

        when: "the request is made"
            def mvcRequest = delete(ENDPOINT_ACCOUNTS + "/" + UUID.randomUUID().toString())
                .contentType("application/json")

            if (cookie != null) {
                mvcRequest = mvcRequest.cookie(accessTokenCookie(cookie))
            }

            def result = mockMvc.perform(mvcRequest)

        then: "the response is 401 Unauthorized"
            result.andExpect(status().isUnauthorized())

        and: "AccountController is never invoked"
            0 * mockAccountService.deleteAccount(_ as String, _ as UUID)

        where:
            scenario           | cookie           | exceptionMessage
            "no cookie"        | null             | "no cookie"
            "malformed cookie" | "bad cookie"     | "bad token"
            "expired cookie"   | "expired cookie" | "expired token"
    }
}
