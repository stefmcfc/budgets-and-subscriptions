package uk.co.stefirby.budgetsandsubscriptions.controller

import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.ObjectMapper
import uk.co.stefirby.budgetsandsubscriptions.dto.JwtClaim
import uk.co.stefirby.budgetsandsubscriptions.exception.JwtParserException
import uk.co.stefirby.budgetsandsubscriptions.model.Role
import uk.co.stefirby.budgetsandsubscriptions.security.JwtService
import uk.co.stefirby.budgetsandsubscriptions.security.util.JwtHelper

import static uk.co.stefirby.budgetsandsubscriptions.security.util.CookieHelper.accessTokenCookie

class BaseControllerSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    @Shared
    JwtHelper jwtHelper = new JwtHelper()

    @SpringBean
    JwtService mockJwtService = Mock()

    @Autowired
    ObjectMapper objectMapper

    static class AuthenticatedUser {
        UUID id
        String jwt
    }

    AuthenticatedUser createAuthenticatedUser(JwtService mockJwtService) {
        return createAuthenticatedRole(mockJwtService, Role.USER)
    }

    AuthenticatedUser createAuthenticatedAdminUser(JwtService mockJwtService) {
        return createAuthenticatedRole(mockJwtService, Role.ADMIN)
    }

    AuthenticatedUser createAuthenticatedRole(JwtService mockJwtService, Role role) {
        UUID authenticatedUuid = UUID.randomUUID()
        String jwt = jwtHelper.buildJwt(authenticatedUuid.toString(), role.name())
        mockJwtService.getClaim(jwt) >> new JwtClaim(authenticatedUuid.toString(), role.name())
        return new AuthenticatedUser(id: authenticatedUuid, jwt: jwt)
    }

    ResultActions rejectUnauthenticatedRequest(MockHttpServletRequestBuilder requestBuilder, String cookie) {
        if (cookie != null) {
            mockJwtService.getClaim(_ as String) >> { throw new JwtParserException("Invalid Token") }
            requestBuilder = requestBuilder.cookie(accessTokenCookie(cookie))
        }
        return mockMvc.perform(requestBuilder)
    }

}
