package uk.co.stefirby.budgetsandsubscriptions.model.util

import uk.co.stefirby.budgetsandsubscriptions.model.User
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository

import static uk.co.stefirby.budgetsandsubscriptions.model.util.UserConstants.*

class UserEntityHelper {

    static UUID createUser(UserRepository userRepository) {
        User user = new User(USER_EMAIL_VALID, USER_PASSWORD, USER_DISPLAY_NAME)
        userRepository.saveAndFlush(user)
        Optional<User> newUser = userRepository.findByEmail(USER_EMAIL_VALID)
        return newUser.get().id
    }

}
