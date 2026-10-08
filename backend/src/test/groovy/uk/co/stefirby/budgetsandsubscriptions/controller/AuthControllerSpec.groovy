package uk.co.stefirby.budgetsandsubscriptions.controller

import spock.lang.Specification

class AuthControllerSpec extends Specification {

    def "AUTH-001-AC-05: registers a new user with a valid request and logs them in"() {
        given: "a valid registration request (unused email, 12+ char password, non-blank displayName)"

        when: "POST /api/auth/register is requested"

        then: "the response is 201 Created with the new user's id, email, displayName, and role"

        and: "the persisted password is a BCrypt hash, not the plaintext value"

        and: "the response sets an httpOnly, Secure, SameSite=Strict access-token cookie expiring in 15 minutes"
    }

    def "AUTH-001-AC-06: rejects registration with a duplicate email (case-insensitive)"() {
        given: "an existing user with a given email"

        when: "POST /api/auth/register is requested with the same email in a different case"

        then: "the response is 409 Conflict"

        and: "no new user is created"
    }

    def "AUTH-001-AC-07: rejects registration with a too-short password"() {
        given: "a registration request with a password under 12 characters"

        when: "POST /api/auth/register is requested"

        then: "the response is 400 Bad Request"

        and: "no new user is created"
    }

    def "AUTH-001-AC-08: rejects registration with a malformed email or blank display name"() {
        given: "a registration request with either a malformed email or a blank displayName"

        when: "POST /api/auth/register is requested"

        then: "the response is 400 Bad Request"

        and: "no new user is created"
    }

    def "AUTH-001-AC-09: registration response never includes credentials or the raw token"() {
        given: "any registration request, successful or rejected"

        when: "POST /api/auth/register is requested"

        then: "the response body contains neither a password field, a password-hash field, nor the raw JWT string"
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
