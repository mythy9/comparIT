package grocery.service;

import grocery.model.Product;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Loads products from data/products.csv (name,category,emoji,unit,basePrice[,imageUrl]). */
public class ProductRepository {
    private final List<Product> products = new ArrayList<>();

    public ProductRepository(Path csv) throws IOException {
        for (String line : Files.readAllLines(csv)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] f = line.split(",");
            String imageUrl = f.length > 5 ? String.join(",", Arrays.copyOfRange(f, 5, f.length)).trim() : "";
            products.add(new Product(f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim(),
                    Double.parseDouble(f[4].trim()), imageUrl));
        }
    }

    /** Exact word match first; if nothing matches, falls back to typo-tolerant matching ("mlk" / "pannir"). */
    public List<Product> search(String query) {
        String[] words = query.toLowerCase().trim().split("\\s+");
        List<Product> res = new ArrayList<>();
        for (Product p : products) {
            String hay = (p.getName() + " " + p.getCategory()).toLowerCase();
            boolean ok = true;
            for (String w : words) if (!hay.contains(w)) { ok = false; break; }
            if (ok) res.add(p);
        }
        if (res.isEmpty() && !query.isBlank())
            for (Product p : products) if (fuzzy(p, words)) res.add(p);
        return res;
    }

    /** Autocomplete: names starting with the text first, then names containing it. */
    public List<String> suggest(String query, int max) {
        String q = query.toLowerCase().trim();
        List<String> starts = new ArrayList<>(), contains = new ArrayList<>();
        if (q.isEmpty()) return starts;
        for (Product p : products) {
            String n = p.getName().toLowerCase();
            if (n.startsWith(q)) starts.add(p.getName());
            else if (n.contains(q)) contains.add(p.getName());
        }
        starts.addAll(contains);
        return starts.subList(0, Math.min(max, starts.size()));
    }

    public Set<String> categories() {
        Set<String> c = new LinkedHashSet<>();
        for (Product p : products) c.add(p.getCategory());
        return c;
    }

    public List<Product> getAll() {
        return Collections.unmodifiableList(products);
    }

    private boolean fuzzy(Product p, String[] words) {
        String[] tokens = (p.getName() + " " + p.getCategory()).toLowerCase().split("\\W+");
        for (String w : words) {
            boolean found = false;
            for (String t : tokens)
                if (t.startsWith(w) || distance(w, t) <= (w.length() > 5 ? 2 : 1)) { found = true; break; }
            if (!found) return false;
        }
        return true;
    }

    /** Levenshtein edit distance. */
    private int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++)
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
