package guru.junaid.azadi.common;

import com.stripe.exception.StripeException;
import io.helidon.http.HttpException;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ErrorHandler {

    private static final Logger LOG = Logger.getLogger(ErrorHandler.class.getName());
    private static final Failure UNEXPECTED = new Failure(Status.INTERNAL_SERVER_ERROR_500, "Something went wrong",
        "An unexpected error has occurred. Please try again later.");
    private static final Failure NOT_FOUND = new Failure(Status.NOT_FOUND_404, "Not Found",
        "The page or resource you requested could not be found.");

    private record Failure(Status status, String title, String message) { }

    private final Web web;

    public ErrorHandler(Web web) {
        this.web = web;
    }

    public void handle(ServerRequest req, ServerResponse res, Throwable t) {
        if (t instanceof HttpException he && he.status().code() == Status.NOT_MODIFIED_304.code()) {
            res.status(he.status()).send();
            return;
        }
        var failure = failure(t);
        if (failure.status().code() >= Status.INTERNAL_SERVER_ERROR_500.code()) {
            LOG.log(Level.WARNING, t, () -> "Request failed: " + req.path().path());
        }
        if (Method.POST.equals(req.prologue().method()) && req.path().path().contains("/make-a-payment")) {
            Web.json(res, failure.status(), "{\"error\":\"Payment could not be started. Please try again.\"}");
            return;
        }
        res.status(failure.status());
        web.page(req, res, "error", Map.of("status", failure.status().code(), "error", failure.title(), "message", failure.message()));
    }

    private static Failure failure(Throwable t) {
        return switch (t) {
            case SecurityException e -> new Failure(Status.FORBIDDEN_403, "Access Denied",
                "You do not have permission to access this resource.");
            case NoSuchElementException e -> NOT_FOUND;
            case HttpException e when e.status().code() == Status.NOT_FOUND_404.code() -> NOT_FOUND;
            case IllegalArgumentException e -> new Failure(Status.BAD_REQUEST_400, "Invalid Request",
                "The request contained invalid data.");
            case IllegalStateException e -> new Failure(Status.CONFLICT_409, "Action Not Allowed",
                "This action cannot be performed at this time.");
            case StripeException e -> new Failure(Status.BAD_GATEWAY_502, "Payment Service Unavailable",
                "Payment service unavailable. Please try again.");
            default -> UNEXPECTED;
        };
    }
}
