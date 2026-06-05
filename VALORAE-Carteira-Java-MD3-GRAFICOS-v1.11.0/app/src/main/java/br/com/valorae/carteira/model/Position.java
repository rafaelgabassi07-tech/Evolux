package br.com.valorae.carteira.model;

public class Position {
    public long id;
    public String ticker;
    public String assetType;
    public double quantity;
    public double averagePrice;
    public double targetPercent;
    public String purchaseDate;

    public Position(long id, String ticker, String assetType, double quantity, double averagePrice, double targetPercent) {
        this(id, ticker, assetType, quantity, averagePrice, targetPercent, "2024-01-02");
    }

    public Position(long id, String ticker, String assetType, double quantity, double averagePrice, double targetPercent, String purchaseDate) {
        this.id = id;
        this.ticker = ticker == null ? "" : ticker.trim().toUpperCase();
        this.assetType = assetType == null ? "ACAO" : assetType.trim().toUpperCase();
        this.quantity = quantity;
        this.averagePrice = averagePrice;
        this.targetPercent = targetPercent;
        this.purchaseDate = purchaseDate == null || purchaseDate.trim().isEmpty() ? "2024-01-02" : purchaseDate.trim();
    }

    public double investedValue() { return quantity * averagePrice; }
}
