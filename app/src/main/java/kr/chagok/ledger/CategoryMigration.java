package kr.chagok.ledger;
import android.content.*;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import java.io.*;import java.util.*;
/** Startup-only normalization with a durable preference preimage for crash/commit recovery. */
final class CategoryMigration {
 static final String DONE="category_cleanup_089",JOURNAL="category_cleanup_089_journal";
 static List<String> keys(){List<String> keys=new ArrayList<>();for(String type:Ledger.INPUT_TYPES){keys.add(type);keys.add("deleted_"+type);}return keys;}
 static String state(SQLiteDatabase sql,String key){try(Cursor c=sql.rawQuery("SELECT value FROM feature_state WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):null;}}
 static void put(SQLiteDatabase sql,String key,String value){ContentValues v=new ContentValues();v.put("key",key);v.put("value",value);if(sql.update("feature_state",v,"key=?",new String[]{key})==0&&sql.insert("feature_state",null,v)<0)throw new android.database.sqlite.SQLiteException("분류 정리 상태를 저장하지 못했어요.");}
 static Map<String,Set<String>> snapshot(SharedPreferences prefs){Map<String,Set<String>> out=new TreeMap<>();Map<String,?> all=prefs.getAll();for(String key:keys())if(all.containsKey(key))out.put(key,new TreeSet<>(prefs.getStringSet(key,Collections.emptySet())));return out;}
 static String encode(Map<String,Set<String>> data){try{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);o.writeInt(data.size());for(Map.Entry<String,Set<String>> e:data.entrySet()){o.writeUTF(e.getKey());o.writeInt(e.getValue().size());for(String name:e.getValue())o.writeUTF(name);}o.flush();return Base64.getEncoder().encodeToString(b.toByteArray());}catch(IOException ex){throw new IllegalStateException(ex);}}
 static Map<String,Set<String>> decode(String text){try{if(text.length()>4000000)throw new IOException("size");DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text)));int n=in.readInt();if(n<0||n>keys().size())throw new IOException("keys");Map<String,Set<String>> out=new TreeMap<>();for(int i=0;i<n;i++){String key=in.readUTF();int count=in.readInt();if(!keys().contains(key)||out.containsKey(key)||count<0||count>10000)throw new IOException("data");Set<String> values=new TreeSet<>();for(int j=0;j<count;j++)values.add(in.readUTF());out.put(key,values);}if(in.read()!=-1)throw new IOException("tail");return out;}catch(Exception ex){throw new IllegalStateException("분류 정리 복구 자료를 확인해 주세요.",ex);}}
 static void writePrefs(SharedPreferences prefs,Map<String,Set<String>> values){SharedPreferences.Editor edit=prefs.edit();for(String key:keys()){if(values.containsKey(key))edit.putStringSet(key,new HashSet<>(values.get(key)));else edit.remove(key);}if(!edit.commit())throw new IllegalStateException("상세 분류 설정을 저장하지 못했어요.");}
 static synchronized void run(Context context,LedgerDb db){
  SQLiteDatabase sql=db.getWritableDatabase();SharedPreferences prefs=context.getSharedPreferences("category_choices",0);
  if(state(sql,DONE)!=null){sql.delete("feature_state","key=?",new String[]{JOURNAL});return;}
  String recovery=state(sql,JOURNAL);if(recovery!=null){writePrefs(prefs,decode(recovery));sql.delete("feature_state","key=?",new String[]{JOURNAL});}
  Map<String,Set<String>> beforePrefs=snapshot(prefs),afterPrefs=new TreeMap<>();for(Map.Entry<String,Set<String>> e:beforePrefs.entrySet())afterPrefs.put(e.getKey(),CategoryNames.clean(e.getValue()));
  put(sql,JOURNAL,encode(beforePrefs));boolean committed=false;List<String> log=new ArrayList<>();int rows=0;boolean prefWritten=false;
  try{
   sql.beginTransaction();try{
    AssetConsistency.State before=new AssetConsistency.State(db.openingAssets(),db.all(),db.movements());
    List<Long> ids=new ArrayList<>();List<String> names=new ArrayList<>();try(Cursor c=sql.rawQuery("SELECT rowid,category FROM entries ORDER BY rowid",null)){while(c.moveToNext()){String raw=c.getString(1),clean=Ledger.cleanCategory(raw);if(clean.isEmpty())clean="기타";if(!clean.equals(raw)){ids.add(c.getLong(0));names.add(clean);}}}
    for(int i=0;i<ids.size();i++){ContentValues v=new ContentValues();v.put("category",names.get(i));sql.update("entries",v,"rowid=?",new String[]{Long.toString(ids.get(i))});}rows=ids.size();
    String plan=state(sql,"planning");if(plan!=null){String clean=PlanningData.decode(plan,log).encode();if(!clean.equals(plan))put(sql,"planning",clean);}
    List<String[]> features=new ArrayList<>();try(Cursor c=sql.rawQuery("SELECT key,value FROM feature_state WHERE key LIKE 'opening:%' OR key LIKE 'movement:%' ORDER BY rowid",null)){while(c.moveToNext())features.add(new String[]{c.getString(0),c.getString(1)});}
    for(String[] item:features){String clean=item[0].startsWith("opening:")?OpeningAsset.encodeAll(OpeningAsset.decodeAll(item[1])):SavingsMovement.encodeAll(SavingsMovement.decodeAll(item[1]));if(!clean.equals(item[1]))put(sql,item[0],clean);}
    AssetConsistency.validate(before,new AssetConsistency.State(db.openingAssets(),db.all(),db.movements()));
    prefWritten=true;writePrefs(prefs,afterPrefs);
    put(sql,DONE,"entries="+rows+";limitConflicts="+log.size()+";"+String.join(" | ",log));sql.setTransactionSuccessful();
   }finally{sql.endTransaction();}committed=true;
  }finally{
   boolean restored=!prefWritten;if(!committed&&prefWritten){writePrefs(prefs,beforePrefs);restored=true;}
   if(committed||restored)sql.delete("feature_state","key=?",new String[]{JOURNAL});
  }
  if(committed){sql.delete("feature_state","key=?",new String[]{JOURNAL});android.util.Log.i("CategoryCleanup",state(sql,DONE));}
 }
}
