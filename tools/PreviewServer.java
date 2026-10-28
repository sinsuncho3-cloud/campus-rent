import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** Local static preview only. Does not execute Thymeleaf, Spring Boot or database code. */
public class PreviewServer {
  private static final Map<String,String> TYPES = new HashMap<String,String>();
  static {
    TYPES.put("html","text/html; charset=utf-8"); TYPES.put("css","text/css; charset=utf-8");
    TYPES.put("js","text/javascript; charset=utf-8"); TYPES.put("svg","image/svg+xml");
    TYPES.put("ttf","font/ttf"); TYPES.put("png","image/png");
  }
  public static void main(String[] args) throws Exception {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
    final Path root = Paths.get(args.length > 1 ? args[1] : ".").toRealPath();
    final Path resources = root.resolve("src/main/resources");
    final Path staticRoot = resources.resolve("static").toRealPath();
    final Set<String> pages = new HashSet<String>(Arrays.asList("/", "/equipment", "/facilities", "/purpose", "/records", "/history", "/admin"));
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
    server.createContext("/", exchange -> {
      try {
        String url = exchange.getRequestURI().getPath();
        Path file;
        if (pages.contains(url)) file = resources.resolve("templates/index.html");
        else if (url.equals("/__checks")) file = root.resolve("tools/browser-check.html");
        else {
          file = staticRoot.resolve(url.substring(1)).normalize();
          if (!file.startsWith(staticRoot) || !Files.isRegularFile(file) || !file.toRealPath().startsWith(staticRoot)) {
            exchange.sendResponseHeaders(404,-1); return;
          }
        }
        if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
          exchange.sendResponseHeaders(405,-1); return;
        }
        byte[] data = Files.readAllBytes(file);
        String name = file.getFileName().toString();
        exchange.getResponseHeaders().set("Content-Type",TYPES.getOrDefault(name.substring(name.lastIndexOf('.')+1),"application/octet-stream"));
        exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        if (exchange.getRequestMethod().equals("HEAD")) exchange.sendResponseHeaders(200,-1);
        else { exchange.sendResponseHeaders(200,data.length); exchange.getResponseBody().write(data); }
      } catch(Exception error) {
        try { exchange.sendResponseHeaders(500,-1); } catch(Exception ignored) {}
      } finally { exchange.close(); }
    });
    server.start();
    System.out.println("CampusRent preview: http://127.0.0.1:"+port+"/ (Ctrl+C to stop)");
  }
}
