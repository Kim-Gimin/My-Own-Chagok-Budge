package kr.chagok.ledger;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;

/** Destructive tests: test-android.ps1 restricts all modes to disposable emulators. */
final class ResetChecks {
    static void open(DeviceChecks t,MainActivity app)throws Exception {
        UserGuideChecks.click(t,app,"앱 전체 초기화(포맷)");t.eq(true,t.activeText("앱 전체 초기화"));
    }
    static void run(DeviceChecks t)throws Exception {
        Context ctx=t.getTargetContext();AppPreferences prefs=new AppPreferences(ctx);prefs.setPinHash("");
        try(LedgerDb db=new LedgerDb(ctx)){
            if(db.all().isEmpty())db.save(new Ledger.Entry("reset-sentinel","2026-11-17","수입",12345,"월급","초기화 테스트",0),false);
        }
        MainActivity app=(MainActivity)t.startActivitySync(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));t.waitForIdleSync();
        t.navigate(app,6);String before=AssetGuardChecks.snapshot(t);
        open(t,app);publicScreenshot(t,"reset-warning-"+app.getResources().getConfiguration().fontScale);
        t.clickText("취소");t.waitForIdleSync();t.eq(before,AssetGuardChecks.snapshot(t));
        open(t,app);t.clickText("먼저 백업");t.waitForIdleSync();t.eq(3,UserGuideChecks.get(app,"tab"));t.eq(before,AssetGuardChecks.snapshot(t));
        t.navigate(app,6);open(t,app);t.clickText("계속");t.waitForIdleSync();
        t.eq(true,t.activeText("모든 데이터를 삭제할까요?"));
        android.view.View finalButton=t.windowDesc("초기화 확인 문구");t.eq(true,finalButton instanceof EditText);
        t.planField("초기화 확인 문구","잘못된 확인");t.eq(false,positive().isEnabled());
        t.planField("초기화 확인 문구","초기화");t.eq(true,positive().isEnabled());
        t.planField("초기화 확인 문구","");t.eq(false,positive().isEnabled());
        t.clickText("취소");t.waitForIdleSync();t.eq(before,AssetGuardChecks.snapshot(t));
        // PIN is explicitly checked again, even though the app is already unlocked.
        prefs.setPinHash(PinCrypto.create("4321"));open(t,app);t.clickText("계속");t.waitForIdleSync();
        t.eq(true,t.activeText("초기화 전 비밀번호 확인"));
        t.planField("현재 비밀번호","1111");t.clickText("확인");Thread.sleep(700);t.waitForIdleSync();
        t.eq(true,t.activeText("초기화 전 비밀번호 확인"));t.eq(false,t.activeText("모든 데이터를 삭제할까요?"));
        t.planField("현재 비밀번호","4321");t.clickText("확인");Thread.sleep(700);t.waitForIdleSync();
        t.eq(true,t.activeText("모든 데이터를 삭제할까요?"));
        t.clickText("취소");t.waitForIdleSync();
        // Busy import/export operations cannot enter reset.
        t.runOnMainSync(()->{try{DeviceChecks.set(app,"busy",true);DeviceChecks.invoke(app,"confirmFullReset");DeviceChecks.set(app,"busy",false);}catch(Exception ex){throw new RuntimeException(ex);}});t.waitForIdleSync();
        t.eq(false,t.activeText("앱 전체 초기화"));
        // Make a real public-document backup. It must survive Android's data wipe.
        String name="chagok-reset-"+System.currentTimeMillis()+".xlsx";
        ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,name);values.put(MediaStore.Downloads.MIME_TYPE,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");values.put(MediaStore.Downloads.RELATIVE_PATH,"Download/ChagokTest/");
        Uri uri=ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);t.eq(true,uri!=null);
        t.runOnMainSync(()->app.onActivityResult(10,Activity.RESULT_OK,new Intent().setData(uri)));t.awaitIdle(app);
        byte[] backup;try(InputStream in=ctx.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);backup=out.toByteArray();
        }
        t.eq(true,XlsxIO.read(new ByteArrayInputStream(backup)).entries.size()>0);
        StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(backup))hash.append(String.format("%02x",b&255));
        try(FileOutputStream out=ctx.openFileOutput("reset-private-sentinel",Context.MODE_PRIVATE)){out.write(1);}
        try(FileOutputStream out=new FileOutputStream(new File(ctx.getCacheDir(),"reset-cache-sentinel"))){out.write(1);}
        ctx.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(BackupReminderService.JOB,new ComponentName(ctx,BackupReminderService.class)).setMinimumLatency(600000L).build());
        t.eq(true,ctx.getSystemService(JobScheduler.class).getPendingJob(BackupReminderService.JOB)!=null);
        prefs.dark(true);prefs.fontId("serif");new CategoryStore(ctx).remember("지출","초기화 분류");new BackupStatus(ctx).enabled(true);
        t.navigate(app,6);open(t,app);t.clickText("계속");t.waitForIdleSync();t.planField("현재 비밀번호","4321");t.clickText("확인");Thread.sleep(700);t.waitForIdleSync();
        t.planField("초기화 확인 문구","초기화");t.eq(true,positive().isEnabled());
        publicScreenshot(t,"reset-final-"+app.getResources().getConfiguration().fontScale);
        // Status is emitted before the expected OS process termination. The script then
        // verifies a new process, empty data and the public backup's exact hash.
        Bundle ready=new Bundle();ready.putString("reset","RESET_READY: "+t.checks+" checks; BACKUP_PATH: Download/ChagokTest/"+name+"; BACKUP_SHA256: "+hash);t.sendStatus(1,ready);
        t.runOnMainSync(()->positive().performClick());Thread.sleep(3000);
        throw new AssertionError("Android did not terminate the app after data reset");
    }
    static Button positive(){
        for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){
            android.view.View button=root.findViewById(android.R.id.button1);
            if(button instanceof Button&&"전체 삭제".contentEquals(((Button)button).getText()))return (Button)button;
        }throw new AssertionError("No final reset button");
    }
    static void publicScreenshot(DeviceChecks t,String name)throws Exception {
        ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,name+".png");values.put(MediaStore.Downloads.MIME_TYPE,"image/png");values.put(MediaStore.Downloads.RELATIVE_PATH,"Download/ChagokTest/");
        Context ctx=t.getTargetContext();Uri uri=ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        Thread.sleep(250);android.graphics.Bitmap bitmap=t.getUiAutomation().takeScreenshot();
        try(OutputStream out=ctx.getContentResolver().openOutputStream(uri)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    static void verify(DeviceChecks t)throws Exception {
        Context ctx=t.getTargetContext();t.eq("0.8.5",ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0).versionName);
        for(String name:new String[]{"app_preferences","category_choices","backup_status"})t.eq(true,ctx.getSharedPreferences(name,0).getAll().isEmpty());
        t.eq(false,new File(ctx.getFilesDir(),"reset-private-sentinel").exists());t.eq(false,new File(ctx.getCacheDir(),"reset-cache-sentinel").exists());
        t.eq(null,ctx.getSystemService(JobScheduler.class).getPendingJob(BackupReminderService.JOB));
        try(LedgerDb db=new LedgerDb(ctx)){
            t.eq(0,db.all().size());t.eq(0,db.openingAssets().size());t.eq(0,db.movements().size());
            try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT count(*) FROM feature_state",null)){c.moveToFirst();t.eq(0L,c.getLong(0));}t.eq(4,db.getReadableDatabase().getVersion());
        }
        AppPreferences prefs=new AppPreferences(ctx);t.eq(false,prefs.hasPin());t.eq(false,prefs.dark());t.eq("system",prefs.fontId());t.eq(2,prefs.startTab());
        Activity app=t.startActivitySync(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));t.waitForIdleSync();
        t.eq(true,t.findDesc(app.getWindow().getDecorView(),"통계 탭").isSelected());t.eq(false,t.activeText("차곡가계부 잠금"));
        t.screenshot(ctx,"reset-first-launch");t.runOnMainSync(()->app.finish());t.waitForIdleSync();
    }
}
