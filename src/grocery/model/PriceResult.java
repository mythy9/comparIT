package grocery.model;

/** One platform's price for one product. */
public record PriceResult(Platform platform, double price, String link) {
    public double total() { return price + platform.getDeliveryFee(); }
}
