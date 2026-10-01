package guru.junaid.azadi.common;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.IWebApplication;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.IWebRequest;
import org.thymeleaf.web.IWebSession;

import java.io.InputStream;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Views {

    private static final int DEFAULT_PORT = 8080;
    private static final String[] NO_VALUES = new String[0];

    private final TemplateEngine engine = new TemplateEngine();

    public Views(boolean cache) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(cache);
        engine.setTemplateResolver(resolver);
        engine.addDialect(new LayoutDialect());
    }

    public String render(String template, String path, Map<String, List<String>> params, Map<String, Object> model) {
        return engine.process(template, new WebContext(new Exchange(path, params), Locale.UK, model));
    }

    private static final class Exchange implements IWebExchange {
        private final String path;
        private final Map<String, List<String>> params;
        private final Map<String, Object> attrs = new HashMap<>();

        Exchange(String path, Map<String, List<String>> params) {
            this.path = path;
            this.params = params;
        }

        @Override public IWebRequest getRequest() { return new Request(path, params); }
        @Override public IWebSession getSession() { return new Session(); }
        @Override public IWebApplication getApplication() { return new App(); }
        @Override public Principal getPrincipal() { return null; }
        @Override public Locale getLocale() { return Locale.UK; }
        @Override public String getContentType() { return "text/html"; }
        @Override public String getCharacterEncoding() { return "UTF-8"; }
        @Override public boolean containsAttribute(String n) { return attrs.containsKey(n); }
        @Override public int getAttributeCount() { return attrs.size(); }
        @Override public Set<String> getAllAttributeNames() { return attrs.keySet(); }
        @Override public Map<String, Object> getAttributeMap() { return attrs; }
        @Override public Object getAttributeValue(String n) { return attrs.get(n); }
        @Override public void setAttributeValue(String n, Object v) { attrs.put(n, v); }
        @Override public void removeAttribute(String n) { attrs.remove(n); }
        @Override public String transformURL(String url) { return url; }
    }

    private record Request(String path, Map<String, List<String>> params) implements IWebRequest {
        @Override public String getMethod() { return "GET"; }
        @Override public String getScheme() { return "http"; }
        @Override public String getServerName() { return "localhost"; }
        @Override public Integer getServerPort() { return DEFAULT_PORT; }
        @Override public String getApplicationPath() { return ""; }
        @Override public String getPathWithinApplication() { return path; }
        @Override public String getQueryString() { return null; }
        @Override public boolean containsHeader(String n) { return false; }
        @Override public int getHeaderCount() { return 0; }
        @Override public Set<String> getAllHeaderNames() { return Set.of(); }
        @Override public Map<String, String[]> getHeaderMap() { return Map.of(); }
        @Override public String[] getHeaderValues(String n) { return NO_VALUES; }
        @Override public boolean containsParameter(String n) { return params.containsKey(n); }
        @Override public int getParameterCount() { return params.size(); }
        @Override public Set<String> getAllParameterNames() { return params.keySet(); }
        @Override public Map<String, String[]> getParameterMap() {
            var m = new HashMap<String, String[]>();
            params.forEach((k, v) -> m.put(k, v.toArray(String[]::new)));
            return m;
        }
        @Override public String[] getParameterValues(String n) {
            return params.containsKey(n) ? params.get(n).toArray(String[]::new) : null;
        }
        @Override public boolean containsCookie(String n) { return false; }
        @Override public int getCookieCount() { return 0; }
        @Override public Set<String> getAllCookieNames() { return Set.of(); }
        @Override public Map<String, String[]> getCookieMap() { return Map.of(); }
        @Override public String[] getCookieValues(String n) { return NO_VALUES; }
    }

    private record Session() implements IWebSession {
        @Override public boolean exists() { return false; }
        @Override public boolean containsAttribute(String n) { return false; }
        @Override public int getAttributeCount() { return 0; }
        @Override public Set<String> getAllAttributeNames() { return Set.of(); }
        @Override public Map<String, Object> getAttributeMap() { return Collections.emptyMap(); }
        @Override public Object getAttributeValue(String n) { return null; }
        @Override public void setAttributeValue(String n, Object v) { }
        @Override public void removeAttribute(String n) { }
    }

    private record App() implements IWebApplication {
        @Override public boolean containsAttribute(String n) { return false; }
        @Override public int getAttributeCount() { return 0; }
        @Override public Set<String> getAllAttributeNames() { return Set.of(); }
        @Override public Map<String, Object> getAttributeMap() { return Collections.emptyMap(); }
        @Override public Object getAttributeValue(String n) { return null; }
        @Override public void setAttributeValue(String n, Object v) { }
        @Override public void removeAttribute(String n) { }
        @Override public boolean resourceExists(String p) { return false; }
        @Override public InputStream getResourceAsStream(String p) { return null; }
    }
}
