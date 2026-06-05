package br.com.valorae.carteira.model;

public class PortfolioTemporalSummary {
    public String startDate;
    public String oldestTicker;
    public long ageDays;
    public double ageYears;
    public double totalInvested;
    public double weightedHoldingDays;

    public PortfolioTemporalSummary(String startDate, String oldestTicker, long ageDays, double ageYears, double totalInvested, double weightedHoldingDays) {
        this.startDate = startDate;
        this.oldestTicker = oldestTicker;
        this.ageDays = ageDays;
        this.ageYears = ageYears;
        this.totalInvested = totalInvested;
        this.weightedHoldingDays = weightedHoldingDays;
    }
}
