package kr.chagok.ledger;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Runs only in the disposable emulator; never on a user's phone. */
final class TimeChecks {
 static File saved(Context c){return new File(c.getExternalFilesDir(null),"time-before.txt");}
 static String classic(Context c)throws Exception{
  List<String> lines=new ArrayList<>();try(LedgerDb db=new LedgerDb(c)){
   try(Cursor q=db.getReadableDatabase().rawQuery("SELECT rowid,id,date,type,amount,category,memo FROM entries ORDER BY rowid",null)){while(q.moveToNext()){StringJoiner row=new StringJoiner("|");for(int i=0;i<7;i++)row.add(q.getString(i));lines.add("entry="+row);}}
   try(Cursor q=db.getReadableDatabase().rawQuery("SELECT rowid,key,value FROM feature_state ORDER BY rowid",null)){while(q.moveToNext())lines.add("feature="+q.getLong(0)+"|"+q.getString(1)+"|"+q.getString(2));}
  }for(String file:new String[]{"app_preferences","category_choices","backup_status"}){TreeMap<String,Object> prefs=new TreeMap<>();for(Map.Entry<String,?> e:c.getSharedPreferences(file,0).getAll().entrySet())prefs.put(e.getKey(),e.getValue() instanceof Set?new TreeSet<>((Set<String>)e.getValue()):e.getValue());lines.add(file+"="+prefs);}return String.join("\n",lines);
 }
 static void before(DeviceChecks t)throws Exception{
  Context c=t.getTargetContext();t.eq("0.8.11",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
  for(String f:new String[]{"app_preferences","category_choices","backup_status"})c.getSharedPreferences(f,0).edit().clear().commit();
  try(LedgerDb db=AssetGuardChecks.fresh(c)){
   db.saveOpening(AssetGuardChecks.basis("time-basis",1000),false);db.save(new Ledger.Entry("time-deposit","2026-02-01","저축",200,"적금","원래 합성 기록",0),false);
   db.saveMovement(new SavingsMovement("time-move","2026-03-01","적금",1200,1300,1100,"예금",100,"ETF","합성 보존",1000,0,false,true),false);
   PlanningData p=new PlanningData();p.put(new PlanningData.Favorite("time-favorite","시간 검증","지출",100,"식비","합성"));p.selectLivingCategories(Arrays.asList("식비","카페"));db.savePlanning(p);CategoryMigration.run(c,db);t.eq(4,db.getReadableDatabase().getVersion());
  }new AppPreferences(c).dark(true);new CategoryStore(c).remember("지출","보존 분류");c.getSharedPreferences("backup_status",0).edit().putLong("last_full",12345L).putLong("first_seen",12340L).commit();
  try(Writer w=new OutputStreamWriter(new FileOutputStream(saved(c)),StandardCharsets.UTF_8)){w.write(classic(c));}t.eq(true,saved(c).length()>0);
 }
 static void upgraded(DeviceChecks t)throws Exception{
  Context c=t.getTargetContext();String before;try(InputStream in=new FileInputStream(saved(c));ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);before=new String(b.toByteArray(),StandardCharsets.UTF_8);}
  t.eq(before,classic(c));try(LedgerDb db=new LedgerDb(c)){t.eq(5,db.getReadableDatabase().getVersion());for(Ledger.Entry e:db.all())t.eq("00:00:00",e.time);for(SavingsMovement m:db.movements())t.eq("00:00:00",m.time);t.eq(1,db.openingAssets().size());t.eq(1,db.planning().favorites.size());}
  t.eq("0.8.12",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);t.eq(26,c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode);
  MainActivity app=StartupChecks.start(t,false);t.eq(5,UserGuideChecks.get(app,"tab"));t.eq(YearMonth.now(),UserGuideChecks.get(app,"calendarMonth"));StartupChecks.close(t,app);t.eq(before,classic(c));
 }
 static void createLegacy(Context c,int version,boolean collide){
  c.deleteDatabase("chagok.db");try(SQLiteDatabase sql=c.openOrCreateDatabase("chagok.db",0,null)){
   sql.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY NOT NULL,date TEXT NOT NULL,type TEXT NOT NULL,amount INTEGER NOT NULL,category TEXT NOT NULL,memo TEXT NOT NULL)");sql.execSQL("CREATE INDEX entries_date ON entries(date)");sql.execSQL("INSERT INTO entries(rowid,id,date,type,amount,category,memo) VALUES(77,'old','2026-02-01','지출',123,'식비','합성 원본')");
   if(version>=3){sql.execSQL("CREATE TABLE feature_state(key TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL)");sql.execSQL("INSERT INTO feature_state(rowid,key,value) VALUES(91,'unknown-preserve','opaque original')");}
   if(collide)sql.execSQL("CREATE INDEX entries_date_time ON feature_state(key)");sql.setVersion(version);
  }
 }
 static void migrations(DeviceChecks t)throws Exception{
  Context c=t.getTargetContext();for(int version=1;version<=4;version++){createLegacy(c,version,false);try(LedgerDb db=new LedgerDb(c)){t.eq(5,db.getReadableDatabase().getVersion());t.eq(1,db.all().size());Ledger.Entry e=db.all().get(0);t.eq("old",e.id);t.eq("2026-02-01",e.date);t.eq("00:00:00",e.time);t.eq(123L,e.amount);t.eq("합성 원본",e.memo);try(Cursor q=db.getReadableDatabase().rawQuery("SELECT rowid FROM entries",null)){q.moveToFirst();t.eq(77L,q.getLong(0));}if(version>=3)try(Cursor q=db.getReadableDatabase().rawQuery("SELECT rowid,value FROM feature_state WHERE key='unknown-preserve'",null)){t.eq(true,q.moveToFirst());t.eq(91L,q.getLong(0));t.eq("opaque original",q.getString(1));}}}
  createLegacy(c,4,true);boolean failed=false;try(LedgerDb db=new LedgerDb(c)){db.getWritableDatabase();}catch(SQLiteException expected){failed=true;}t.eq(true,failed);
  try(SQLiteDatabase sql=c.openOrCreateDatabase("chagok.db",0,null)){t.eq(4,sql.getVersion());try(Cursor q=sql.rawQuery("PRAGMA table_info(entries)",null)){t.eq(6,q.getCount());}try(Cursor q=sql.rawQuery("SELECT rowid,memo FROM entries",null)){q.moveToFirst();t.eq(77L,q.getLong(0));t.eq("합성 원본",q.getString(1));}}
 }
 static void clickDesc(DeviceChecks t,String desc){t.runOnMainSync(()->t.windowDesc(desc).performClick());t.waitForIdleSync();}
 static void day(DeviceChecks t,LocalDate date){t.runOnMainSync(()->((DatePicker)t.windowView(DatePicker.class)).updateDate(date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()));t.waitForIdleSync();}
 static void clock(DeviceChecks t,int h,int m,int s){t.runOnMainSync(()->{TimePicker time=(TimePicker)t.windowView(TimePicker.class);time.setHour(h);time.setMinute(m);});clickDesc(t,"기록 초 설정");t.runOnMainSync(()->((NumberPicker)t.windowDesc("기록 초 선택")).setValue(s));t.clickText("확인");t.waitForIdleSync();}
 static void invokeEntry(DeviceChecks t,MainActivity app,Ledger.Entry e){t.runOnMainSync(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("edit",Ledger.Entry.class);m.setAccessible(true);m.invoke(app,e);}catch(Exception x){throw new RuntimeException(x);}});t.waitForIdleSync();}
 static void fromTemplate(DeviceChecks t,MainActivity app,String method,Class<?> type,Object value)throws Exception{
  LocalTime before=LocalTime.now().withNano(0);t.runOnMainSync(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(method,type);m.setAccessible(true);m.invoke(app,value);}catch(Exception x){throw new RuntimeException(x);}});t.waitForIdleSync();String header=((TextView)t.windowDesc("새 기록 날짜 선택")).getText().toString();LocalTime actual=LocalTime.parse(header.substring(header.length()-8));t.eq(true,Duration.between(before,actual).getSeconds()>=0&&Duration.between(before,actual).getSeconds()<5);t.clickText("취소");t.waitForIdleSync();
 }
 static void run(DeviceChecks t)throws Exception{
  migrations(t);Context c=t.getTargetContext();for(String f:new String[]{"app_preferences","category_choices","backup_status"})c.getSharedPreferences(f,0).edit().clear().commit();try(LedgerDb db=AssetGuardChecks.fresh(c)){CategoryMigration.run(c,db);}
  boolean dark="timedark".equals(t.mode);new AppPreferences(c).dark(dark);MainActivity app=StartupChecks.start(t,false);
  LocalTime before=LocalTime.now().withNano(0);invokeEntry(t,app,null);String initial=((TextView)t.windowDesc("새 기록 날짜 선택")).getText().toString();LocalTime automatic=LocalTime.parse(initial.substring(initial.length()-8));t.eq(true,Duration.between(before,automatic).getSeconds()>=0&&Duration.between(before,automatic).getSeconds()<5);
  clickDesc(t,"새 기록 날짜 선택");t.eq(true,t.windowView(DatePicker.class)!=null);day(t,LocalDate.of(2025,2,3));t.clickText("취소");t.waitForIdleSync();t.eq(initial,((TextView)t.windowDesc("새 기록 날짜 선택")).getText().toString());
  clickDesc(t,"새 기록 날짜 선택");day(t,LocalDate.of(2026,6,27));t.screenshot(c,"record-calendar-"+dark);t.clickText("다음");t.waitForIdleSync();t.eq(true,t.windowView(TimePicker.class)!=null);clock(t,14,5,36);t.screenshot(c,"record-clock-"+dark);
  t.clickText("뒤로");t.waitForIdleSync();DatePicker calendar=(DatePicker)t.windowView(DatePicker.class);t.eq(27,calendar.getDayOfMonth());t.clickText("취소");t.waitForIdleSync();t.eq(initial,((TextView)t.windowDesc("새 기록 날짜 선택")).getText().toString());
  clickDesc(t,"새 기록 날짜 선택");day(t,LocalDate.of(2026,6,27));t.clickText("다음");t.waitForIdleSync();clock(t,14,5,36);t.clickText("확인");t.waitForIdleSync();t.eq(true,((TextView)t.windowDesc("새 기록 날짜 선택")).getText().toString().endsWith("14:05:36"));t.screenshot(c,"record-amount-time-"+dark);
  for(String key:new String[]{"1","00"})clickDesc(t,"키패드 "+key);t.clickText("적용");t.waitForIdleSync();t.eq(true,t.activeText("기록 시간 14:05:36"));t.runOnMainSync(()->{((EditText)t.windowDesc("새 기록 상세 분류")).setText("식비");((EditText)t.windowDesc("새 기록 메모")).setText("시간 저장 합성");});t.clickText("저장");t.waitForIdleSync();
  Ledger.Entry entry;try(LedgerDb db=new LedgerDb(c)){t.eq(1,db.all().size());entry=db.all().get(0);t.eq("2026-06-27",entry.date);t.eq("14:05:36",entry.time);t.eq(100L,entry.amount);}
  invokeEntry(t,app,entry);t.eq("14:05:36",((TextView)t.windowDesc("기록 시간 선택")).getText().toString());t.clickText("저장");t.waitForIdleSync();try(LedgerDb db=new LedgerDb(c)){t.eq(entry.fingerprint(),db.all().get(0).fingerprint());}
  invokeEntry(t,app,entry);clickDesc(t,"기록 시간 선택");t.clickText("다음");t.waitForIdleSync();clock(t,9,1,2);t.clickText("확인");t.waitForIdleSync();t.clickText("저장");t.waitForIdleSync();
  try(LedgerDb db=new LedgerDb(c)){t.eq("09:01:02",db.all().get(0).time);db.save(new Ledger.Entry("later",entry.date,"23:59:59","지출",50,"식비","시간 정렬 합성",0),false);t.eq("later",db.all().get(0).id);t.eq(-150L,Ledger.available(Ledger.totals(db.all())));}
  t.navigate(app,1);t.eq(true,t.findText(app.getWindow().getDecorView(),"23:59:59")!=null);t.eq(true,t.findText(app.getWindow().getDecorView(),"09:01:02")!=null);t.screenshot(c,"history-time-"+dark);
  fromTemplate(t,app,"copyEntry",Ledger.Entry.class,entry);
  fromTemplate(t,app,"useFavorite",PlanningData.Favorite.class,new PlanningData.Favorite("clock-favorite","합성 즐겨찾기","지출",50,"식비","합성"));
  fromTemplate(t,app,"confirmRepeat",PlanningData.Repeat.class,new PlanningData.Repeat("clock-repeat","합성 반복","지출",50,"식비","합성","MONTHLY",LocalDate.now(),LocalDate.now().getDayOfMonth(),1,true));
  StartupChecks.close(t,app);
  try(LedgerDb db=new LedgerDb(c)){
   db.save(new Ledger.Entry("principal","2026-01-01","저축",1000,"적금","합성",0),false);SavingsMovement m=new SavingsMovement("timed-move","2026-07-01","적금",1000,1100,600,"예금",400,"ETF","시간 합성",0,20,false,true).withTime("16:17:18");db.saveMovement(m,false);for(Ledger.Entry e:db.all())if(e.id.startsWith(m.prefix()))t.eq("16:17:18",e.time);
   ByteArrayOutputStream b=new ByteArrayOutputStream();BackupArchive.Snapshot original=db.snapshot();BackupArchive.write(b,original.entries,original.planning,original.movements,original.openingAssets);BackupArchive.Snapshot restored=BackupArchive.read(new ByteArrayInputStream(b.toByteArray()));t.eq(0,db.importPlanningAtomic(restored).additions.size());t.eq(0,db.importPlanningAtomic(restored).errors.size());
   db.saveMovement(m.withTime("19:20:21"),false);t.eq("19:20:21",db.movement(m.id).time);for(Ledger.Entry e:db.all())if(e.id.startsWith(m.prefix()))t.eq("19:20:21",e.time);
   // Invalid SQL times cannot be persisted, even outside the UI.
   boolean rejected=false;try{db.getWritableDatabase().execSQL("UPDATE entries SET time='24:00:00' WHERE id='later'");}catch(SQLiteException expected){rejected=true;}t.eq(true,rejected);t.eq("23:59:59",db.all().stream().filter(e->e.id.equals("later")).findFirst().get().time);
  }
 }
}
