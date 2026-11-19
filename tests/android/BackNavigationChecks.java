package kr.chagok.ledger;

import android.app.*;
import android.content.*;
import android.os.ParcelFileDescriptor;
import android.view.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Uses OS input, so API33+ dispatcher regressions cannot hide behind direct method calls. */
final class BackNavigationChecks {
    static void back(DeviceChecks t)throws Exception {
        try(ParcelFileDescriptor fd=t.getUiAutomation().executeShellCommand("input keyevent 4");InputStream in=new FileInputStream(fd.getFileDescriptor())){byte[] b=new byte[256];while(in.read(b)!=-1){}}
        t.waitForIdleSync();Thread.sleep(350);
    }
    static MainActivity start(DeviceChecks t)throws Exception {
        MainActivity app=(MainActivity)t.startActivitySync(new Intent(t.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));t.waitForIdleSync();return app;
    }
    static void before(DeviceChecks t)throws Exception {
        UserGuideChecks.baseline(t);MainActivity app=start(t);t.navigate(app,2);
        UserGuideChecks.click(t,app,"설정 열기");back(t);
        t.eq(true,app.isFinishing()||!Integer.valueOf(5).equals(UserGuideChecks.get(app,"tab")));
        t.runOnMainSync(()->app.finish());t.waitForIdleSync();
        try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(t.getTargetContext().getExternalFilesDir(null),"guide-before.txt")),StandardCharsets.UTF_8)){out.write(AssetGuardChecks.snapshot(t));}
    }
    static void run(DeviceChecks t)throws Exception {
        Context ctx=t.getTargetContext();new AppPreferences(ctx).setPinHash("");
        for(boolean dark:new boolean[]{false,true}){
            new AppPreferences(ctx).dark(dark);new AppPreferences(ctx).fontId(dark?"serif":"gothic");
            MainActivity app=start(t);String initial=AssetGuardChecks.snapshot(t);
            for(int source:new int[]{0,1,2,3,4,5,7,8}){
                t.navigate(app,source);UserGuideChecks.click(t,app,"설정 열기");UserGuideChecks.click(t,app,"설정 뒤로");
                t.eq(5,UserGuideChecks.get(app,"tab"));t.eq(false,app.isFinishing());
                t.navigate(app,source);UserGuideChecks.click(t,app,"설정 열기");back(t);
                t.eq(5,UserGuideChecks.get(app,"tab"));t.eq(false,app.isFinishing());
            }
            t.navigate(app,6);UserGuideChecks.click(t,app,"설명서 보기");back(t);t.eq(6,UserGuideChecks.get(app,"tab"));back(t);t.eq(5,UserGuideChecks.get(app,"tab"));
            for(int source:new int[]{0,1,2,3,4,7,8}){t.navigate(app,source);back(t);t.eq(5,UserGuideChecks.get(app,"tab"));t.eq(null,UserGuideChecks.get(app,"exitDialog"));}
            back(t);t.eq(false,app.isFinishing());t.eq(true,t.activeText("알림"));t.eq(true,t.activeText("앱 종료"));
            t.eq(true,UserGuideChecks.get(app,"exitDialog")!=null);
            t.screenshot(ctx,"exit-dialog-"+dark+"-"+app.getResources().getConfiguration().fontScale);
            t.clickText("취소");t.waitForIdleSync();t.eq(false,app.isFinishing());t.eq(null,UserGuideChecks.get(app,"exitDialog"));
            // Navigation and closing a record editor must not arm app exit.
            UserGuideChecks.click(t,app,"수입, 지출, 저축, 투자, 저축회수 기록 추가");t.clickText("취소");t.waitForIdleSync();t.eq(null,UserGuideChecks.get(app,"exitDialog"));
            back(t);t.eq(true,t.activeText("알림"));t.clickText("취소");t.waitForIdleSync();
            t.runOnMainSync(()->{try{DeviceChecks.set(app,"busy",true);app.onBackPressed();DeviceChecks.set(app,"busy",false);}catch(Exception ex){throw new RuntimeException(ex);}});t.waitForIdleSync();t.eq(null,UserGuideChecks.get(app,"exitDialog"));Thread.sleep(2500);
            back(t);t.eq(true,t.activeText("알림"));
            t.runOnMainSync(()->app.onStop());t.waitForIdleSync();t.eq(null,UserGuideChecks.get(app,"exitDialog"));t.runOnMainSync(()->app.onResume());t.waitForIdleSync();
            back(t);t.eq(true,t.activeText("알림"));t.eq(false,app.isFinishing());
            t.eq(initial,AssetGuardChecks.snapshot(t));
            if(dark)back(t);else{t.clickText("앱 종료");t.waitForIdleSync();}
            t.eq(true,app.isFinishing());t.eq(initial,AssetGuardChecks.snapshot(t));
            Thread.sleep(500);
        }
        // The legacy override follows the same home/confirmation route.
        MainActivity app=start(t);t.navigate(app,6);t.runOnMainSync(()->app.onBackPressed());t.waitForIdleSync();t.eq(5,UserGuideChecks.get(app,"tab"));
        t.runOnMainSync(()->app.onBackPressed());t.waitForIdleSync();t.eq(true,t.activeText("알림"));t.clickText("취소");t.runOnMainSync(()->app.finish());t.waitForIdleSync();
    }
}
