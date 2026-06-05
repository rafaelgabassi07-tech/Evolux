package br.com.valorae.carteira.model;

public class PositionTemporalMetrics {
    public long holdingDays;
    public double holdingYears;
    public double currentValue;
    public double unrealizedPnl;
    public double unrealizedPnlPercent;
    public double annualizedReturnPercent;

    public PositionTemporalMetrics(long holdingDays, double holdingYears, double currentValue, double unrealizedPnl, double unrealizedPnlPercent, double annualizedReturnPercent) {
        this.holdingDays = holdingDays;
        this.holdingYears = holdingYears;
        this.currentValue = currentValue;
        this.unrealizedPnl = unrealizedPnl;
        this.unrealizedPnlPercent = unrealizedPnlPercent;
        this.annualizedReturnPercent = annualizedReturnPercent;
    }
}
