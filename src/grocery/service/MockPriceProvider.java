package grocery.service;

import grocery.model.*;
import java.util.*;

/** Generates stable, realistic SAMPLE prices (base price x platform factor x small variation). */
public class MockPriceProvider implements PriceProvider {
    private final List<Platform> platforms;

    public MockPriceProvider(List<Platform> platforms) { this.platforms = platforms; }

    @Override
    public List<PriceResult> getPrices(Product p) {
        List<PriceResult> out = new ArrayList<>();
        for (Platform pl : platforms) {
            int h = Math.abs((p.getName() + pl.getName()).hashCode());
            double variation = 0.92 + (h % 17) / 100.0;           // 0.92 .. 1.08
            double price = Math.max(1, Math.round(p.getBasePrice() * pl.getPriceFactor() * variation));
            out.add(new PriceResult(pl, price, pl.linkFor(p.getName())));
        }
        out.sort(Comparator.comparingDouble(PriceResult::total));
        return out;
    }
}
