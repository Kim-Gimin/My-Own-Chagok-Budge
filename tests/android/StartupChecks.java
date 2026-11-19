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
 static String financial(DeviceChecks t)throws Exception{try(LedgerDb db=new LedgerDb(t.getTargetContext())){StringBuilder s=new StringBuilder();for(Ledger.Entry e:db.all())s.append(e.id).append(e.fingerprint());try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT rowid,key,value FROM feature_state ORDER BY rowid",null)){while(c.moveToNext())s.append(c.getLong(0)).append(c.getString(1)).append(c.getString(2));}return s.toString();}}
 static void run(DeviceChecks t)throws Exception{
  Context ctx=t.getTargetContext();AppPreferences prefs=new AppPreferences(ctx);prefs.setPinHash("");ctx.getSharedPreferences("app_preferences",0).edit().remove("start_tab").commit();String original=financial(t);t.eq(5,prefs.startTab());
  MainActivity app=start(t,false);t.eq(5,UserGuideChecks.get(app,"tab"));t.eq(true,t.findDesc(app.getWindow().getDecorView(),"홈 탭").isSelected());t.screenshot(ctx,"startup-default-home");
  for(int tab:new int[]{6,9}){t.navigate(app,tab);reopen(t);t.eq(5,UserGuideChecks.get(app,"tab"));t.eq(false,app.isFinishing());}
  for(int tab:new int[]{6,9}){t.navigate(app,tab);app=recreate(t,app);t.eq(5,UserGuideChecks.get(app,"tab"));}
  t.navigate(app,6);close(t,app);app=start(t,false);t.eq(5,UserGuideChecks.get(app,"tab"));
  prefs.startTab(0);close(t,app);app=start(t,false);t.eq(0,UserGuideChecks.get(app,"tab"));ctx.getSharedPreferences("app_preferences",0).edit().remove("start_tab").commit();
  prefs.startTab(6);t.eq(5,prefs.startTab());ctx.getSharedPreferences("app_preferences",0).edit().remove("start_tab").commit();
  t.navigate(app,3);reopen(t);t.eq(5,UserGuideChecks.get(app,"tab"));t.navigate(app,3);
  final MainActivity pickerApp=app;t.runOnMainSync(()->{try{DeviceChecks.invoke(pickerApp,"chooseImport");}catch(Exception ex){throw new RuntimeException(ex);}});Thread.sleep(600);BackNavigationChecks.back(t);t.eq(3,UserGuideChecks.get(app,"tab"));
  close(t,app);app=start(t,true);t.eq(3,UserGuideChecks.get(app,"tab"));
  prefs.setPinHash(PinCrypto.create("1234"));t.navigate(app,6);shell(t,"input keyevent 3");Thread.sleep(1500);shell(t,"am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n kr.chagok.ledger/.MainActivity");t.eq(5,UserGuideChecks.get(app,"tab"));t.eq(true,t.activeText("차곡가계부 잠금"));
  t.planField("잠금 비밀번호","1234");t.clickText("잠금 해제");t.awaitActiveText("차곡가계부 잠금",false);t.eq(5,UserGuideChecks.get(app,"tab"));prefs.setPinHash("");t.eq(original,financial(t));close(t,app);
 }
}
