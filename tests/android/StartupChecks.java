package kr.chagok.ledger;
import android.app.*;
import android.content.*;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class StartupChecks {
 static void shell(DeviceChecks t,String command)throws Exception{try(ParcelFileDescriptor fd=t.getUiAutomation().executeShellCommand(command);InputStream in=new FileInputStream(fd.getFileDescriptor())){byte[] b=new byte[256];while(in.read(b)!=-1){}}t.waitForIdleSync();Thread.sleep(400);}
 static void reopen(DeviceChecks t)throws Exception{shell(t,"input keyevent 3");shell(t,"am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n kr.chagok.ledger/.MainActivity");}
 static MainActivity start(DeviceChecks t,boolean backup)throws Exception{MainActivity app=(MainActivity)t.startActivitySync(new Intent(Intent.ACTION_MAIN).setClass(t.getTargetContext(),MainActivity.class).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED).putExtra("open_backup",backup));t.waitForIdleSync();return app;}
 static void close(DeviceChecks t,Activity app){t.runOnMainSync(()->app.finish());t.waitForIdleSync();}
 static MainActivity recreate(DeviceChecks t,MainActivity app)throws Exception{Instrumentation.ActivityMonitor monitor=t.addMonitor(MainActivity.class.getName(),null,false);t.runOnMainSync(()->app.recreate());Activity result=monitor.waitForActivityWithTimeout(8000);t.removeMonitor(monitor);t.eq(true,result instanceof MainActivity);t.waitForIdleSync();return (MainActivity)result;}
 static void before(DeviceChecks t)throws Exception{
  UserGuideChecks.baseline(t);MainActivity app=start(t,false);t.eq(2,UserGuideChecks.get(app,"tab"));t.navigate(app,6);reopen(t);t.eq(true,app.hasWindowFocus());t.eq(6,UserGuideChecks.get(app,"tab"));close(t,app);
  try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(t.getTargetContext().getExternalFilesDir(null),"guide-before.txt")),StandardCharsets.UTF_8)){out.write(AssetGuardChecks.snapshot(t));}
 }
 static String financial(DeviceChecks t)throws Exception{try(LedgerDb db=new LedgerDb(t.getTargetContext())){StringBuilder s=new StringBuilder();for(Ledger.Entry e:db.all())s.append(e.id).append(e.fingerprint());try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT rowid,key,value FROM feature_state WHERE key NOT IN ('category_cleanup_089','category_cleanup_089_journal') ORDER BY rowid",null)){while(c.moveToNext())s.append(c.getLong(0)).append(c.getString(1)).append(c.getString(2));}return s.toString();}}
 static void run(DeviceChecks t)throws Exception{
  Context ctx=t.getTargetContext();AppPreferences prefs=new AppPreferences(ctx);prefs.setPinHash("");ctx.getSharedPreferences("app_preferences",0).edit().remove("start_tab").commit();String original=financial(t);t.eq(5,prefs.startTab());
  MainActivity app=start(t,false);t.eq(5,UserGuideChecks.get(app,"tab"));t.eq(true,t.findDesc(app.getWindow().getDecorView(),"홈 탭").isSelected());t.screenshot(ctx,"startup-default-home");
  for(int tab:new int[]{0,1,2,3,4,5,6,7,8,9}){t.navigate(app,tab);Object root=UserGuideChecks.get(app,"root");reopen(t);t.eq(tab,UserGuideChecks.get(app,"tab"));t.eq(true,root==UserGuideChecks.get(app,"root"));t.eq(false,app.isFinishing());}
  for(int tab:new int[]{6,9}){t.navigate(app,tab);app=recreate(t,app);t.eq(tab,UserGuideChecks.get(app,"tab"));}
  // Scroll and history paging survive both a short switch and Activity recreation.
  t.navigate(app,6);final MainActivity scrolling=app;android.widget.ScrollView oldScroll=(android.widget.ScrollView)UserGuideChecks.get(app,"pageScroll");
  t.runOnMainSync(()->{oldScroll.scrollTo(0,oldScroll.getChildAt(0).getHeight());try{DeviceChecks.set(scrolling,"displayLimit",300);}catch(Exception ex){throw new RuntimeException(ex);}});t.waitForIdleSync();int y=oldScroll.getScrollY();
  reopen(t);t.eq(true,oldScroll==UserGuideChecks.get(app,"pageScroll"));t.eq(y,oldScroll.getScrollY());
  app=recreate(t,app);t.waitForIdleSync();t.eq(6,UserGuideChecks.get(app,"tab"));t.eq(y,((android.widget.ScrollView)UserGuideChecks.get(app,"pageScroll")).getScrollY());t.eq(300,UserGuideChecks.get(app,"displayLimit"));
  // Unsaved amount and memo stay in their live dialogs, including behind the PIN gate.
  t.navigate(app,1);UserGuideChecks.click(t,app,"수입, 지출, 저축, 투자, 저축회수 기록 추가");
  for(String digit:new String[]{"1","2","3"})t.runOnMainSync(()->t.windowDesc("키패드 "+digit).performClick());t.waitForIdleSync();
  Object draftRoot=UserGuideChecks.get(app,"root");reopen(t);t.eq(1,UserGuideChecks.get(app,"tab"));t.eq(true,draftRoot==UserGuideChecks.get(app,"root"));t.eq(true,t.activeText("123 원"));
  prefs.setPinHash(PinCrypto.create("1234"));reopen(t);t.eq(true,t.activeText("차곡가계부 잠금"));t.planField("잠금 비밀번호","1234");t.clickText("잠금 해제");t.awaitActiveText("차곡가계부 잠금",false);
  t.eq(1,UserGuideChecks.get(app,"tab"));t.eq(true,draftRoot==UserGuideChecks.get(app,"root"));t.eq(true,t.activeText("123 원"));prefs.setPinHash("");
  t.clickText("적용");t.waitForIdleSync();t.planField("새 기록 메모","가상 임시 메모 유지");reopen(t);t.eq(true,t.activeText("새 기록"));t.eq(true,t.activeText("가상 임시 메모 유지"));t.clickText("취소");t.waitForIdleSync();
  t.navigate(app,6);close(t,app);app=start(t,false);t.eq(5,UserGuideChecks.get(app,"tab"));
  prefs.startTab(0);close(t,app);app=start(t,false);t.eq(0,UserGuideChecks.get(app,"tab"));ctx.getSharedPreferences("app_preferences",0).edit().remove("start_tab").commit();
  prefs.startTab(6);t.eq(5,prefs.startTab());ctx.getSharedPreferences("app_preferences",0).edit().remove("start_tab").commit();
  t.navigate(app,3);reopen(t);t.eq(3,UserGuideChecks.get(app,"tab"));t.navigate(app,3);
  final MainActivity pickerApp=app;t.runOnMainSync(()->{try{DeviceChecks.invoke(pickerApp,"chooseImport");}catch(Exception ex){throw new RuntimeException(ex);}});Thread.sleep(600);BackNavigationChecks.back(t);t.eq(3,UserGuideChecks.get(app,"tab"));
  close(t,app);app=start(t,true);t.eq(3,UserGuideChecks.get(app,"tab"));t.navigate(app,1);app=recreate(t,app);t.eq(1,UserGuideChecks.get(app,"tab"));
  prefs.setPinHash(PinCrypto.create("1234"));t.navigate(app,6);shell(t,"input keyevent 3");Thread.sleep(1500);shell(t,"am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n kr.chagok.ledger/.MainActivity");t.eq(6,UserGuideChecks.get(app,"tab"));t.eq(true,t.activeText("차곡가계부 잠금"));
  t.planField("잠금 비밀번호","1234");t.clickText("잠금 해제");t.awaitActiveText("차곡가계부 잠금",false);t.eq(6,UserGuideChecks.get(app,"tab"));prefs.setPinHash("");t.eq(original,financial(t));close(t,app);
 }
}
