package guru.junaid.azadi.help;

import guru.junaid.azadi.common.Web;
import io.helidon.webserver.http.HttpRules;

import java.util.Map;

public final class HelpController {

    private static final Map<String, String> PAGES = Map.of(
        "/help/faqs", "help/faqs",
        "/help/ways-to-pay", "help/ways-to-pay",
        "/help/contact-us", "help/contact-us",
        "/cookies", "legal/cookies",
        "/privacy", "legal/privacy",
        "/terms", "legal/terms");

    private final Web web;

    public HelpController(Web web) {
        this.web = web;
    }

    public void routing(HttpRules rules) {
        PAGES.forEach((path, template) -> rules.get(path, (req, res) -> web.page(req, res, template, Map.of())));
    }
}
