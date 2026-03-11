package com.acme.betaview;

import java.net.URL;

public final class BetaViewActionPathResolver {

    public String toRelativeAction(URL clickedUrl) {
        String path = clickedUrl.getPath();

        int betaviewIdx = path.lastIndexOf("/betaview/");
        if (betaviewIdx >= 0) {
            path = path.substring(betaviewIdx + "/betaview/".length());
        } else if (path.startsWith("/")) {
            path = path.substring(1);
        }

        String query = clickedUrl.getQuery();
        return query == null || query.trim().isEmpty() ? path : path + "?" + query;
    }
}