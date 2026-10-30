package uk.co.stefirby.budgetsandsubscriptions.controller

import jakarta.servlet.http.Cookie
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.databind.ObjectMapper
import uk.co.stefirby.budgetsandsubscriptions.dto.JwtClaim
import uk.co.stefirby.budgetsandsubscriptions.dto.LoginRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.UserResponse
import uk.co.stefirby.budgetsandsubscriptions.exception.DuplicateEmailException
import uk.co.stefirby.budgetsandsubscriptions.exception.JwtParserException
import uk.co.stefirby.budgetsandsubscriptions.exception.LoginException
import uk.co.stefirby.budgetsandsubscriptions.security.JwtService
import uk.co.stefirby.budgetsandsubscriptions.security.SecurityConfig
import uk.co.stefirby.budgetsandsubscriptions.security.util.JwtHelper
import uk.co.stefirby.budgetsandsubscriptions.service.AuthService

import static org.hamcrest.Matchers.aMapWithSize
import static org.hamcrest.Matchers.allOf
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.not
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import static uk.co.stefirby.budgetsandsubscriptions.common.Constants.COOKIE_ACCESS_TOKEN
import static uk.co.stefirby.budgetsandsubscriptions.common.TestConstants.ROLE_USER
import static uk.co.stefirby.budgetsandsubscriptions.security.util.CookieHelper.accessTokenCookie

@WebMvcTest(controllers = AuthController)
@Import(SecurityConfig)
class AuthControllerSpec extends Specification {

    private static final String ENDPOINT_LOGIN = "/api/auth/login"
    private static final String ENDPOINT_REGISTER = "/api/auth/register"
    private static final String ENDPOINT_GET_CURRENT_USER = "/api/auth/me"
    private static final String ENDPOINT_LOGOUT = "/api/auth/logout"

    private static final String NEW_EMAIL_ADDRESS = "new.user@test.com"
    private static final String VALID_PASSWORD = "validPassword"
    private static final String DISPLAY_NAME = "Display Name"
    private static final String MOCK_JWT = "mock.jwt"
    private static final String DUMMY_MESSAGE = "dummy message"

    @Autowired
    MockMvc mockMvc

    @Autowired
    ObjectMapper objectMapper

    @SpringBean
    AuthService mockAuthService = Mock()

    @SpringBean
    JwtService mockJwtService = Mock()

    @Shared
    JwtHelper jwtHelper = new JwtHelper()

    private void setupMockForSuccessfulRegistration(RegisterRequest registerRequest, UUID uuid) {
        def validResponse = new UserResponse(uuid, registerRequest.email(), registerRequest.displayName(), ROLE_USER)
        mockAuthService.register(registerRequest) >> validResponse
        mockJwtService.createJWT(uuid.toString(), ROLE_USER) >> MOCK_JWT
    }

    private void setupMockForSuccessfulLogin(LoginRequest loginRequest, UUID uuid) {
        def validResponse = new UserResponse(uuid, loginRequest.email(), DISPLAY_NAME, ROLE_USER)
        mockAuthService.login(loginRequest) >> validResponse
        mockJwtService.createJWT(uuid.toString(), ROLE_USER) >> MOCK_JWT
    }

    private static UUID getRandomUuid() {
        return UUID.randomUUID()
    }

    //---------------------------------
    // /api/auth/registration tests
    //=================================

    def "AUTH-001-AC-05: registers a new user with a valid request and logs them in"() {
        given: "a valid registration request (unused email, 12+ char password, non-blank displayName)"
            def newRegisterRequest = new RegisterRequest(NEW_EMAIL_ADDRESS, VALID_PASSWORD, DISPLAY_NAME)
            UUID uuid = getRandomUuid()
            setupMockForSuccessfulRegistration(newRegisterRequest, uuid)

        when: "POST /api/auth/register is requested"
            def body = objectMapper.writeValueAsString(newRegisterRequest)
            def result = mockMvc.perform(post(ENDPOINT_REGISTER)
                .contentType("application/json")
                .content(body))

        then: "the response is 201 Created with the new user's id, email, displayName, and role"
            result.andExpect(status().isCreated())
            String responseJson = result.andReturn().getResponse().getContentAsString()
            UserResponse response = objectMapper.readValue(responseJson, UserResponse)
            response.id() == uuid
            response.email() == NEW_EMAIL_ADDRESS
            response.displayName() == DISPLAY_NAME
            response.role() == ROLE_USER

        and: "the response sets an httpOnly, Secure, SameSite=Strict access-token cookie expiring in 15 minutes"
            result.andExpect(cookie().httpOnly(COOKIE_ACCESS_TOKEN, true))
            result.andExpect(cookie().secure(COOKIE_ACCESS_TOKEN, true))
            result.andExpect(cookie().maxAge(COOKIE_ACCESS_TOKEN, 15 * 60))
            result.andExpect(cookie().sameSite(COOKIE_ACCESS_TOKEN, "Strict"))

        and: "(AUTH-001-AC-09) the response body contains neither a password field, a password-hash field, nor the raw JWT string"
            result.andExpect(jsonPath('$.password').doesNotExist())
            result.andExpect(jsonPath('$.passwordHash').doesNotExist())
            result.andExpect(content().string(not(containsString(MOCK_JWT))))

    }

