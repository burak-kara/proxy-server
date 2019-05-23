import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class ProxyServer {

    private HttpServer server;

    public ProxyServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", new ProxyHandler());
        server.setExecutor(null);
        server.start();
    }

}
