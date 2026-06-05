package br.com.valorae.carteira.model;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {
    public int rowsRead;
    public int validTrades;
    public int skippedRows;
    public int buyTrades;
    public int sellTrades;
    public int positionsCreated;
    public int positionsReplaced;
    public double grossValue;
    public final List<String> messages = new ArrayList<>();

    public boolean hasUsefulData() { return positionsCreated > 0 || positionsReplaced > 0 || validTrades > 0; }

    public String shortStatus() {
        if (hasUsefulData()) return positionsCreated + " ativo(s) atualizados";
        return "Nenhum ativo importado";
    }

    public String readableSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Linhas lidas: ").append(rowsRead).append('\n');
        sb.append("Negociações válidas: ").append(validTrades).append('\n');
        sb.append("Compras: ").append(buyTrades).append(" • Vendas: ").append(sellTrades).append('\n');
        sb.append("Ativos consolidados: ").append(positionsCreated).append('\n');
        if (positionsReplaced > 0) sb.append("Posições substituídas: ").append(positionsReplaced).append('\n');
        if (skippedRows > 0) sb.append("Linhas ignoradas: ").append(skippedRows).append('\n');
        if (!messages.isEmpty()) {
            sb.append('\n').append("Observações:").append('\n');
            for (int i = 0; i < Math.min(messages.size(), 8); i++) sb.append("• ").append(messages.get(i)).append('\n');
        }
        return sb.toString().trim();
    }
}