    def "AUTH-001-AC-06: rejects registration with a duplicate email (case-insensitive)"() {
        given: "an existing user with a given email"
            mockAuthService.register(_ as RegisterRequest) >> { throw new DuplicateEmailException(DUMMY_MESSAGE) }

        when: "POST /api/auth/register is requested with the same email in a different case"
            def existingRegisterRequest = new RegisterRequest(
                "existing.user@test.com", VALID_PASSWORD, DISPLAY_NAME)
            def body = objectMapper.writeValueAsString(existingRegisterRequest)
            def result = mockMvc.perform(post(ENDPOINT_REGISTER)
                .contentType("application/json")
                .content(body))

        then: "the response is 409 Conflict"
            result.andExpect(status().isConflict())

        and: "(AUTH-001-AC-09) the response body contains neither a password field, a password-hash field, nor the raw JWT string"
            result.andExpect(jsonPath('$.password').doesNotExist())
            result.andExpect(jsonPath('$.passwordHash').doesNotExist())
            result.andExpect(content().string(not(containsString(MOCK_JWT))))

    }

    @Unroll
    def "AUTH-001-AC-07: rejects registration with password that is #scenario"() {
        given: "a registration request with an invalid password"
            def newRegisterRequest = new RegisterRequest(NEW_EMAIL_ADDRESS, password, DISPLAY_NAME)

        when: "POST /api/auth/register is requested"
            def body = objectMapper.writeValueAsString(newRegisterRequest)
            def result = mockMvc.perform(post(ENDPOINT_REGISTER)
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "no new user is created"
            0 * mockAuthService.register(_ as RegisterRequest)

        and: "(AUTH-001-AC-09) the response body contains neither a password field, a password-hash field, nor the raw JWT string"
            result.andExpect(jsonPath('$.password').doesNotExist())
            result.andExpect(jsonPath('$.passwordHash').doesNotExist())
            result.andExpect(content().string(not(containsString(MOCK_JWT))))

        and: "validation errors are returned"
            result.andExpect(jsonPath('$.details.password',
                allOf(
                    hasSize(errorCount),
                    hasItem(containsString(errorContains))
                )))


        where:
            scenario                          | password         | errorCount | errorContains
            "null"                            | null             | 1          | "blank"
            "empty"                           | ""               | 2          | "blank"
            "blank and too short"             | "   "            | 2          | "size"
            "blank but 12 or more characters" | "              " | 1          | "blank"
            "too short (boundary case)"       | "1234567890A"    | 1          | "size"
    }

    def "AUTH-001-AC-07: accepts registration with password that is not blank and has at least 12 characters"() {
        given: "a registration request with a password under 12 characters"
            def newRegisterRequest = new RegisterRequest(NEW_EMAIL_ADDRESS, "1234567890AB", DISPLAY_NAME)
            UUID uuid = getRandomUuid()
            setupMockForSuccessfulRegistration(newRegisterRequest, uuid)

        when: "POST /api/auth/register is requested"
            def body = objectMapper.writeValueAsString(newRegisterRequest)
            def result = mockMvc.perform(post(ENDPOINT_REGISTER)
                .contentType("application/json")
                .content(body))

        then: "the response is 201 created"
            result.andExpect(status().isCreated())

        and: "(AUTH-001-AC-09) the response body contains neither a password field, a password-hash field, nor the raw JWT string"
            result.andExpect(jsonPath('$.password').doesNotExist())
            result.andExpect(jsonPath('$.passwordHash').doesNotExist())
            result.andExpect(content().string(not(containsString(MOCK_JWT))))
    }

    @Unroll
    def "AUTH-001-AC-08: rejects registration with a #scenario"() {
        given: "a registration request with invalid data"
            def newRegisterRequest = new RegisterRequest(email, VALID_PASSWORD, displayName)

        when: "POST /api/auth/register is requested"
            def body = objectMapper.writeValueAsString(newRegisterRequest)
            def result = mockMvc.perform(post(ENDPOINT_REGISTER)
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "no new user is created"
            0 * mockAuthService.register(_ as RegisterRequest)

        and: "(AUTH-001-AC-09) the response body contains neither a password field, a password-hash field, nor the raw JWT string"
            result.andExpect(jsonPath('$.password').doesNotExist())
            result.andExpect(jsonPath('$.passwordHash').doesNotExist())
            result.andExpect(content().string(not(containsString(MOCK_JWT))))

        where:
            scenario                                 | email           | displayName
            "null email and valid display name"      | null            | "validName"
            "malformed email and valid display name" | "notAnEmail"    | "validName"
            "blank email and valid display name"     | "      "        | "validName"
            "empty email and valid display name"     | ""              | "validName"
            "valid email and null display name"      | "test@test.com" | null
            "valid email and blank display name"     | "test@test.com" | "     "
            "valid email and empty display name"     | "test@test.com" | ""
            "both invalid email and display name"    | null            | null

    }

    //---------------------------------
    // /api/auth/login tests
    //=================================

    def "AUTH-001-AC-10: successful login issues an access-token cookie"() {
        given: "an existing user with a known email and password"
            def newLoginRequest = new LoginRequest(NEW_EMAIL_ADDRESS, VALID_PASSWORD)
            UUID uuid = getRandomUuid()
            setupMockForSuccessfulLogin(newLoginRequest, uuid)

        when: "POST /api/auth/login is requested with matching credentials"
            def body = objectMapper.writeValueAsString(newLoginRequest)
            def result = mockMvc.perform(post(ENDPOINT_LOGIN)
                .contentType("application/json")
                .content(body))

        then: "the response is 200 OK with the user's id, email, displayName, and role"
            result.andExpect(status().isOk())
            String responseJson = result.andReturn().getResponse().getContentAsString()
            UserResponse response = objectMapper.readValue(responseJson, UserResponse)
            response.id() == uuid
            response.email() == NEW_EMAIL_ADDRESS
            response.displayName() == DISPLAY_NAME
            response.role() == ROLE_USER

        and: "the response sets an httpOnly, Secure, SameSite=Strict access-token cookie expiring in 15 minutes"
            result.andExpect(cookie().httpOnly(COOKIE_ACCESS_TOKEN, true))
            result.andExpect(cookie().secure(COOKIE_ACCESS_TOKEN, true))
            result.andExpect(cookie().maxAge(COOKIE_ACCESS_TOKEN, 15 * 60))
            result.andExpect(cookie().sameSite(COOKIE_ACCESS_TOKEN, "Strict"))
    }

    def "AUTH-001-AC-11: rejects login with a non-existent email or a wrong password"() {
        given: "either no user with the given email, or a user with a non-matching password"
            def newLoginRequest = new LoginRequest(email, password)
            mockAuthService.login(_ as LoginRequest) >> { throw new LoginException(DUMMY_MESSAGE) }

        when: "POST /api/auth/login is requested"
            def body = objectMapper.writeValueAsString(newLoginRequest)
            def result = mockMvc.perform(post(ENDPOINT_LOGIN)
                .contentType("application/json")
                .content(body))

        then: "the response is 401 Unauthorized with a generic message"
            result.andExpect(status().isUnauthorized())

        and: "no access-token cookie is set"
            result.andExpect(cookie().doesNotExist(COOKIE_ACCESS_TOKEN))

        where:
            scenario                | email                    | password
            "non-existent email"    | "non.existent@test.com"  | "correctPassword"
            "non-matching password" | "existing.user@test.com" | "wrongPassword"

    }

    def "AUTH-001-AC-13: login response never includes credentials or the raw token"() {
        given: "a successful login"
            def newLoginRequest = new LoginRequest(NEW_EMAIL_ADDRESS, VALID_PASSWORD)
            UUID uuid = getRandomUuid()
            setupMockForSuccessfulLogin(newLoginRequest, uuid)

        when: "POST /api/auth/login is requested"
            def body = objectMapper.writeValueAsString(newLoginRequest)
            def result = mockMvc.perform(post(ENDPOINT_LOGIN)
                .contentType("application/json")
                .content(body))

        then: "the response body contains neither the password hash nor the raw JWT string"
            result.andExpect(jsonPath('$.password').doesNotExist())
            result.andExpect(jsonPath('$.passwordHash').doesNotExist())
            result.andExpect(content().string(not(containsString(MOCK_JWT))))
    }

    @Unroll
    def "AUTH-001-AC-19: rejects login with #scenario"() {
        given: "a login request with an invalid password"
            def newLoginRequest = new LoginRequest(email, password)

        when: "POST /api/auth/login is requested"
            def body = objectMapper.writeValueAsString(newLoginRequest)
            def result = mockMvc.perform(post(ENDPOINT_LOGIN)
                .contentType("application/json")
                .content(body))

        then: "the response is 400 Bad Request"
            result.andExpect(status().isBadRequest())

        and: "no login attempt"
            0 * mockAuthService.login(_ as LoginRequest)

        and: "the response body contains neither a password field, a password-hash field, nor the raw JWT string"
            result.andExpect(jsonPath('$.password').doesNotExist())
            result.andExpect(jsonPath('$.passwordHash').doesNotExist())
            result.andExpect(content().string(not(containsString(MOCK_JWT))))

        and: "validation errors are returned"
            result.andExpect(jsonPath('$.details', aMapWithSize(errorCount)))
            result.andExpect(jsonPath('$.details.' + errorContainsPath).exists())


        where:
            scenario                   | email             | password       | errorCount | errorContainsPath
            "email is null"            | null              | VALID_PASSWORD | 1          | "email"
            "email is empty"           | ""                | VALID_PASSWORD | 1          | "email"
            "email is blank"           | "     "           | VALID_PASSWORD | 1          | "email"
            "password is null"         | NEW_EMAIL_ADDRESS | null           | 1          | "password"
            "password is empty"        | NEW_EMAIL_ADDRESS | ""             | 1          | "password"
            "password is blank"        | NEW_EMAIL_ADDRESS | "       "      | 1          | "password"
            "empty email and password" | ""                | ""             | 2          | "password"
    }

    //---------------------------------
    // /api/auth/me tests
    //=================================

    def "AUTH-001-AC-14: returns the current user for a valid session"() {
        given: "a valid, unexpired access-token cookie for an existing user"
            UUID uuid = getRandomUuid()
            UserResponse validResponse = new UserResponse(uuid, NEW_EMAIL_ADDRESS, DISPLAY_NAME, ROLE_USER)
            mockAuthService.getUserDetailsById(uuid.toString()) >> validResponse

            String jwt = jwtHelper.buildJwt(uuid.toString(), ROLE_USER)
            mockJwtService.getClaim(jwt) >> new JwtClaim(uuid.toString(), ROLE_USER)

        when: "GET /api/auth/me is requested with that cookie"
            def result = mockMvc.perform(get(ENDPOINT_GET_CURRENT_USER)
                .cookie(new Cookie(COOKIE_ACCESS_TOKEN, jwt)))

        then: "the response is 200 OK with the user's id, email, displayName, and role"
            result.andExpect(status().isOk())
            String responseJson = result.andReturn().getResponse().getContentAsString()
            UserResponse response = objectMapper.readValue(responseJson, UserResponse)
            response.id() == uuid
            response.email() == NEW_EMAIL_ADDRESS
            response.displayName() == DISPLAY_NAME
            response.role() == ROLE_USER
    }

    def "AUTH-001-AC-15: rejects a missing, invalid, or expired session"() {
        given: "a request with either no cookie, a malformed cookie, or an expired/invalid-signature token"
            if (cookie != null) {
                mockJwtService.getClaim(_ as String) >> { throw new JwtParserException(exceptionMessage) }
            }

        when: "GET /api/auth/me is requested"
            def result
            if (cookie == null) {
                result = mockMvc.perform(get(ENDPOINT_GET_CURRENT_USER))
            } else {
                result = mockMvc.perform(get(ENDPOINT_GET_CURRENT_USER)
                    .cookie(accessTokenCookie(cookie)))
            }

        then: "the response is 401 Unauthorized"
            result.andExpect(status().isUnauthorized())

        where:
            scenario           | cookie           | exceptionMessage
            "no cookie"        | null             | "no cookie"
            "malformed cookie" | "bad cookie"     | "bad token"
            "expired cookie"   | "expired cookie" | "expired token"
    }

    //---------------------------------
    // /api/auth/logout tests
    //=================================

    def "AUTH-001-AC-16: logout clears the access-token cookie"() {
        when: "POST /api/auth/logout is requested"
            def result = mockMvc.perform(post(ENDPOINT_LOGOUT))

        then: "the response is 200 OK"
            result.andExpect(status().isOk())

        and: "the response clears the access-token cookie"
            result.andExpect(cookie().maxAge(COOKIE_ACCESS_TOKEN, 0))
    }

    def "AUTH-001-AC-17: the session is unusable immediately after logout"() {
        given: "a logged-in user who has just logged out"
            mockMvc.perform(post(ENDPOINT_LOGOUT))

        when: "GET /api/auth/me is requested using the cookie returned by the logout response"
            def result = mockMvc.perform(get(ENDPOINT_GET_CURRENT_USER))

        then: "the response is 401 Unauthorized"
            result.andExpect(status().isUnauthorized())
    }
}
