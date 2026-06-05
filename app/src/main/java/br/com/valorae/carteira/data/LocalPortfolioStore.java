package br.com.valorae.carteira.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import br.com.valorae.carteira.model.Position;

public class LocalPortfolioStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "valorae_portfolio.db";
    private static final int DB_VERSION = 2;

    public LocalPortfolioStore(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE positions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ticker TEXT NOT NULL UNIQUE," +
                "assetType TEXT NOT NULL," +
                "quantity REAL NOT NULL," +
                "averagePrice REAL NOT NULL," +
                "targetPercent REAL NOT NULL DEFAULT 0," +
                "purchaseDate TEXT NOT NULL DEFAULT '2024-01-02'," +
                "updatedAt INTEGER NOT NULL)");
        seed(db, "PETR4", "ACAO", 18, 32.00, 25, "2023-05-15");
        seed(db, "VALE3", "ACAO", 10, 61.50, 20, "2023-09-01");
        seed(db, "HGLG11", "FII", 8, 160.00, 20, "2024-02-10");
        seed(db, "KNRI11", "FII", 10, 150.00, 20, "2024-06-21");
        seed(db, "IVVB11", "ETF", 4, 290.00, 15, "2025-01-08");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE positions ADD COLUMN purchaseDate TEXT NOT NULL DEFAULT '2024-01-02'"); }
            catch (Exception ignored) {}
        }
    }

    private void seed(SQLiteDatabase db, String ticker, String type, double quantity, double avg, double target, String purchaseDate) {
        ContentValues values = valuesFor(ticker, type, quantity, avg, target, purchaseDate);
        db.insert("positions", null, values);
    }

    private ContentValues valuesFor(String ticker, String type, double quantity, double averagePrice, double targetPercent, String purchaseDate) {
        ContentValues values = new ContentValues();
        values.put("ticker", ticker == null ? "" : ticker.toUpperCase(Locale.ROOT).trim());
        values.put("assetType", type == null ? "ACAO" : type.trim().toUpperCase(Locale.ROOT));
        values.put("quantity", quantity);
        values.put("averagePrice", averagePrice);
        values.put("targetPercent", targetPercent);
        values.put("purchaseDate", purchaseDate == null || purchaseDate.trim().isEmpty() ? "2024-01-02" : purchaseDate.trim());
        values.put("updatedAt", System.currentTimeMillis());
        return values;
    }

    public List<Position> listPositions() {
        ArrayList<Position> out = new ArrayList<>();
        String sql = "SELECT id,ticker,assetType,quantity,averagePrice,targetPercent,purchaseDate FROM positions ORDER BY purchaseDate ASC, ticker ASC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                out.add(new Position(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getDouble(4), c.getDouble(5), c.getString(6)));
            }
        }
        return out;
    }

    public void upsertPosition(String ticker, String type, double quantity, double averagePrice, double targetPercent) {
        upsertPosition(ticker, type, quantity, averagePrice, targetPercent, "2024-01-02");
    }

    public void upsertPosition(String ticker, String type, double quantity, double averagePrice, double targetPercent, String purchaseDate) {
        getWritableDatabase().insertWithOnConflict("positions", null, valuesFor(ticker, type, quantity, averagePrice, targetPercent, purchaseDate), SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void clearPositions() { getWritableDatabase().delete("positions", null, null); }

    public void replacePositions(List<Position> positions) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("positions", null, null);
            if (positions != null) {
                for (Position p : positions) {
                    db.insertWithOnConflict("positions", null, valuesFor(p.ticker, p.assetType, p.quantity, p.averagePrice, p.targetPercent, p.purchaseDate), SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void upsertPositions(List<Position> positions) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (positions != null) {
                for (Position p : positions) {
                    db.insertWithOnConflict("positions", null, valuesFor(p.ticker, p.assetType, p.quantity, p.averagePrice, p.targetPercent, p.purchaseDate), SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void deletePosition(long id) { getWritableDatabase().delete("positions", "id=?", new String[]{String.valueOf(id)}); }
    public void deleteByTicker(String ticker) { getWritableDatabase().delete("positions", "ticker=?", new String[]{ticker}); }
}
