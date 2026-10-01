package guru.junaid.azadi.auth;

import guru.junaid.azadi.common.Web;
import guru.junaid.azadi.config.Cookies;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.Map;

public final class AuthController {

    private final Web web;
    private final AuthService auth;
    private final Sessions sessions;
    private final Cookies cookies;
    private final boolean demoMode;

    public AuthController(Web web, AuthService auth, Sessions sessions, Cookies cookies, boolean demoMode) {
        this.web = web;
        this.auth = auth;
        this.sessions = sessions;
        this.cookies = cookies;
        this.demoMode = demoMode;
    }

    public void routing(HttpRules rules) {
        rules.get("/", (req, res) -> Web.redirect(res, "/login"))
            .get("/login", (req, res) -> web.page(req, res, "login", Map.of("demoMode", demoMode)))
            .get("/login-error", (req, res) -> web.page(req, res, "login", Map.of("demoMode", demoMode, "error", true)))
            .post("/login", this::login)
            .post("/logout", this::logout);
    }

    private void login(ServerRequest req, ServerResponse res) {
        var form = Web.form(req);
        auth.login(form.get("username"), form.get("password")).ifPresentOrElse(session -> {
            res.headers().addCookie(cookies.session(session.id())).addCookie(cookies.csrf(Sessions.token()));
            Web.redirect(res, "/my-account");
        }, () -> Web.redirect(res, "/login-error"));
    }

    private void logout(ServerRequest req, ServerResponse res) {
        Web.session(req).ifPresent(s -> sessions.invalidate(s.id()));
        res.headers().clearCookie(cookies.session(""));
        Web.redirect(res, "/login");
    }
}
