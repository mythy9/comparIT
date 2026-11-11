package grocery.model;

/** A grocery item from the catalogue. imageUrl is optional (empty = show emoji tile). */
public class Product {
    private final String name, category, emoji, unit, imageUrl;
    private final double basePrice;

    public Product(String name, String category, String emoji, String unit, double basePrice, String imageUrl) {
        this.name = name; this.category = category; this.emoji = emoji;
        this.unit = unit; this.basePrice = basePrice; this.imageUrl = imageUrl;
    }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getEmoji() { return emoji; }
    public String getUnit() { return unit; }
    public double getBasePrice() { return basePrice; }
    public String getImageUrl() { return imageUrl; }
}
