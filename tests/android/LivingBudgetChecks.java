package kr.chagok.ledger;
import android.content.*;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.util.*;

final class LivingBudgetChecks {
 static Ledger.Entry e(String id,YearMonth m,String type,long amount,String category){return new Ledger.Entry(id,m.atDay(2).toString(),type,amount,category,"생활비 테스트",0);}
 static void editor(DeviceChecks t,MainActivity app,PlanningData.Limit limit)throws Exception{t.runOnMainSync(()->{try{DeviceChecks.invoke(app,"limitEditor",new Class[]{PlanningData.Limit.class,String.class},limit,"지출");}catch(Exception ex){throw new RuntimeException(ex);}});t.waitForIdleSync();}
 static void click(DeviceChecks t,String desc){t.runOnMainSync(()->t.windowDesc(desc).performClick());t.waitForIdleSync();}
 static String preview(DeviceChecks t){return ((TextView)t.windowDesc("생활비 계산 미리보기")).getText().toString();}
 static void chooseThree(DeviceChecks t){click(t,"생활비 분류 전체 해제");for(String name:new String[]{"식비","카페","쇼핑"})click(t,"생활비 반영 "+name);}
 static String rows(LedgerDb db){StringBuilder b=new StringBuilder();try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT rowid,id,date,type,amount,category,memo FROM entries ORDER BY rowid",null)){while(c.moveToNext())for(int i=0;i<7;i++)b.append(c.getString(i)).append('\n');}return b.toString();}
 static void run(DeviceChecks t)throws Exception{run(t,false);}
 static void run(DeviceChecks t,boolean dark)throws Exception{
  Context ctx=t.getTargetContext();ctx.getSharedPreferences("app_preferences",0).edit().clear().commit();ctx.getSharedPreferences("category_choices",0).edit().clear().commit();new AppPreferences(ctx).dark(dark);new AppPreferences(ctx).fontId(dark?"serif":"gothic");
  YearMonth month=YearMonth.now();PlanningData.Limit overall=new PlanningData.Limit("*","지출","",1000000);
  try(LedgerDb db=AssetGuardChecks.fresh(ctx)){
   db.importAtomic(Arrays.asList(e("food",month,"지출",200000,"식비"),e("cafe",month,"지출",50000,"카페"),e("shop",month,"지출",100000,"쇼핑"),e("trip",month,"지출",300000,"출장비"),e("old-food",month.minusMonths(1),"지출",1111,"식비"),e("old-trip",month.minusMonths(1),"지출",2222,"출장비"),e("saving",month,"저축",70000,"적금"),e("invest",month,"투자",80000,"주식")));
   PlanningData p=new PlanningData();p.put(overall);p.put(new PlanningData.Limit("*","지출","출장비",500000));p.put(new PlanningData.Limit("*","저축","",200000));db.savePlanning(p);
   MainActivity app=StartupChecks.start(t,false);String original=rows(db),originalPlan=db.planning().encode();
   editor(t,app,overall);t.eq(true,preview(t).contains("생활비 반영 지출  650,000원"));
   click(t,"생활비 분류 전체 해제");t.eq(true,preview(t).contains("생활비 반영 지출  0원"));t.eq(true,preview(t).contains("생활비 잔액  1,000,000원"));
   chooseThree(t);t.eq(true,preview(t).contains("전체 지출  650,000원"));t.eq(true,preview(t).contains("생활비 반영 지출  350,000원"));t.eq(true,preview(t).contains("생활비 잔액  650,000원"));t.eq(true,((TextView)t.windowDesc("생활비 선택 분류 개수")).getText().toString().startsWith("선택 3 /"));
   t.planField("금액 (원)","1200000");t.eq(true,preview(t).contains("생활비 잔액  850,000원"));t.planField("금액 (원)","1000000");
   t.screenshot(ctx,"living-selector-"+app.getResources().getConfiguration().fontScale);android.view.ViewParent parent=t.windowView(LivingBudgetSelector.class).getParent();while(!(parent instanceof ScrollView))parent=parent.getParent();final ScrollView outer=(ScrollView)parent;t.runOnMainSync(()->outer.fullScroll(android.view.View.FOCUS_DOWN));t.waitForIdleSync();t.screenshot(ctx,"living-selector-list-"+app.getResources().getConfiguration().fontScale);t.clickText("취소");t.waitForIdleSync();t.eq(originalPlan,db.planning().encode());t.eq(original,rows(db));
   editor(t,app,overall);chooseThree(t);t.clickText("저장");t.waitForIdleSync();p=db.planning();t.eq(true,p.hasLivingFilter());t.eq(new TreeSet<>(Arrays.asList("식비","카페","쇼핑")),p.livingCategories());t.eq(350000L,p.budgetProgress(overall,month,db.all()).actual);t.eq(650000L,p.budgetProgress(overall,month,db.all()).remaining);t.eq(original,rows(db));
   t.navigate(app,5);t.eq(true,t.findText(app.getWindow().getDecorView(),"전체 지출 650,000원 · 생활비 반영 350,000원")!=null);t.screenshot(ctx,"living-home-"+app.getResources().getConfiguration().fontScale);
   t.planSection(app,0);t.eq(true,t.findDesc(app.getWindow().getDecorView(),"지출 전체 예산 진행 350000 / 1000000원")!=null);t.eq(true,t.findDesc(app.getWindow().getDecorView(),"출장비 예산 진행 300000 / 500000원")!=null);t.screenshot(ctx,"living-budget-"+app.getResources().getConfiguration().fontScale);
   t.eq(650000L,StatisticsData.aggregate(Ledger.month(db.all(),month),"지출").total);t.eq(1111L,p.budgetProgress(overall,month.minusMonths(1),db.all()).actual);t.eq(70000L,p.budgetProgress(p.effective(month,"저축").get(0),month,db.all()).actual);
   try(LedgerDb reopened=new LedgerDb(ctx)){t.eq(p.livingCategories(),reopened.planning().livingCategories());}
   db.save(e("new-category",month,"지출",5000,"신규분류"),false);editor(t,app,overall);t.eq(false,((CheckBox)t.windowDesc("생활비 반영 신규분류")).isChecked());t.eq(true,preview(t).contains("생활비 반영 지출  350,000원"));click(t,"생활비 반영 신규분류");t.eq(true,preview(t).contains("생활비 반영 지출  355,000원"));t.clickText("저장");t.waitForIdleSync();t.eq(355000L,db.planning().budgetProgress(overall,month,db.all()).actual);
   new CategoryStore(ctx).delete("지출","카페");editor(t,app,overall);t.eq(true,((CheckBox)t.windowDesc("생활비 반영 카페")).isChecked());t.clickText("취소");t.waitForIdleSync();new CategoryStore(ctx).remember("지출","카페");t.eq(true,db.planning().livingCategories().contains("카페"));
   db.save(e("food",month,"지출",180000,"식비"),true);t.eq(335000L,db.planning().budgetProgress(overall,month,db.all()).actual);db.delete("cafe");t.eq(285000L,db.planning().budgetProgress(overall,month,db.all()).actual);t.eq(585000L,Ledger.totals(Ledger.month(db.all(),month))[1]);
   String beforeFail=db.planning().encode();db.getWritableDatabase().execSQL("CREATE TRIGGER living_fail BEFORE INSERT ON feature_state WHEN NEW.key='planning' BEGIN SELECT RAISE(ABORT,'forced failure'); END");
   try{PlanningData failed=db.planning();failed.selectLivingCategories(Collections.emptySet());failed.put(new PlanningData.Limit("*","지출","",999));db.savePlanning(failed);throw new AssertionError("Expected atomic save failure");}catch(android.database.sqlite.SQLiteException expected){t.checks++;}finally{db.getWritableDatabase().execSQL("DROP TRIGGER living_fail");}t.eq(beforeFail,db.planning().encode());
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();BackupArchive.Snapshot snapshot=db.snapshot();BackupArchive.write(bytes,snapshot.entries,snapshot.planning,snapshot.movements,snapshot.openingAssets);BackupArchive.Snapshot restored=BackupArchive.read(new ByteArrayInputStream(bytes.toByteArray()));t.eq(snapshot.planning.livingCategories(),restored.planning.livingCategories());
   db.getWritableDatabase().delete("entries",null,null);db.getWritableDatabase().delete("feature_state",null,null);Ledger.ImportPlan imported=db.importPlanningAtomic(restored);t.eq(0,imported.errors.size());t.eq(snapshot.entries.size(),db.all().size());t.eq(285000L,db.planning().budgetProgress(overall,month,db.all()).actual);t.eq(snapshot.planning.livingCategories(),db.planning().livingCategories());
   PlanningData different=PlanningData.decode(restored.planning.encode());different.selectLivingCategories(Collections.singleton("출장비"));db.importPlanningAtomic(new BackupArchive.Snapshot(restored.entries,different));t.eq(snapshot.planning.livingCategories(),db.planning().livingCategories());
   editor(t,app,overall);click(t,"생활비 분류 전체 선택");t.eq(true,preview(t).contains("생활비 반영 지출  585,000원"));click(t,"생활비 분류 전체 해제");t.clickText("저장");t.waitForIdleSync();t.eq(true,db.planning().hasLivingFilter());t.eq(0L,db.planning().budgetProgress(overall,month,db.all()).actual);try(LedgerDb reopened=new LedgerDb(ctx)){t.eq(0,reopened.planning().livingCategories().size());}
   t.eq(585000L,StatisticsData.aggregate(Ledger.month(db.all(),month),"지출").total);StartupChecks.close(t,app);
  }
 }
}
