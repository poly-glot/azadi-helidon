package guru.junaid.azadi.common;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import guru.junaid.azadi.auth.Session;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class Web {

    private static final Gson GSON = new Gson();
    private static final String VITE_MANIFEST = "/static/.vite/manifest.json";
    private static final Type MANIFEST_TYPE = new TypeToken<Map<String, Map<String, Object>>>() { }.getType();

    private final Views views;
    private final Map<String, String> assets;

    public Web(Views views) {
        this.views = views;
        this.assets = viteAssets();
    }

    public void page(ServerRequest req, ServerResponse res, String template, Map<String, Object> extra) {
        var session = session(req);
        var model = new HashMap<String, Object>();
        model.put("appVersion", "helidon");
        model.put("requestURI", req.path().path());
        model.put("viteDevUrl", null);
        model.put("viteAssets", assets);
        model.put("cspNonce", req.context().get("nonce", String.class).orElse(""));
        model.put("csrf", req.context().get("csrf", String.class).orElse(""));
        model.put("ordinal", Ordinal.INSTANCE);
        model.put("authenticated", session.isPresent());
        model.put("customerName", session.map(Session::name).orElse("Customer"));
        session.ifPresent(s -> model.putAll(s.takeFlash()));
        model.putAll(extra);

        res.header(HeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        res.send(views.render(template, req.path().path(), req.query().toMap(), model));
    }

    public void flashRedirect(ServerRequest req, ServerResponse res, String location, String key, Object value) {
        session(req).ifPresent(s -> s.flash(key, value));
        redirect(res, location);
    }

    public static Optional<Session> session(ServerRequest req) {
        return req.context().get(Session.class);
    }

    public static String customerId(ServerRequest req) {
        return session(req).orElseThrow(() -> new IllegalStateException("No authenticated user found.")).customerId();
    }

    public static String sessionId(ServerRequest req) {
        return session(req).map(Session::id).orElse("");
    }

    public static String body(ServerRequest req) {
        return req.context().get("body", String.class).orElse("");
    }

    public static Map<String, String> form(ServerRequest req) {
        return parseForm(body(req));
    }

    public static Map<String, String> parseForm(String body) {
        var fields = new HashMap<String, String>();
        if (body == null || body.isEmpty()) {
            return fields;
        }
        UriQuery.create(body).toMap().forEach((name, values) -> fields.put(name, values.isEmpty() ? "" : values.getLast()));
        return fields;
    }

    public static String ip(ServerRequest req) {
        return req.context().get("ip", String.class).orElse("");
    }

    public static void redirect(ServerResponse res, String location) {
        res.status(Status.FOUND_302).header(HeaderNames.LOCATION, location).send();
    }

    public static void json(ServerResponse res, Status status, String body) {
        res.status(status).header(HeaderNames.CONTENT_TYPE, "application/json").send(body);
    }

    private static Map<String, String> viteAssets() {
        var manifest = Web.class.getResource(VITE_MANIFEST);
        if (manifest == null) {
            throw new IllegalStateException("No " + VITE_MANIFEST + " on the classpath: run cd frontend && npm run build");
        }
        try (var in = manifest.openStream()) {
            Map<String, Map<String, Object>> entries = GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), MANIFEST_TYPE);
            return entries.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> "/" + e.getValue().get("file")));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + VITE_MANIFEST, e);
        }
    }
}
