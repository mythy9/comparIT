package grocery;

import com.sun.net.httpserver.*;
import grocery.model.*;
import grocery.service.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    static final Path WEB = Path.of("web").toAbsolutePath().normalize();
    static final HttpClient IMAGE_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NORMAL).build();
    static final ConcurrentMap<String, String[]> IMAGE_CACHE = new ConcurrentHashMap<>();
    static final Pattern BING_RESULT = Pattern.compile("class=\"iusc\"[^>]*?m=\"([^\"]+)\"");
    static final Pattern BING_THUMBNAIL = Pattern.compile("\"turl\":\"(https?://[^\"\\\\]+)");
    static final Pattern BING_SOURCE = Pattern.compile("\"purl\":\"(https?://[^\"\\\\]+)");
    static final Pattern BING_TITLE = Pattern.compile("\"t\":\"([^\"]+)");

    public static void main(String[] args) throws Exception {
        ProductRepository repo = new ProductRepository(Path.of("data/products.csv"));
        PriceProvider provider = new MockPriceProvider(Platform.defaults());
        MySQLService mysql = new MySQLService();
        mysql.init(repo.getAll());

        int port = Integer.getInteger("server.port", 8080);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/status", ex -> {
            send(ex, 200, "application/json",
                    "{\"mysqlConnected\":" + mysql.isConnected() + ",\"database\":\"comparit_db\",\"host\":\"localhost:3306\"}");
        });

        server.createContext("/api/cart", ex -> {
            String method = ex.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                Map<String, Integer> cart = mysql.getCart();
                StringBuilder sb = new StringBuilder("{");
                for (Map.Entry<String, Integer> e : cart.entrySet()) {
                    sb.append(q(e.getKey())).append(':').append(e.getValue()).append(',');
                }
                if (sb.length() > 1) sb.setLength(sb.length() - 1);
                sb.append('}');
                send(ex, 200, "application/json", "{\"cart\":" + sb.toString() + ",\"connected\":" + mysql.isConnected() + "}");
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(ex);
                String name = param(ex, "name");
                int qty = 0;
                try { qty = Integer.parseInt(param(ex, "qty")); } catch (Exception ignored) {}
                if (name.isEmpty() && !body.isEmpty()) {
                    name = extractJsonString(body, "name");
                    qty = extractJsonInt(body, "qty");
                }
                final String targetName = name;
                Product p = repo.search(targetName).stream().filter(x -> x.getName().equalsIgnoreCase(targetName)).findFirst().orElse(null);
                mysql.updateCartItem(targetName, qty, p);
                send(ex, 200, "application/json", "{\"status\":\"ok\",\"qty\":" + qty + "}");
            } else if ("DELETE".equalsIgnoreCase(method)) {
                mysql.clearCart();
                send(ex, 200, "application/json", "{\"status\":\"cleared\"}");
            } else {
                send(ex, 405, "text/plain", "Method Not Allowed");
            }
        });

        server.createContext("/api/favourites", ex -> {
            String method = ex.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                Set<String> favs = mysql.getFavourites();
                StringBuilder sb = new StringBuilder("[");
                for (String f : favs) sb.append(q(f)).append(',');
                if (sb.length() > 1) sb.setLength(sb.length() - 1);
                sb.append(']');
                send(ex, 200, "application/json", sb.toString());
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(ex);
                String name = param(ex, "name");
                if (name.isEmpty() && !body.isEmpty()) name = extractJsonString(body, "name");
                boolean added = mysql.toggleFavourite(name);
                send(ex, 200, "application/json", "{\"status\":\"ok\",\"added\":" + added + "}");
            } else {
                send(ex, 405, "text/plain", "Method Not Allowed");
            }
        });

        server.createContext("/api/recent", ex -> {
            List<String> recent = mysql.getRecentSearches();
            StringBuilder sb = new StringBuilder("[");
            for (String r : recent) sb.append(q(r)).append(',');
            if (sb.length() > 1) sb.setLength(sb.length() - 1);
            sb.append(']');
            send(ex, 200, "application/json", sb.toString());
        });

        server.createContext("/api/categories", ex -> {
            StringBuilder sb = new StringBuilder("[");
            for (String c : repo.categories()) sb.append(q(c)).append(',');
            if (sb.length() > 1) sb.setLength(sb.length() - 1);
            send(ex, 200, "application/json", sb.append(']').toString());
        });

        server.createContext("/api/suggest", ex -> {
            StringBuilder sb = new StringBuilder("[");
            for (String n : repo.suggest(param(ex, "q"), 8)) sb.append(q(n)).append(',');
            if (sb.length() > 1) sb.setLength(sb.length() - 1);
            send(ex, 200, "application/json", sb.append(']').toString());
        });

        server.createContext("/api/image", ex -> {
            String name = param(ex, "q").trim();
            String category = param(ex, "category").trim();
            String unit = param(ex, "unit").trim();
            Product product = repo.search(name).stream()
                .filter(p -> p.getName().equalsIgnoreCase(name)
                    && p.getCategory().equalsIgnoreCase(category)
                    && p.getUnit().equalsIgnoreCase(unit))
                .findFirst().orElse(null);
            if (name.isEmpty() || name.length() > 120 || product == null) {
                send(ex, 400, "application/json", "{\"image\":\"\",\"source\":\"\"}");
                return;
            }
            if (!product.getImageUrl().isEmpty()) {
                send(ex, 200, "application/json", "{\"image\":" + q(product.getImageUrl()) + ",\"source\":\"\"}");
                return;
            }
            String cacheKey = (name + "|" + category + "|" + unit).toLowerCase(java.util.Locale.ROOT);
            String[] result = IMAGE_CACHE.computeIfAbsent(cacheKey, k -> findProductImage(name, category, unit));
            send(ex, 200, "application/json", "{\"image\":" + q(result[0]) + ",\"source\":" + q(result[1]) + "}");
        });

        server.createContext("/api/search", ex -> {
            String query = param(ex, "q");
            if (query != null && !query.isBlank()) mysql.addRecentSearch(query);
            StringBuilder sb = new StringBuilder("[");
            for (Product p : repo.search(query)) {
                sb.append("{\"name\":").append(q(p.getName()))
                  .append(",\"category\":").append(q(p.getCategory()))
                  .append(",\"emoji\":").append(q(p.getEmoji()))
                  .append(",\"unit\":").append(q(p.getUnit()))
                  .append(",\"image\":").append(q(p.getImageUrl()))
                  .append(",\"prices\":[");
                for (PriceResult r : provider.getPrices(p)) {
                    sb.append("{\"platform\":").append(q(r.platform().getName()))
                      .append(",\"color\":").append(q(r.platform().getColor()))
                      .append(",\"price\":").append(r.price())
                      .append(",\"fee\":").append(r.platform().getDeliveryFee())
                      .append(",\"mins\":").append(r.platform().getDeliveryMins())
                      .append(",\"link\":").append(q(r.link())).append("},");
                }
                sb.setLength(sb.length() - 1);
                sb.append("]},");
            }
            if (sb.length() > 1) sb.setLength(sb.length() - 1);
            send(ex, 200, "application/json", sb.append(']').toString());
        });

        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            Path file = WEB.resolve(path.equals("/") ? "index.html" : path.substring(1)).normalize();
            if (!file.startsWith(WEB) || !Files.isRegularFile(file)) { send(ex, 404, "text/plain; charset=utf-8", "Not found"); return; }
            String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                        : name.endsWith(".css") ? "text/css; charset=utf-8"
                        : name.endsWith(".js") ? "application/javascript; charset=utf-8"
                        : name.endsWith(".json") ? "application/json; charset=utf-8"
                        : name.endsWith(".png") ? "image/png"
                        : (name.endsWith(".jpg") || name.endsWith(".jpeg")) ? "image/jpeg"
                        : name.endsWith(".webp") ? "image/webp"
                        : name.endsWith(".svg") ? "image/svg+xml"
                        : name.endsWith(".ico") ? "image/x-icon"
                        : "application/octet-stream";
            sendBytes(ex, 200, type, Files.readAllBytes(file));
        });

        server.start();
        System.out.println("Running at http://localhost:" + port + "  (Ctrl+C to stop)");
    }

    static String param(HttpExchange ex, String key) {
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return "";
        for (String kv : raw.split("&")) {
            String[] p = kv.split("=", 2);
            if (p[0].equals(key) && p.length == 2) return URLDecoder.decode(p[1], StandardCharsets.UTF_8);
        }
        return "";
    }

    /** Finds a product-matched Bing Images thumbnail; a stock image remains the client-side fallback. */
    static String[] findProductImage(String name, String category, String unit) {
        String compactUnit = unit.replaceAll("\\s+", "");
        String[] queries = {
            name + " " + unit + " " + category + " India product pack",
            name + " " + compactUnit + " " + category + " India product pack",
            name + " " + compactUnit + " India product pack",
            name + " product pack"
        };
        for (String search : queries) {
            try {
                String query = URLEncoder.encode(search, StandardCharsets.UTF_8);
                URI uri = URI.create("https://www.bing.com/images/search?q=" + query + "&first=1");
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131 Safari/537.36")
                        .header("Accept-Language", "en-US,en;q=0.9").GET().build();
                HttpResponse<String> response = IMAGE_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) continue;

                Matcher results = BING_RESULT.matcher(response.body());
                while (results.find()) {
                    String metadata = results.group(1).replace("&quot;", "\"").replace("&amp;", "&");
                    Matcher thumbnail = BING_THUMBNAIL.matcher(metadata);
                    Matcher source = BING_SOURCE.matcher(metadata);
                    Matcher title = BING_TITLE.matcher(metadata);
                    if (!thumbnail.find() || !title.find()) continue;
                    String imageUrl = unescapeJsonUrl(thumbnail.group(1));
                    String sourceUrl = source.find() ? unescapeJsonUrl(source.group(1)) : "";
                    if (!matchesProduct(name, title.group(1) + " " + sourceUrl)) continue;
                    if (imageUrl.startsWith("https://") && imageUrl.contains(".bing.net/"))
                        return new String[]{imageUrl, sourceUrl.startsWith("https://") ? sourceUrl : ""};
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new String[]{"", ""};
            } catch (IOException | IllegalArgumentException ignored) {
                // Try a simpler query, then keep the stock photo if search is unavailable.
            }
        }
        return new String[]{"", ""};
    }

    static boolean matchesProduct(String name, String result) {
        String[] terms = name.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+");
        String text = result.toLowerCase(java.util.Locale.ROOT);
        int required = terms.length < 2 ? 1 : (int) Math.ceil(terms.length * 0.6);
        int matches = 0;
        for (String term : terms) if (term.length() > 2 && text.contains(term)) matches++;
        return matches >= required;
    }

    static String unescapeJsonUrl(String value) {
        return value.replace("\\u0026", "&").replace("\\u003d", "=").replace("\\/", "/");
    }

    static String q(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    static void sendBytes(HttpExchange ex, int code, String type, byte[] b) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        sendBytes(ex, code, type, body.getBytes(StandardCharsets.UTF_8));
    }

    static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String extractJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }

    static int extractJsonInt(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
