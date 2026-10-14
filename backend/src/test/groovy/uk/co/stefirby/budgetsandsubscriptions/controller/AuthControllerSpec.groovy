package uk.co.stefirby.budgetsandsubscriptions.controller

import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.databind.ObjectMapper
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterResponse
import uk.co.stefirby.budgetsandsubscriptions.exception.DuplicateEmailException
import uk.co.stefirby.budgetsandsubscriptions.security.JwtBuilder
import uk.co.stefirby.budgetsandsubscriptions.security.SecurityConfig
import uk.co.stefirby.budgetsandsubscriptions.service.AuthService

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(controllers = AuthController)
@Import(SecurityConfig)
class AuthControllerSpec extends Specification {

    private static final String ENDPOINT_REGISTER = "/api/auth/register"

    private static final String NEW_EMAIL_ADDRESS = "new.user@test.com"
    private static final String VALID_PASSWORD = "validPassword"
    private static final String DISPLAY_NAME = "Test"
    private static final String ACCESS_TOKEN = "access_token"
    private static final String MOCK_JWT = "mock.jwt"

    @Autowired
    MockMvc mockMvc

    @Autowired
    ObjectMapper objectMapper

    @SpringBean
    AuthService mockAuthService = Mock()

    @SpringBean
    JwtBuilder mockJwtBuilder = Mock()

    private void setupMockForSuccessfulRegistration(RegisterRequest registerRequest, UUID uuid) {
        def validResponse = new RegisterResponse(uuid, registerRequest.email(), registerRequest.displayName(), "USER")
        mockAuthService.register(registerRequest) >> validResponse
        mockJwtBuilder.createJWT(_ as String, _ as String) >> MOCK_JWT
    }

    private static UUID getRandomUuid() {
        return UUID.randomUUID()
    }

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
        RegisterResponse response = objectMapper.readValue(responseJson, RegisterResponse)
        response.id == uuid
        response.email == NEW_EMAIL_ADDRESS
        response.displayName == DISPLAY_NAME
        response.role == "USER"

        and: "the response sets an httpOnly, Secure, SameSite=Strict access-token cookie expiring in 15 minutes"
        result.andExpect(cookie().httpOnly(ACCESS_TOKEN, true))
        result.andExpect(cookie().secure(ACCESS_TOKEN, true))
        result.andExpect(cookie().maxAge(ACCESS_TOKEN, 15 * 60))
        result.andExpect(cookie().sameSite(ACCESS_TOKEN, "Strict"))

        and: "(AUTH-001-AC-09) the response body contains neither a password field, a password-hash field, nor the raw JWT string"
        result.andExpect(jsonPath('$.password').doesNotExist())
        result.andExpect(jsonPath('$.passwordHash').doesNotExist())
        result.andExpect(content().string(not(containsString(MOCK_JWT))))

    }

    def "AUTH-001-AC-06: rejects registration with a duplicate email (case-insensitive)"() {
        given: "an existing user with a given email"
        mockAuthService.register(_ as RegisterRequest) >> { throw new DuplicateEmailException("dummy message") }

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

        where:
        scenario                          | password
        "null"                            | null
        "empty"                           | ""
        "blank and too short"             | "   "
        "blank but 12 or more characters" | "              "
        "too short (boundary case)"       | "1234567890A"
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

    def "AUTH-001-AC-10: successful login issues an access-token cookie"() {
        given: "an existing user with a known email and password"

        when: "POST /api/auth/login is requested with matching credentials"

        then: "the response is 200 OK with the user's id, email, displayName, and role"

        and: "the response sets an httpOnly, Secure, SameSite=Strict access-token cookie expiring in 15 minutes"
    }

    def "AUTH-001-AC-11: rejects login with a non-existent email or a wrong password"() {
        given: "either no user with the given email, or a user with a non-matching password"

        when: "POST /api/auth/login is requested"

        then: "the response is 401 Unauthorized with a generic message"

        and: "no access-token cookie is set"
    }

    def "AUTH-001-AC-12: access token claims are limited to id and role"() {
        given: "an existing user"

        when: "POST /api/auth/login is requested with matching credentials"

        then: "the issued JWT's claims contain only the user's id and role"
    }

    def "AUTH-001-AC-13: login response never includes credentials or the raw token"() {
        given: "a successful login"

        when: "POST /api/auth/login is requested"

        then: "the response body contains neither the password hash nor the raw JWT string"
    }

    def "AUTH-001-AC-14: returns the current user for a valid session"() {
        given: "a valid, unexpired access-token cookie for an existing user"

        when: "GET /api/auth/me is requested with that cookie"

        then: "the response is 200 OK with the user's id, email, displayName, and role"
    }

    def "AUTH-001-AC-15: rejects a missing, invalid, or expired session"() {
        given: "a request with either no cookie, a malformed cookie, or an expired/invalid-signature token"

        when: "GET /api/auth/me is requested"

        then: "the response is 401 Unauthorized"
    }

    def "AUTH-001-AC-16: logout clears the access-token cookie"() {
        given: "a logged-in user with a valid access-token cookie"

        when: "POST /api/auth/logout is requested"

        then: "the response is 200 OK"

        and: "the response clears the access-token cookie"
    }

    def "AUTH-001-AC-17: the session is unusable immediately after logout"() {
        given: "a logged-in user who has just logged out"

        when: "GET /api/auth/me is requested using the cookie returned by the logout response"

        then: "the response is 401 Unauthorized"
    }
}
