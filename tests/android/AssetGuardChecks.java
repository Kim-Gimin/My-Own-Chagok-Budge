package kr.chagok.ledger;
import android.content.Context;
import java.util.*;

/** Regression cases use only the disposable emulator's synthetic ledger. */
final class AssetGuardChecks {
 static String snapshot(DeviceChecks t)throws Exception{return t.recoveryUpgradeSnapshot()+"\nBACKUP_PREFS:"+new TreeMap<>(t.getTargetContext().getSharedPreferences("backup_status",0).getAll());}
 static void baseline(DeviceChecks t)throws Exception{
  Context ctx=t.getTargetContext();t.eq("0.8.3",ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0).versionName);
  ctx.getSharedPreferences("app_preferences",0).edit().clear().commit();ctx.getSharedPreferences("category_choices",0).edit().clear().commit();ctx.getSharedPreferences("backup_status",0).edit().clear().putBoolean("notify",false).putLong("last_full",12345L).commit();
  try(LedgerDb db=fresh(ctx)){
   db.saveOpening(basis("base",1000),false);db.saveOpening(new OpeningAsset("cash","2026-01-01","현금",200,"기존 현금",""),false);
   db.save(new Ledger.Entry("original","2026-02-01","저축",200,"적금","원래 납입",0),false);
   db.saveMovement(new SavingsMovement("detailed","2026-03-01","적금",1200,1300,1100,"예금",100,"주식","이전 데이터",1000,0,false,true),false);
   db.saveMovement(new SavingsMovement("legacy","2026-04-01","예금",1100,1110,1000,"다른 예금",100,"주식","기존 순액"),false);
   PlanningData plan=new PlanningData();plan.put(new PlanningData.Favorite("favorite","저축 틀","저축",100,"적금",""));plan.put(new PlanningData.Repeat("repeat","월 납입","저축",100,"적금","","MONTHLY",java.time.LocalDate.of(2027,1,31),31,1,true));db.savePlanning(plan);t.eq(8,db.all().size());t.eq(2,db.openingAssets().size());t.eq(2,db.movements().size());
  }
  new AppPreferences(ctx).dark(true);new CategoryStore(ctx).remember("저축","검증 분류");
  try(java.io.Writer out=new java.io.OutputStreamWriter(new java.io.FileOutputStream(new java.io.File(ctx.getExternalFilesDir(null),"asset-upgrade-before.txt")),java.nio.charset.StandardCharsets.UTF_8)){out.write(snapshot(t));}
 }
 static void upgraded(DeviceChecks t)throws Exception{
  Context ctx=t.getTargetContext();String before;try(java.io.InputStream in=new java.io.FileInputStream(new java.io.File(ctx.getExternalFilesDir(null),"asset-upgrade-before.txt"));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);before=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}
  t.eq(before,snapshot(t));t.eq("0.8.4",ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0).versionName);t.eq(18,ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0).versionCode);
  try(LedgerDb db=new LedgerDb(ctx)){t.eq(4,db.getReadableDatabase().getVersion());t.eq(8,db.all().size());t.eq(2,db.movements().size());Map<String,Long> balances=OpeningAsset.balances(db.openingAssets(),db.all(),"2026-12-31",db.movements());t.eq(0L,balances.get("저축 · 적금"));t.eq(0L,balances.get("저축 · 예금"));t.eq(1000L,balances.get("저축 · 다른 예금"));t.eq(110L,balances.get("현금 · 거래 후 잔액"));}
 }
 static LedgerDb fresh(Context ctx){ctx.deleteDatabase("chagok.db");return new LedgerDb(ctx);}
 static OpeningAsset basis(String id,long amount){return new OpeningAsset(id,"2026-01-01","저축",amount,"적금",id);}
 static SavingsMovement move(String id,String date,String source,long p,String target,long prior){return new SavingsMovement(id,date,source,p,p,p,target,0,"주식","",prior,0,false,true);}
 interface Action{void run();}
 static void blocked(DeviceChecks t,boolean strict,Action action){boolean rejected=false;try{action.run();}catch(IllegalArgumentException ex){rejected=true;}t.eq(strict,rejected);}
 static void run(DeviceChecks t,boolean strict)throws Exception{
  Context ctx=t.getTargetContext();
  try(LedgerDb db=fresh(ctx)){
   OpeningAsset a=basis("a",1000),b=basis("b",2000);db.saveOpening(a,false);db.saveOpening(b,false);
   blocked(t,strict,()->db.saveOpening(new OpeningAsset(b.id,a.date,a.kind,a.amount,a.category,a.memo),true));
   if(strict){t.eq(3000L,OpeningAsset.balances(db.openingAssets(),db.all(),"2026-12-31").get("저축 · 적금"));}
  }
  try(LedgerDb db=fresh(ctx)){
   db.saveOpening(basis("base",900),false);db.save(new Ledger.Entry("paid","2026-02-01","저축",100,"적금","",0),false);
   SavingsMovement first=move("first","2026-03-01","적금",1000,"예금",900);db.saveMovement(first,false);
   String before=t.recoveryUpgradeSnapshot();blocked(t,strict,()->db.deleteOpening("base"));if(strict)t.eq(before,t.recoveryUpgradeSnapshot());
  }
  try(LedgerDb db=fresh(ctx)){
   OpeningAsset a=basis("base",900);db.saveOpening(a,false);db.save(new Ledger.Entry("paid","2026-02-01","저축",100,"적금","",0),false);db.saveMovement(move("first","2026-03-01","적금",1000,"예금",900),false);
   String before=t.recoveryUpgradeSnapshot();blocked(t,strict,()->db.saveOpening(new OpeningAsset(a.id,a.date,a.kind,800,a.category,a.memo),true));if(strict)t.eq(before,t.recoveryUpgradeSnapshot());
  }
  try(LedgerDb db=fresh(ctx)){
   db.saveOpening(basis("base",1000),false);db.saveMovement(move("first","2026-03-01","적금",1000,"예금",1000),false);
   String before=t.recoveryUpgradeSnapshot();blocked(t,strict,()->db.saveMovement(move("second","2026-03-02","적금",1000,"다른 예금",1000),false));if(strict)t.eq(before,t.recoveryUpgradeSnapshot());
  }
  try(LedgerDb db=fresh(ctx)){
   db.saveOpening(new OpeningAsset("base","2026-07-01","저축",1000,"적금",""),false);
   blocked(t,strict,()->db.saveMovement(move("earlier","2026-06-01","적금",1000,"예금",0),false));if(strict)t.eq(0,db.movements().size());
  }
  try(LedgerDb db=fresh(ctx)){
   db.saveOpening(basis("base",1000),false);SavingsMovement first=move("first","2026-03-01","적금",1000,"예금",1000);db.saveMovement(first,false);db.saveMovement(move("second","2026-04-01","예금",1000,"다른 예금",0),false);
   String before=t.recoveryUpgradeSnapshot();blocked(t,strict,()->db.saveMovement(first,true));if(strict)t.eq(before,t.recoveryUpgradeSnapshot());
  }
  try(LedgerDb db=fresh(ctx)){
   db.saveOpening(basis("base",900),false);Ledger.Entry paid=new Ledger.Entry("paid","2026-02-01","저축",100,"적금","",0);db.save(paid,false);db.saveMovement(move("first","2026-03-01","적금",1000,"예금",900),false);
   String before=t.recoveryUpgradeSnapshot();blocked(t,strict,()->db.save(new Ledger.Entry(paid.id,paid.date,paid.type,50,paid.category,paid.memo,0),true));if(strict)t.eq(before,t.recoveryUpgradeSnapshot());
  }
  try(LedgerDb db=fresh(ctx)){
   SavingsMovement m=move("missing-basis","2026-03-01","적금",1000,"예금",900);
   boolean rejected=false;try{Ledger.ImportPlan plan=db.importPlanningAtomic(new BackupArchive.Snapshot(m.entries(),new PlanningData(),Arrays.asList(m),Collections.emptyList()));rejected=!plan.errors.isEmpty();}catch(IllegalArgumentException ex){rejected=true;}t.eq(strict,rejected);if(strict){t.eq(0,db.all().size());t.eq(0,db.movements().size());}
  }
  if(strict){
   try(LedgerDb db=fresh(ctx)){
    blocked(t,true,()->db.saveMovement(move("self","2026-03-01","적금",1000,"적금",0),false));t.eq(0,db.movements().size());
   }
   try(LedgerDb db=fresh(ctx)){
    OpeningAsset b=basis("base",1000);db.saveOpening(b,false);db.saveMovement(move("first","2026-03-01","적금",1000,"예금",1000),false);String before=t.recoveryUpgradeSnapshot();
    blocked(t,true,()->db.saveOpening(new OpeningAsset(b.id,"2026-12-01",b.kind,b.amount,b.category,b.memo),true));t.eq(before,t.recoveryUpgradeSnapshot());
   }
   try(LedgerDb db=fresh(ctx)){
    db.saveOpening(basis("base",1000),false);SavingsMovement first=move("first","2026-03-01","적금",1000,"예금",1000);db.saveMovement(first,false);db.saveMovement(move("second","2026-03-01","예금",1000,"다른 예금",0),false);db.saveMovement(first,false);t.eq("first",db.movements().get(0).id);t.eq(2,db.movements().size());
   }
   try(LedgerDb db=fresh(ctx)){
    db.saveOpening(basis("base",2000),false);db.saveMovement(move("first","2026-03-01","적금",1000,"예금A",1000),false);db.saveMovement(move("second","2026-03-01","적금",1000,"예금B",1000),false);t.eq(2,db.movements().size());t.eq(0L,OpeningAsset.balances(db.openingAssets(),db.all(),"2026-03-01").get("저축 · 적금"));
   }
   try(LedgerDb db=fresh(ctx)){
    db.saveOpening(basis("base",1000),false);SavingsMovement first=move("first","2026-03-01","적금",1000,"예금",1000);db.saveMovement(first,false);db.saveMovement(move("second","2026-04-01","예금",1000,"다른 예금",0),false);
    t.eq(0L,OpeningAsset.balances(db.openingAssets(),db.all(),"2026-12-31").get("저축 · 예금"));
    Ledger.ImportPlan plan=db.importAtomic(Arrays.asList(new Ledger.Entry("extra-recovery","2026-05-01","저축회수",1,"적금","",0)));t.eq(true,!plan.errors.isEmpty());t.eq(false,db.all().stream().anyMatch(e->e.id.equals("extra-recovery")));
   }
   MainActivity app=(MainActivity)t.startActivitySync(new android.content.Intent(ctx,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));t.waitForIdleSync();String before=t.recoveryUpgradeSnapshot();OpeningAsset asset;try(LedgerDb db=new LedgerDb(ctx)){asset=db.openingAssets().get(0);}
   t.runOnMainSync(()->{try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("openingEditor",OpeningAsset.class);method.setAccessible(true);method.invoke(app,asset);}catch(Exception ex){throw new RuntimeException(ex);}});t.waitForIdleSync();t.clickText("삭제");t.waitForIdleSync();t.clickText("삭제");t.waitForIdleSync();t.eq(true,t.activeText("기초자산을 삭제하지 못했어요"));t.eq(before,t.recoveryUpgradeSnapshot());t.screenshot(ctx,"assetguard-deletion-blocked");t.clickText("확인");t.waitForIdleSync();t.clickText("취소");t.runOnMainSync(()->app.finish());t.waitForIdleSync();
  }
 }
}
