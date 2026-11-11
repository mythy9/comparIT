package grocery.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** An online grocery platform. searchUrl is a template; %s is replaced by the encoded query. */
public class Platform {
    private final String name, searchUrl, color;
    private final double priceFactor, deliveryFee;
    private final int deliveryMins;

    public Platform(String name, String searchUrl, String color,
                    double priceFactor, double deliveryFee, int deliveryMins) {
        this.name = name; this.searchUrl = searchUrl; this.color = color;
        this.priceFactor = priceFactor; this.deliveryFee = deliveryFee; this.deliveryMins = deliveryMins;
    }
    public String getName() { return name; }
    public String getColor() { return color; }
    public double getPriceFactor() { return priceFactor; }
    public double getDeliveryFee() { return deliveryFee; }
    public int getDeliveryMins() { return deliveryMins; }

    public String linkFor(String query) {
        return String.format(searchUrl, URLEncoder.encode(query, StandardCharsets.UTF_8));
    }

    public static List<Platform> defaults() {
        return List.of(
            new Platform("Blinkit", "https://blinkit.com/s/?q=%s", "#f8cb46", 1.03, 25, 10),
            new Platform("Zepto", "https://www.zeptonow.com/search?query=%s", "#7b2ff7", 1.00, 30, 10),
            new Platform("Swiggy Instamart", "https://www.swiggy.com/instamart/search?query=%s", "#fc8019", 1.04, 25, 15),
            new Platform("BigBasket", "https://www.bigbasket.com/ps/?q=%s", "#84c225", 0.97, 20, 30),
            new Platform("Amazon Fresh", "https://www.amazon.in/s?k=%s&i=nowstore", "#232f3e", 0.98, 0, 120),
            new Platform("JioMart", "https://www.jiomart.com/search/%s", "#0078ad", 0.95, 0, 90),
            new Platform("Flipkart Minutes", "https://www.flipkart.com/search?q=%s&marketplace=GROCERY", "#2874f0", 1.01, 20, 12),
            new Platform("DMart Ready", "https://www.dmart.in/search?searchTerm=%s", "#00a859", 0.93, 40, 240)
        );
    }
}
