package grocery.service;

import grocery.model.*;
import java.util.List;

/** Abstraction: swap MockPriceProvider for a real/live provider without touching the rest. */
public interface PriceProvider {
    List<PriceResult> getPrices(Product product);
}
