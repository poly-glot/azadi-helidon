package guru.junaid.azadi.config;

import io.helidon.http.SetCookie;

public record Cookies(boolean secure) {

    public static final String SESSION = "__session";
    public static final String CSRF = "XSRF-TOKEN";

    public SetCookie session(String id) {
        return cookie(SESSION, id, true);
    }

    public SetCookie csrf(String token) {
        return cookie(CSRF, token, false);
    }

    private SetCookie cookie(String name, String value, boolean httpOnly) {
        return SetCookie.builder(name, value).path("/").httpOnly(httpOnly).secure(secure).sameSite(SetCookie.SameSite.STRICT).build();
    }
}
