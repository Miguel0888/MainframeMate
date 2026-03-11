package de.bund.zrb.betaview;

import java.net.CookieManager;
import java.net.HttpCookie;

final class CookieHeaderBuilder {

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
