package de.bund.zrb.betaview.infrastructure;

import java.net.CookieManager;
import java.net.HttpCookie;

/**
 * Builds cookie header string from a CookieManager.
 */
public final class CookieHeaderBuilder {

    private CookieHeaderBuilder() {
    }

    public static String build(CookieManager cookieManager) {
        StringBuilder sb = new StringBuilder();
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(cookie.getName()).append("=").append(cookie.getValue());
        }
        return sb.toString();
    }
}

