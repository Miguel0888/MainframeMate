package de.bund.zrb.betaview.infrastructure;

/**
 * Simple value object for HTTP responses from BetaView.
 */
public final class HttpResponse {

    private final int statusCode;
    private final byte[] body;
    private final String location;

    public HttpResponse(int statusCode, byte[] body, String location) {
        this.statusCode = statusCode;
        this.body = body == null ? new byte[0] : body;
        this.location = location;
    }

    public int statusCode() {
        return statusCode;
    }

    public byte[] body() {
        return body;
    }

    public String location() {
        return location;
    }
}

