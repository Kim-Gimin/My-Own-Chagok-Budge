package kr.chagok.ledger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

/** Navigation, readability, lifecycle and read-only checks on a disposable emulator. */
final class UserGuideChecks {
    static final String[] TITLES={"1. 처음 시작하기·기록 입력","2. 수입·지출·저축·투자·저축회수","3. 기초자산·현재 보유","4. 저축 해지·배분 사용하기","5. 가상 적금 해지 예시","6. 엑셀로 한 번에 입력하기","7. 백업·복원·기기 변경","8. 내역·통계·결산 보기","9. 예산·목표·반복·즐겨찾기","10. 분류·화면·글꼴·잠금 설정"};
    static void baseline(DeviceChecks t)throws Exception {
        Context ctx=t.getTargetContext();t.eq("0.8.7",ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0).versionName);
        // Rich synthetic fixture already covers rowids, V1/V2, opening, planning and preferences.
        try(LedgerDb db=AssetGuardChecks.fresh(ctx)){
            ctx.getSharedPreferences("app_preferences",0).edit().clear().commit();ctx.getSharedPreferences("category_choices",0).edit().clear().commit();
            db.saveOpening(AssetGuardChecks.basis("guide-basis",1000),false);
            db.save(new Ledger.Entry("guide-pay","2026-02-01","저축",200,"적금","원래 기록",0),false);
            db.saveMovement(new SavingsMovement("guide-move","2026-03-01","적금",1200,1300,1100,"예금",100,"주식","보존",1000,0,false,true),false);
            PlanningData plan=new PlanningData();plan.put(new PlanningData.Favorite("guide-favorite","저축","저축",100,"적금","보존"));plan.selectLivingCategories(java.util.Arrays.asList("식비","카페"));db.savePlanning(plan);
        }
        new AppPreferences(ctx).dark(true);new CategoryStore(ctx).remember("저축","보존 분류");
        ctx.getSharedPreferences("backup_status",0).edit().clear().putBoolean("notify",false).putLong("last_full",12345L).commit();
        try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(ctx.getExternalFilesDir(null),"guide-before.txt")),StandardCharsets.UTF_8)){out.write(AssetGuardChecks.snapshot(t));}
        t.eq(true,AssetGuardChecks.snapshot(t).contains("guide-move"));
    }
    static void upgraded(DeviceChecks t)throws Exception {
        Context ctx=t.getTargetContext();String before;
        try(InputStream in=new FileInputStream(new File(ctx.getExternalFilesDir(null),"guide-before.txt"));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);before=new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
        t.eq(before,AssetGuardChecks.snapshot(t));
        t.eq("0.8.8",ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0).versionName);
        t.eq(22,ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0).versionCode);
        try(LedgerDb db=new LedgerDb(ctx)){t.eq(4,db.getReadableDatabase().getVersion());t.eq(1,db.movements().size());t.eq(1,db.openingAssets().size());t.eq(1,db.planning().favorites.size());t.eq(true,db.planning().hasLivingFilter());t.eq(2,db.planning().livingCategories().size());}
    }
    static Object get(Object obj,String key)throws Exception{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(obj);}
    static void click(DeviceChecks t,MainActivity app,String description)throws Exception{
        View view=t.findDesc(app.getWindow().getDecorView(),description);t.eq(true,view!=null);
        t.runOnMainSync(()->view.performClick());t.waitForIdleSync();Thread.sleep(300);
    }
    static void layout(DeviceChecks t,View view){
        if(view instanceof TextView&&view.isShown()){
            TextView text=(TextView)view;if(text.getText().length()>0){t.eq(true,text.getWidth()>0);t.eq(0,text.getMaxLines()==1?1:0);
                if(text.getLayout()!=null){t.eq(true,text.getLayout().getLineCount()>0);t.eq(0,text.getLayout().getEllipsisCount(text.getLayout().getLineCount()-1));}
            }
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)layout(t,group.getChildAt(i));}
    }
    static void run(DeviceChecks t)throws Exception {
        Context ctx=t.getTargetContext();MainActivity app=(MainActivity)t.startActivitySync(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));t.waitForIdleSync();
        for(boolean dark:new boolean[]{false,true}){
            t.runOnMainSync(()->{try{new AppPreferences(ctx).dark(dark);new AppPreferences(ctx).fontId(dark?"serif":"gothic");t.invoke(app,"applyAppearance");t.set(app,"appFont",new AppPreferences(ctx).font());t.invoke(app,"navigate",new Class[]{int.class},0);}catch(Exception ex){throw new RuntimeException(ex);}});t.waitForIdleSync();
            String before=AssetGuardChecks.snapshot(t);
            click(t,app,"설정 열기");t.eq(6,get(app,"tab"));
            click(t,app,"설명서 보기");t.eq(9,get(app,"tab"));
            t.eq(true,t.findText(app.getWindow().getDecorView(),"설명서")!=null);
            t.eq(null,t.findDesc(app.getWindow().getDecorView(),"수입, 지출, 저축, 투자, 저축회수 기록 추가"));
            t.eq(null,t.findDesc(app.getWindow().getDecorView(),"내역 탭"));
            t.screenshot(ctx,"guide-index-"+dark+"-"+app.getResources().getConfiguration().fontScale);
            for(int i=0;i<TITLES.length;i++){
                click(t,app,TITLES[i]+" 펼치기");t.eq(i,get(app,"guideSection"));
                View button=t.findDesc(app.getWindow().getDecorView(),TITLES[i]+" 접기");t.eq(true,button.isSelected());
                layout(t,(View)get(app,"page"));
                if(i==4||i==5)t.screenshot(ctx,"guide-topic-"+i+"-"+dark+"-"+app.getResources().getConfiguration().fontScale);
            }
            click(t,app,TITLES[9]+" 접기");t.eq(-1,get(app,"guideSection"));
            click(t,app,TITLES[5]+" 펼치기");
            Bundle saved=new Bundle();t.runOnMainSync(()->app.onSaveInstanceState(saved));t.eq(9,saved.getInt("tab"));t.eq(5,saved.getInt("guideSection"));
            click(t,app,"설명서 뒤로");t.eq(6,get(app,"tab"));
            click(t,app,"설명서 보기");t.eq(true,t.findDesc(app.getWindow().getDecorView(),TITLES[5]+" 접기").isSelected());
            t.runOnMainSync(()->app.onBackPressed());t.waitForIdleSync();t.eq(6,get(app,"tab"));
            t.runOnMainSync(()->app.onBackPressed());t.waitForIdleSync();t.eq(5,get(app,"tab"));
            t.eq(before,AssetGuardChecks.snapshot(t));
        }
        t.runOnMainSync(()->app.finish());t.waitForIdleSync();
    }
}
