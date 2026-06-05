package br.com.valorae.carteira.util;

import java.util.List;
import br.com.valorae.carteira.model.Position;
import br.com.valorae.carteira.model.PositionTemporalMetrics;
import br.com.valorae.carteira.model.PortfolioTemporalSummary;

public final class PortfolioMath {
    private PortfolioMath() {}

    public static PositionTemporalMetrics positionMetrics(Position p, double currentPrice) {
        if (p == null) return new PositionTemporalMetrics(1, 1d / 365.25d, 0, 0, 0, 0);
        double invested = p.investedValue();
        double price = currentPrice > 0 ? currentPrice : p.averagePrice;
        double current = p.quantity * price;
        double pnl = current - invested;
        double pct = invested > 0 ? (pnl / invested) * 100d : 0d;
        long days = DateUtils.holdingDays(p.purchaseDate);
        double years = Math.max(DateUtils.yearsFromDays(days), 1d / 365.25d);
        double annualized = 0d;
        if (invested > 0 && current > 0) annualized = (Math.pow(current / invested, 1d / years) - 1d) * 100d;
        return new PositionTemporalMetrics(days, years, current, pnl, pct, annualized);
    }

    public static PortfolioTemporalSummary portfolioSummary(List<Position> positions) {
        if (positions == null || positions.isEmpty()) return new PortfolioTemporalSummary(DateUtils.todayIso(), "", 0, 0, 0, 0);
        String start = null;
        String oldestTicker = "";
        double total = 0;
        double weightedDays = 0;
        for (Position p : positions) {
            if (p == null) continue;
            String date = DateUtils.normalizeIsoDate(p.purchaseDate, DateUtils.todayIso());
            if (start == null || date.compareTo(start) < 0) {
                start = date;
                oldestTicker = p.ticker;
            }
            double invested = p.investedValue();
            total += invested;
            weightedDays += invested * DateUtils.holdingDays(date);
        }
        long ageDays = DateUtils.daysBetween(start, DateUtils.todayIso());
        double weighted = total > 0 ? weightedDays / total : 0;
        return new PortfolioTemporalSummary(start, oldestTicker, ageDays, DateUtils.yearsFromDays(ageDays), total, weighted);
    }
}
