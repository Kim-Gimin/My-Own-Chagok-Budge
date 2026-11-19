package kr.chagok.ledger;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

final class LedgerDb extends SQLiteOpenHelper {
    LedgerDb(Context context) { super(context, "chagok.db", null, 4); }
    @Override public void onCreate(SQLiteDatabase db) {
        createEntries(db,"entries");
        createPlanning(db);
        db.execSQL("CREATE INDEX entries_date ON entries(date)");
    }
    private static void createEntries(SQLiteDatabase db,String table) {
        db.execSQL("CREATE TABLE "+table+" (id TEXT PRIMARY KEY NOT NULL, date TEXT NOT NULL, type TEXT NOT NULL CHECK(type IN ('수입','지출','저축','투자','저축회수')), amount INTEGER NOT NULL CHECK(amount>0 AND amount<=999999999999), category TEXT NOT NULL, memo TEXT NOT NULL)");
    }
    private static void createPlanning(SQLiteDatabase db){db.execSQL("CREATE TABLE feature_state (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to){
        if(from<1||from>3||to!=4)throw new IllegalStateException("지원하지 않는 데이터 버전입니다.");
        if(from<4) {
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
        try{AssetConsistency.State guardBefore=guardState();PlanningData p=planning();PlanningData.Repeat r=p.repeat(repeatId);if(!r.enabled||!r.next.toString().equals(occurrence)||!entry.id.equals(r.entryId()))throw new IllegalArgumentException("반복 일정이 변경됐어요. 다시 열어 주세요.");
            if(r.next.isAfter(java.time.LocalDate.now()))throw new IllegalArgumentException("예정일 전의 기록은 직접 입력해 주세요.");
            sql.insertOrThrow("entries",null,values(entry));p.put(r.advance());savePlanning(p);validateAssets(guardBefore);sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
    }
    void skipOccurrence(String repeatId,String occurrence){
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{PlanningData p=planning();PlanningData.Repeat r=p.repeat(repeatId);if(!r.next.toString().equals(occurrence))throw new IllegalArgumentException("반복 일정이 변경됐어요. 다시 열어 주세요.");p.put(r.advance());savePlanning(p);sql.setTransactionSuccessful();}finally{sql.endTransaction();}
    }
    BackupArchive.Snapshot snapshot(){SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{BackupArchive.Snapshot snapshot=new BackupArchive.Snapshot(all(),planning(),movements(),openingAssets());sql.setTransactionSuccessful();return snapshot;}finally{sql.endTransaction();}}
    Ledger.ImportPlan importPlanningAtomic(BackupArchive.Snapshot snapshot){
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{Ledger.ImportPlan plan=planImport(snapshot.entries,snapshot.openingAssets,snapshot.movements);if(!plan.errors.isEmpty())return plan;PlanningData merged=PlanningData.merge(planning(),snapshot.planning);checkIncomingMovements(snapshot);for(Ledger.Entry e:plan.additions)sql.insertOrThrow("entries",null,values(e));savePlanning(merged);for(OpeningAsset a:plan.openingAdditions)putOpening(a);for(SavingsMovement m:snapshot.movements)if(movement(m.id)==null)putMovement(m);sql.setTransactionSuccessful();return plan;}finally{sql.endTransaction();}
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
    private AssetConsistency.State assetState(){return new AssetConsistency.State(openingAssets(),all(),movements());}
    private AssetConsistency.State guardState(){List<SavingsMovement> moves=movements();return new AssetConsistency.State(openingAssets(),moves.isEmpty()?Collections.emptyList():all(),moves);}
    private void validateAssets(AssetConsistency.State before){AssetConsistency.validate(before,guardState());}
    void save(Ledger.Entry e,boolean update){SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{AssetConsistency.State before=guardState();if(update){if(sql.update("entries",values(e),"id=?",new String[]{e.id})!=1)throw new IllegalStateException("내역을 찾지 못했어요.");}else sql.insertOrThrow("entries",null,values(e));validateAssets(before);sql.setTransactionSuccessful();}finally{sql.endTransaction();}}
    void delete(String id){SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{AssetConsistency.State before=guardState();sql.delete("entries","id=?",new String[]{id});validateAssets(before);sql.setTransactionSuccessful();}finally{sql.endTransaction();}}
    Ledger.ImportPlan planImport(List<Ledger.Entry> input,List<OpeningAsset> opening){return planImport(input,opening,Collections.emptyList());}
    Ledger.ImportPlan planImport(List<Ledger.Entry> input,List<OpeningAsset> opening,List<SavingsMovement> incoming){
        AssetConsistency.State before=assetState();Ledger.ImportPlan plan=Ledger.planImport(input,before.entries);OpeningAsset.plan(opening,before.opening,plan);
        if(!plan.errors.isEmpty())return plan;
        List<Ledger.Entry> rows=new ArrayList<>(before.entries);rows.addAll(plan.additions);List<OpeningAsset> assets=new ArrayList<>(before.opening);assets.addAll(plan.openingAdditions);List<SavingsMovement> moves=new ArrayList<>(before.movements);
        try{Map<String,SavingsMovement> ids=new HashMap<>();for(SavingsMovement m:moves)ids.put(m.id,m);for(SavingsMovement m:incoming){SavingsMovement old=ids.get(m.id);if(old!=null&&!old.encode().equals(m.encode()))throw new IllegalArgumentException("같은 ID의 저축 이동 내용이 달라요.");if(old==null){moves.add(m);ids.put(m.id,m);}}AssetConsistency.validate(before,new AssetConsistency.State(assets,rows,moves));}catch(IllegalArgumentException ex){plan.errors.add(ex.getMessage());}return plan;
    }
    Ledger.ImportPlan importAtomic(List<Ledger.Entry> input){return importAtomic(input,Collections.emptyList());}
    Ledger.ImportPlan importAtomic(List<Ledger.Entry> input,List<OpeningAsset> opening) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Ledger.ImportPlan plan = planImport(input,opening);
            if (!plan.errors.isEmpty()) return plan;
            for (Ledger.Entry entry : plan.additions) db.insertOrThrow("entries", null, values(entry));
            for(OpeningAsset a:plan.openingAdditions)putOpening(a);
            db.setTransactionSuccessful();
            return plan;
        } finally { db.endTransaction(); }
    }

    List<OpeningAsset> openingAssets(){List<OpeningAsset> out=new ArrayList<>();try(Cursor c=getReadableDatabase().query("feature_state",new String[]{"value"},"key LIKE ?",new String[]{"opening:%"},null,null,"rowid")){while(c.moveToNext())out.addAll(OpeningAsset.decodeAll(c.getString(0)));}return out;}
    private void putOpening(OpeningAsset a){if(a.id.isEmpty())throw new IllegalArgumentException("기초자산 ID가 없어요.");ContentValues v=new ContentValues();v.put("key","opening:"+a.id);v.put("value",a.encode());getWritableDatabase().insertOrThrow("feature_state",null,v);}
    void saveOpening(OpeningAsset a,boolean update){
        if(a.id.startsWith("move-base:")&&movement(a.id.substring(10))!=null)throw new IllegalArgumentException("연결된 기초자산은 저축 해지·배분에서 수정하세요.");
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{AssetConsistency.State before=guardState();for(OpeningAsset old:before.opening)if(!old.id.equals(a.id)&&old.fingerprint().equals(a.fingerprint()))throw new IllegalArgumentException("같은 기초자산이 이미 있어요.");if(update&&sql.delete("feature_state","key=?",new String[]{"opening:"+a.id})!=1)throw new IllegalArgumentException("기초자산을 찾지 못했어요.");List<OpeningAsset> dated=new ArrayList<>(openingAssets());dated.add(a);OpeningAsset.sameDate(dated);putOpening(a);validateAssets(before);sql.setTransactionSuccessful();}finally{sql.endTransaction();}}
    void deleteOpening(String id){if(id.startsWith("move-base:")&&movement(id.substring(10))!=null)throw new IllegalArgumentException("연결된 기초자산은 이동 전체에서 삭제하세요.");SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{AssetConsistency.State before=guardState();sql.delete("feature_state","key=?",new String[]{"opening:"+id});validateAssets(before);sql.setTransactionSuccessful();}finally{sql.endTransaction();}}
    private void replaceMovementOpening(SavingsMovement old,SavingsMovement next,boolean delete){
        OpeningAsset before=old==null?null:old.opening(),after=delete?null:next.opening();List<OpeningAsset> existing=openingAssets();
        if(before!=null){OpeningAsset found=null;for(OpeningAsset a:existing)if(a.id.equals(before.id))found=a;if(found==null||!found.fingerprint().equals(before.fingerprint()))throw new IllegalArgumentException("연결된 기초자산이 바뀌었어요. 복원 내용을 확인하세요.");getWritableDatabase().delete("feature_state","key=?",new String[]{"opening:"+before.id});}
        if(!delete&&next.detailed&&!next.createOpening&&next.previousPrincipal>0){long basis=0;for(OpeningAsset a:existing)if((before==null||!a.id.equals(before.id))&&a.kind.equals("저축")&&a.category.equals(next.source)&&a.date.compareTo(next.date)<=0)basis=Math.addExact(basis,a.amount);if(basis<next.previousPrincipal)throw new IllegalArgumentException("기존 기초자산이 이전 원금보다 적어요. 금액을 확인하거나 기초자산 함께 등록을 선택하세요.");}
        if(after!=null){List<OpeningAsset> dated=new ArrayList<>();for(OpeningAsset a:existing)if(before==null||!a.id.equals(before.id))dated.add(a);dated.add(after);OpeningAsset.sameDate(dated);for(OpeningAsset a:existing)if((before==null||!a.id.equals(before.id))&&a.kind.equals(after.kind)&&a.category.equals(after.category))throw new IllegalArgumentException("이 분류의 기초자산이 이미 있어요. ‘기초자산 함께 등록’을 끄고 기존 자산을 확인하세요.");putOpening(after);}
    }
    List<SavingsMovement> movements(){List<SavingsMovement> out=new ArrayList<>();try(Cursor c=getReadableDatabase().query("feature_state",new String[]{"value"},"key LIKE ?",new String[]{"movement:%"},null,null,"rowid")){while(c.moveToNext())out.addAll(SavingsMovement.decodeAll(c.getString(0)));}return out;}
    SavingsMovement movement(String id){for(SavingsMovement m:movements())if(m.id.equals(id))return m;return null;}
    void putMovement(SavingsMovement m){ContentValues v=new ContentValues();v.put("key","movement:"+m.id);v.put("value",m.encode());SQLiteDatabase sql=getWritableDatabase();if(sql.update("feature_state",v,"key=?",new String[]{"movement:"+m.id})==0&&sql.insert("feature_state",null,v)<0)throw new IllegalStateException("이동 저장 실패");}
    void checkIncomingMovements(BackupArchive.Snapshot snapshot){Map<String,Ledger.Entry> rows=new HashMap<>();for(Ledger.Entry e:snapshot.entries)rows.put(e.id,e);for(SavingsMovement m:snapshot.movements){OpeningAsset required=m.opening();if(required!=null&&!snapshot.openingAssets.stream().anyMatch(a->a.id.equals(required.id)&&a.fingerprint().equals(required.fingerprint())))throw new IllegalArgumentException("이동과 연결 기초자산이 일치하지 않아요.");SavingsMovement old=movement(m.id);if(old!=null&&!old.encode().equals(m.encode()))throw new IllegalArgumentException("같은 ID의 저축 이동 내용이 달라요.");for(Ledger.Entry e:m.entries())if(!rows.containsKey(e.id)||!rows.get(e.id).sameContent(e))throw new IllegalArgumentException("저축 이동과 연결 내역이 일치하지 않아요.");}}
    void saveMovement(SavingsMovement m,boolean delete){SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{AssetConsistency.State guardBefore=guardState();SavingsMovement old=movement(m.id);if(old==null&&delete)throw new IllegalArgumentException("이동을 찾지 못했어요.");if(old!=null){for(Ledger.Entry e:old.entries()){try(Cursor c=sql.query("entries",null,"id=?",new String[]{e.id},null,null,null)){if(!c.moveToFirst()||!new Ledger.Entry(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4),c.getString(5),0).sameContent(e))throw new IllegalArgumentException("연결 내역이 바뀌었어요. 백업 내역을 확인하세요.");}sql.delete("entries","id=?",new String[]{e.id});}}else for(SavingsMovement saved:movements())if(saved.date.equals(m.date)&&saved.source.equals(m.source)&&saved.principal==m.principal&&saved.received==m.received&&saved.saving==m.saving&&saved.investment==m.investment&&saved.savingCategory.equals(m.savingCategory)&&saved.investmentCategory.equals(m.investmentCategory)&&saved.memo.equals(m.memo)&&saved.previousPrincipal==m.previousPrincipal&&saved.government==m.government&&saved.createOpening==m.createOpening&&saved.detailed==m.detailed)throw new IllegalArgumentException("같은 날짜·금액의 이동이 이미 있어요.");
        replaceMovementOpening(old,m,delete);
        if(delete)sql.delete("feature_state","key=?",new String[]{"movement:"+m.id});else{for(Ledger.Entry e:m.entries())sql.insertOrThrow("entries",null,values(e));putMovement(m);}validateAssets(guardBefore);sql.setTransactionSuccessful();}finally{sql.endTransaction();}}
}
