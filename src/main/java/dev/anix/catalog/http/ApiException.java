package dev.anix.catalog.http;

/** An error that maps directly to an HTTP status and a machine-readable error code. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public static ApiException badRequest(String message) { return new ApiException(400, "invalid_request", message); }
    public static ApiException unauthorized() { return new ApiException(401, "unauthorized", "missing or invalid bearer token"); }
    public static ApiException notFound(String what) { return new ApiException(404, "not_found", what + " not found"); }
    public static ApiException conflict(String message) { return new ApiException(409, "conflict", message); }
}
