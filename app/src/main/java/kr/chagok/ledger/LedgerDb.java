package kr.chagok.ledger;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

final class LedgerDb extends SQLiteOpenHelper {
    LedgerDb(Context context) { super(context, "chagok.db", null, 3); }
    @Override public void onCreate(SQLiteDatabase db) {
        createEntries(db,"entries");
        createPlanning(db);
        db.execSQL("CREATE INDEX entries_date ON entries(date)");
    }
    private static void createEntries(SQLiteDatabase db,String table) {
        db.execSQL("CREATE TABLE "+table+" (id TEXT PRIMARY KEY NOT NULL, date TEXT NOT NULL, type TEXT NOT NULL CHECK(type IN ('수입','지출','저축','투자')), amount INTEGER NOT NULL CHECK(amount>0 AND amount<=999999999999), category TEXT NOT NULL, memo TEXT NOT NULL)");
    }
    private static void createPlanning(SQLiteDatabase db){db.execSQL("CREATE TABLE feature_state (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to){
        if(from<1||from>2||to!=3)throw new IllegalStateException("지원하지 않는 데이터 버전입니다.");
        if(from==1) {
            createEntries(db,"entries_next");
            db.execSQL("INSERT INTO entries_next (rowid,id,date,type,amount,category,memo) SELECT rowid,id,date,type,amount,category,memo FROM entries");
            db.execSQL("DROP TABLE entries");
            db.execSQL("ALTER TABLE entries_next RENAME TO entries");
            db.execSQL("CREATE INDEX entries_date ON entries(date)");
        }
        if(from<3)createPlanning(db);
    }
    PlanningData planning(){
        try(Cursor c=getReadableDatabase().query("feature_state",new String[]{"value"},"key=?",new String[]{"planning"},null,null,null)){return c.moveToFirst()?PlanningData.decode(c.getString(0)):new PlanningData();}
    }
    void savePlanning(PlanningData planning){
        String encoded=planning.encode();PlanningData.decode(encoded);ContentValues values=new ContentValues();values.put("key","planning");values.put("value",encoded);if(getWritableDatabase().insertWithOnConflict("feature_state",null,values,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IllegalStateException("계획을 저장하지 못했어요.");
    }
    void saveOccurrence(Ledger.Entry entry,String repeatId,String occurrence){
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try{PlanningData p=planning();PlanningData.Repeat r=p.repeat(repeatId);if(!r.enabled||!r.next.toString().equals(occurrence)||!entry.id.equals(r.entryId()))throw new IllegalArgumentException("반복 일정이 변경됐어요. 다시 열어 주세요.");
            if(r.next.isAfter(java.time.LocalDate.now()))throw new IllegalArgumentException("예정일 전의 기록은 직접 입력해 주세요.");
            sql.insertOrThrow("entries",null,values(entry));p.put(r.advance());savePlanning(p);sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
    }
    void skipOccurrence(String repeatId,String occurrence){
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{PlanningData p=planning();PlanningData.Repeat r=p.repeat(repeatId);if(!r.next.toString().equals(occurrence))throw new IllegalArgumentException("반복 일정이 변경됐어요. 다시 열어 주세요.");p.put(r.advance());savePlanning(p);sql.setTransactionSuccessful();}finally{sql.endTransaction();}
    }
    BackupArchive.Snapshot snapshot(){SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{BackupArchive.Snapshot snapshot=new BackupArchive.Snapshot(all(),planning());sql.setTransactionSuccessful();return snapshot;}finally{sql.endTransaction();}}
    Ledger.ImportPlan importPlanningAtomic(BackupArchive.Snapshot snapshot){
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{Ledger.ImportPlan plan=Ledger.planImport(snapshot.entries,all());if(!plan.errors.isEmpty())return plan;PlanningData merged=PlanningData.merge(planning(),snapshot.planning);for(Ledger.Entry e:plan.additions)sql.insertOrThrow("entries",null,values(e));savePlanning(merged);sql.setTransactionSuccessful();return plan;}finally{sql.endTransaction();}
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
