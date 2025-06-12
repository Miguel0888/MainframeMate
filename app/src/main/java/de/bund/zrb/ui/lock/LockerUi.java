package de.bund.zrb.ui.lock;

import de.bund.zrb.login.LoginCredentials;

import java.awt.*;
import java.util.function.Predicate;

public interface LockerUi {
    /**
     * Asks for HOST & USER
     *
     * @return new Credential Object
     */
    LoginCredentials init();

    /**
     * Just asks for the password
     *
     * @param loginCredentials
     *
     * @return refreshed loginCredentials
     */
    LoginCredentials logOn(LoginCredentials loginCredentials);

    /**
     * Locks the screen and validates input via the given predicate
     * @param passwordValidator, determines whether login is a success
     */
    void lock(Predicate<char[]> passwordValidator);


}
