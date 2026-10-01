package guru.junaid.azadi.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CookiesTest {

    @Test
    @DisplayName("The session cookie is HttpOnly, same site strict and secure when configured")
    void hardensTheSessionCookie() {
        assertThat(new Cookies(true).session("abc").toString())
            .startsWith("__session=abc")
            .contains("Path=/").contains("HttpOnly").contains("Secure").contains("SameSite=Strict");
    }

    @Test
    @DisplayName("The CSRF cookie is readable by the page script and not secure on plain HTTP")
    void exposesTheCsrfCookie() {
        assertThat(new Cookies(false).csrf("tok").toString())
            .startsWith("XSRF-TOKEN=tok")
            .doesNotContain("HttpOnly").doesNotContain("Secure").contains("SameSite=Strict");
    }
}
