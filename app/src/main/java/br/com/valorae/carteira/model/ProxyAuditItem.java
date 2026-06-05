package br.com.valorae.carteira.model;

public class ProxyAuditItem {
    public final String title;
    public final String route;
    public final boolean ok;
    public final String detail;
    public final int recordsCount;

    public ProxyAuditItem(String title, String route, boolean ok, String detail, int recordsCount) {
        this.title = title;
        this.route = route;
        this.ok = ok;
        this.detail = detail;
        this.recordsCount = recordsCount;
    }
}
