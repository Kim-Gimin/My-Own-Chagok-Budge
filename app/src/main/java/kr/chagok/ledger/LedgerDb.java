package kr.chagok.ledger;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

final class LedgerDb extends SQLiteOpenHelper {
    LedgerDb(Context context) { super(context, "chagok.db", null, 2); }
    @Override public void onCreate(SQLiteDatabase db) {
        createEntries(db,"entries");
        db.execSQL("CREATE INDEX entries_date ON entries(date)");
    }
    private static void createEntries(SQLiteDatabase db,String table) {
        db.execSQL("CREATE TABLE "+table+" (id TEXT PRIMARY KEY NOT NULL, date TEXT NOT NULL, type TEXT NOT NULL CHECK(type IN ('수입','지출','저축','투자')), amount INTEGER NOT NULL CHECK(amount>0 AND amount<=999999999999), category TEXT NOT NULL, memo TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int from, int to) {
        if(from==1 && to==2) {
            // SQLiteOpenHelper runs this upgrade in a transaction. Keep IDs and row order.
            createEntries(db,"entries_next");
            db.execSQL("INSERT INTO entries_next (rowid,id,date,type,amount,category,memo) SELECT rowid,id,date,type,amount,category,memo FROM entries");
            db.execSQL("DROP TABLE entries");
            db.execSQL("ALTER TABLE entries_next RENAME TO entries");
            db.execSQL("CREATE INDEX entries_date ON entries(date)");
        } else throw new IllegalStateException("지원하지 않는 데이터 버전입니다.");
    }
    List<Ledger.Entry> all() {
        List<Ledger.Entry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("entries", null, null, null, null, null, "date DESC, rowid DESC")) {
            while (c.moveToNext()) result.add(new Ledger.Entry(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4), c.getString(5), 0));
        }
        return result;
    }
    private ContentValues values(Ledger.Entry e) {
        ContentValues v = new ContentValues();
        v.put("id",e.id); v.put("date",e.date); v.put("type",e.type); v.put("amount",e.amount); v.put("category",e.category); v.put("memo",e.memo);
        return v;
    }
    void save(Ledger.Entry e, boolean update) {
        SQLiteDatabase db = getWritableDatabase();
        if (update) {
            if (db.update("entries", values(e), "id=?", new String[]{e.id}) != 1) throw new IllegalStateException("내역을 찾지 못했어요.");
        } else db.insertOrThrow("entries", null, values(e));
    }
    void delete(String id) { getWritableDatabase().delete("entries", "id=?", new String[]{id}); }
    Ledger.ImportPlan importAtomic(List<Ledger.Entry> input) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Ledger.ImportPlan plan = Ledger.planImport(input, all());
            if (!plan.errors.isEmpty()) return plan;
            for (Ledger.Entry entry : plan.additions) db.insertOrThrow("entries", null, values(entry));
            db.setTransactionSuccessful();
            return plan;
        } finally { db.endTransaction(); }
    }
}
