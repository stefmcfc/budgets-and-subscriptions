package uk.co.stefirby.budgetsandsubscriptions.security.util

import jakarta.servlet.http.Cookie

import static uk.co.stefirby.budgetsandsubscriptions.common.Constants.COOKIE_ACCESS_TOKEN;

class CookieHelper {

    static Cookie[] accessTokenCookie(String value) {
        return [new Cookie(COOKIE_ACCESS_TOKEN, value)] as Cookie[]
    }
}
