package guru.junaid.azadi;

import guru.junaid.azadi.config.Cookies;
import guru.junaid.azadi.config.SecurityFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

final class Client {

    private static final String FORM_TYPE = "application/x-www-form-urlencoded";

    private final HttpClient http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final String baseUrl;
    private final String forwardedFor;

    Client(String baseUrl, String forwardedFor) {
        this.baseUrl = baseUrl;
        this.forwardedFor = forwardedFor;
    }

    HttpResponse<String> get(String path) {
        return send(request(path).GET());
    }

    HttpResponse<String> get(String path, String headerName, String headerValue) {
        return send(request(path).header(headerName, headerValue).GET());
    }

    HttpResponse<String> postForm(String path, Map<String, String> fields) {
        var withToken = new LinkedHashMap<>(fields);
        withToken.put("_csrf", cookies.getOrDefault(Cookies.CSRF, ""));
        return postForm(path, encode(withToken), Map.of());
    }

    HttpResponse<String> postForm(String path, String body, Map<String, String> headers) {
        return send(withHeaders(request(path).header("Content-Type", FORM_TYPE), headers)
            .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    HttpResponse<String> postJson(String path, String body) {
        return postJson(path, body, Map.of(SecurityFilter.XSRF_HEADER.defaultCase(), cookies.getOrDefault(Cookies.CSRF, "")));
    }

    HttpResponse<String> postJson(String path, String body, Map<String, String> headers) {
        return send(withHeaders(request(path).header("Content-Type", "application/json"), headers)
            .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    String cookie(String name) {
        return cookies.get(name);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("X-Forwarded-For", forwardedFor)
            .header("Cookie", cookieHeader());
    }

    private static HttpRequest.Builder withHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        headers.forEach(builder::header);
        return builder;
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            response.headers().allValues("set-cookie").forEach(this::remember);
            return response;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void remember(String setCookie) {
        var pair = setCookie.split(";", 2)[0];
        var eq = pair.indexOf('=');
        if (eq > 0) {
            cookies.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
    }

    private String cookieHeader() {
        return cookies.entrySet().stream()
            .filter(cookie -> !cookie.getValue().isEmpty())
            .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
            .collect(Collectors.joining("; "));
    }

    private static String encode(Map<String, String> fields) {
        return fields.entrySet().stream()
            .map(field -> encode(field.getKey()) + "=" + encode(field.getValue()))
            .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String csrfToken(String html) {
        return Arrays.stream(html.split("\n"))
            .filter(line -> line.contains("name=\"_csrf\""))
            .map(Client::valueAttribute)
            .findFirst()
            .orElse("");
    }

    private static String valueAttribute(String tag) {
        var start = tag.indexOf("value=\"") + "value=\"".length();
        return tag.substring(start, tag.indexOf('"', start));
    }
}
