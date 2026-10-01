package guru.junaid.azadi.config;

import guru.junaid.azadi.auth.Session;
import guru.junaid.azadi.auth.Sessions;
import guru.junaid.azadi.common.Web;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

public final class SecurityFilter implements Filter {

    public static final HeaderName XSRF_HEADER = HeaderNames.create("X-XSRF-TOKEN");
    private static final HeaderName CSRF_HEADER = HeaderNames.create("X-CSRF-TOKEN");
    private static final HeaderName FORWARDED_FOR = HeaderNames.create("X-Forwarded-For");
    private static final Set<String> PUBLIC = Set.of("/", "/login", "/login-error", "/cookies", "/privacy", "/terms",
        "/actuator/health", "/api/stripe/webhook", "/api/csp-report");
    private static final Set<String> CSRF_EXEMPT = Set.of("/api/stripe/webhook", "/api/csp-report");
    private static final String ASSETS_PREFIX = "/assets/";
    private static final int NONCE_LENGTH = 22;
    private static final int LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final int PAYMENT_ATTEMPTS = 3;
    private static final Duration PAYMENT_WINDOW = Duration.ofHours(1);
    private static final Duration GENERAL_WINDOW = Duration.ofMinutes(1);

    private final Sessions sessions;
    private final Cookies cookies;
    private final RateLimiter limiter;
    private final int generalLimit;

    public SecurityFilter(Sessions sessions, Cookies cookies, RateLimiter limiter, int generalLimit) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.limiter = limiter;
        this.generalLimit = generalLimit;
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        var path = req.path().path();
        var method = req.prologue().method();
        var ip = clientIp(req);
        req.context().register("ip", ip);

        var nonce = Sessions.token().substring(0, NONCE_LENGTH);
        req.context().register("nonce", nonce);
        securityHeaders(res, nonce);

        var jar = req.headers().cookies();
        var session = sessions.get(jar.first(Cookies.SESSION).orElse(null));
        session.ifPresent(s -> req.context().register(s));

        if (rateLimited(path, method, ip, session)) {
            res.status(Status.TOO_MANY_REQUESTS_429).send("Too many requests. Please try again later.");
            return;
        }

        var csrf = jar.first(Cookies.CSRF).orElseGet(() -> issueCsrf(res));
        req.context().register("csrf", csrf);

        if (readOnly(method)) {
            proceedIfAllowed(chain, res, session, path);
            return;
        }

        var body = req.content().hasEntity() ? req.content().as(String.class) : "";
        req.context().register("body", body);
        if (!CSRF_EXEMPT.contains(path) && !csrfMatches(req, body, csrf)) {
            res.status(Status.FORBIDDEN_403).send("Forbidden");
            return;
        }
        proceedIfAllowed(chain, res, session, path);
    }

    private static void proceedIfAllowed(FilterChain chain, RoutingResponse res, Optional<Session> session, String path) {
        if (session.isEmpty() && !PUBLIC.contains(path) && !path.startsWith(ASSETS_PREFIX)) {
            Web.redirect(res, "/login");
            return;
        }
        chain.proceed();
    }

    private boolean rateLimited(String path, Method method, String ip, Optional<Session> session) {
        var caller = session.map(Session::id).orElse(ip);
        if (Method.POST.equals(method)) {
            if ("/login".equals(path) && limiter.limited("login:" + ip, LOGIN_ATTEMPTS, LOGIN_WINDOW)) {
                return true;
            }
            if (path.contains("/make-a-payment") && limiter.limited("pay:" + caller, PAYMENT_ATTEMPTS, PAYMENT_WINDOW)) {
                return true;
            }
        }
        return limiter.limited("gen:" + caller, generalLimit, GENERAL_WINDOW);
    }

    private String issueCsrf(RoutingResponse res) {
        var token = Sessions.token();
        res.headers().addCookie(cookies.csrf(token));
        return token;
    }

    private static boolean readOnly(Method method) {
        return Method.GET.equals(method) || Method.HEAD.equals(method);
    }

    private static boolean csrfMatches(RoutingRequest req, String body, String expected) {
        var submitted = req.headers().value(XSRF_HEADER)
            .or(() -> req.headers().value(CSRF_HEADER))
            .orElseGet(() -> Web.parseForm(body).get("_csrf"));
        return submitted != null
            && MessageDigest.isEqual(submitted.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static void securityHeaders(RoutingResponse res, String nonce) {
        res.headers().set(HeaderNames.create("Content-Security-Policy"), String.join("",
            "default-src 'self'; ",
            "script-src 'self' 'nonce-", nonce, "' https://js.stripe.com; ",
            "style-src 'self' 'nonce-", nonce, "' https://fonts.googleapis.com; ",
            "font-src 'self' https://fonts.gstatic.com; ",
            "frame-src https://js.stripe.com; ",
            "img-src 'self' data:; ",
            "connect-src 'self' https://api.stripe.com; ",
            "base-uri 'self'; form-action 'self'; report-uri /api/csp-report;"));
        res.headers().set(HeaderNames.create("Strict-Transport-Security"), "max-age=63072000; includeSubDomains");
        res.headers().set(HeaderNames.create("X-Content-Type-Options"), "nosniff");
        res.headers().set(HeaderNames.create("X-Frame-Options"), "DENY");
        res.headers().set(HeaderNames.create("Referrer-Policy"), "strict-origin-when-cross-origin");
        res.headers().set(HeaderNames.create("Permissions-Policy"), "camera=(), microphone=(), geolocation=()");
    }

    private static String clientIp(RoutingRequest req) {
        return req.headers().value(FORWARDED_FOR).map(v -> v.split(",")[0].trim()).orElseGet(() -> req.remotePeer().host());
    }
}
