package de.bund.zrb.betaview;

final class HttpResponse {

    private final int statusCode;
    private final byte[] body;
    private final String location;
    private final String contentType;

    public HttpResponse(int statusCode, byte[] body, String location) {
        this(statusCode, body, location, null);
    }

    public HttpResponse(int statusCode, byte[] body, String location, String contentType) {
        this.statusCode = statusCode;
        this.body = body == null ? new byte[0] : body;
        this.location = location;
        this.contentType = contentType;
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

    public String contentType() {
        return contentType;
    }
}