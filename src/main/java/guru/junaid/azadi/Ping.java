package guru.junaid.azadi;

import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;

import java.util.logging.Logger;

public final class Ping {

    private static final Logger LOG = Logger.getLogger(Ping.class.getName());

    private Ping() { }

    public static void main(String[] args) {
        LogConfig.configureRuntime();
        var server = WebServer.builder().port(8080).routing(r -> r.get("/ping", (q, s) -> s.send("pong"))).build().start();
        LOG.info(() -> "started on port " + server.port());
    }
}
