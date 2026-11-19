package kr.chagok.ledger;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;

public class DeviceChecks extends Instrumentation {
    int checks;
    String mode;
    void eq(Object a,Object b){checks++;if(!Objects.equals(a,b))throw new AssertionError("Expected "+a+", actual "+b);}
    @Override public void onCreate(Bundle args){super.onCreate(args);mode=args==null?"":args.getString("mode","");start();}
    @Override public void onStart(){
        Bundle out=new Bundle();
        try{if("upgrade".equals(mode))runUpgradeChecks();else if("preview".equals(mode))statisticsPreview();else runChecks();out.putString("result","PASS: "+checks+" Android integration checks");finish(Activity.RESULT_OK,out);}
        catch(Throwable e){StringWriter sw=new StringWriter();e.printStackTrace(new PrintWriter(sw));out.putString("error",sw.toString());finish(Activity.RESULT_CANCELED,out);}
    }
    void runUpgradeChecks()throws Exception {
        Context ctx=getTargetContext(); LedgerDb db=new LedgerDb(ctx);
        eq(5,db.all().size());eq(1976000L,Ledger.available(Ledger.totals(db.all())));db.close();
        eq("0.2.0",ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0).versionName);
        eq(true,new CategoryStore(ctx).choices("지출",Collections.emptyList()).contains("반려동물 병원"));
    }
    void runChecks()throws Exception {
        Context ctx=getTargetContext();ctx.deleteDatabase("chagok.db");LedgerDb db=new LedgerDb(ctx);
        ctx.getSharedPreferences("category_choices",Context.MODE_PRIVATE).edit().clear().commit();
        List<Ledger.Entry> seed=Arrays.asList(new Ledger.Entry("i","2026-10-01","수입",3000000,"월급","10월 급여",2),new Ledger.Entry("e","2026-10-27","지출",12500,"식비","점심 식사",3),new Ledger.Entry("s","2026-10-05","저축",1000000,"적금","차곡차곡 모으기",4));
        Ledger.ImportPlan p=db.importAtomic(seed);eq(3,p.additions.size());eq(3,db.all().size());
        p=db.importAtomic(seed);eq(3,p.duplicates);eq(3,db.all().size());
        p=db.importAtomic(Arrays.asList(new Ledger.Entry("new","2026-10-27","지출",20,"임시","",2),new Ledger.Entry("i","2026-10-01","수입",2,"월급","변경",3)));eq(1,p.errors.size());eq(3,db.all().size());
        db.save(new Ledger.Entry("temp","2026-10-27","지출",20,"임시","",0),false);eq(4,db.all().size());db.delete("temp");eq(3,db.all().size());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();XlsxIO.write(bytes,db.all(),false);XlsxIO.ReadResult r=XlsxIO.read(new ByteArrayInputStream(bytes.toByteArray()));eq(3,r.entries.size());eq(0,r.invalidRows);eq(1987500L,Ledger.available(Ledger.totals(r.entries)));
        try(InputStream sample=getContext().getAssets().open("example.xlsx")){r=XlsxIO.read(sample);eq(3,r.entries.size());eq(0,r.invalidRows);eq(1000000L,Ledger.totals(r.entries)[2]);}
        db.close();db=new LedgerDb(ctx);eq(3,db.all().size());db.close();
        CategoryStore categories=new CategoryStore(ctx);
        eq(true,categories.choices("지출",seed).contains("의료·건강"));eq(true,categories.choices("수입",seed).contains("부수입"));
        eq(true,categories.remember("지출","  반려동물  "));eq(false,categories.remember("지출","반려동물"));
        eq(true,new CategoryStore(ctx).choices("지출",seed).contains("반려동물"));
        eq(false,categories.choices("수입",seed).contains("반려동물"));
        eq(true,categories.choices("지출",Arrays.asList(new Ledger.Entry("import","2026-10-27","지출",10,"엑셀 분류","",2))).contains("엑셀 분류"));
        try {categories.remember("저축","   ");throw new AssertionError("Blank category accepted");}catch(IllegalArgumentException expected){checks++;}
        try {categories.remember("저축",String.join("",Collections.nCopies(41,"가")));throw new AssertionError("Long category accepted");}catch(IllegalArgumentException expected){checks++;}
        Activity a=startActivitySync(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        eq(true,findDesc(a.getWindow().getDecorView(),"통계 탭").isSelected());eq(true,findDesc(a.getWindow().getDecorView(),"지출 통계").isSelected());
        runOnMainSync(()->{try{set(a,"tab",0);set(a,"month",YearMonth.of(2026,10));set(a,"selected",LocalDate.of(2026,10,27));invoke(a,"render");}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        eq(true,findText(a.getWindow().getDecorView(),"1,987,500원")!=null);
        screenshot(ctx,"calendar");
        runOnMainSync(()->findDesc(a.getWindow().getDecorView(),"수입, 지출, 저축 기록 추가").performClick());waitForIdleSync();Thread.sleep(350);
        List<AccessibilityNodeInfo> edits=new ArrayList<>();collectEdits(getUiAutomation().getRootInActiveWindow(),edits);eq(4,edits.size());
        setText(edits.get(0),"25000");setText(edits.get(2),"카페");setText(edits.get(3),"기록 저장 테스트");
        screenshot(ctx,"entry");clickText("저장");waitForIdleSync();Thread.sleep(350);
        LedgerDb verify=new LedgerDb(ctx);eq(4,verify.all().size());eq(1962500L,Ledger.available(Ledger.totals(verify.all())));verify.close();
        eq(true,findText(a.getWindow().getDecorView(),"1,962,500원")!=null);
        Thread.sleep(2200);
        for(String tab:new String[]{"내역","통계","엑셀"}){runOnMainSync(()->findDesc(a.getWindow().getDecorView(),tab+" 탭").performClick());waitForIdleSync();Thread.sleep(200);screenshot(ctx,tab.equals("내역")?"records":tab.equals("통계")?"statistics":"excel");}
        eq(true,findText(a.getWindow().getDecorView(),"전체 내역 백업")!=null);
        MainActivity app=(MainActivity)a;
        File backup=new File(ctx.getExternalFilesDir(null),"actual-backup.xlsx");
        runOnMainSync(()->app.onActivityResult(10,Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(backup))));
        awaitIdle(app);
        try(InputStream in=new FileInputStream(backup)){r=XlsxIO.read(in);eq(4,r.entries.size());eq(0,r.invalidRows);}
        File blank=new File(ctx.getExternalFilesDir(null),"actual-template.xlsx");
        runOnMainSync(()->app.onActivityResult(11,Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(blank))));
        awaitIdle(app);try(InputStream in=new FileInputStream(blank)){eq(0,XlsxIO.read(in).entries.size());}
        LedgerDb clear=new LedgerDb(ctx);clear.getWritableDatabase().delete("entries",null,null);clear.close();
        File sampleFile=new File(ctx.getExternalFilesDir(null),"sample.xlsx");
        try(InputStream source=getContext().getAssets().open("example.xlsx");OutputStream dest=new FileOutputStream(sampleFile)){byte[] buffer=new byte[4096];int n;while((n=source.read(buffer))!=-1)dest.write(buffer,0,n);}
        runOnMainSync(()->app.onActivityResult(12,Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(sampleFile))));awaitIdle(app);
        verify=new LedgerDb(ctx);eq(0,verify.all().size());verify.close();
        clickText("취소");waitForIdleSync();
        runOnMainSync(()->app.onActivityResult(12,Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(sampleFile))));awaitIdle(app);
        clickText("3건 추가");awaitIdle(app);verify=new LedgerDb(ctx);eq(3,verify.all().size());verify.close();
        runOnMainSync(()->app.onActivityResult(12,Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(sampleFile))));awaitIdle(app);
        eq(true,getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("추가할 내역이 없어요").size()>0);clickText("확인");
        verify=new LedgerDb(ctx);eq(3,verify.all().size());verify.close();
        categoryUiChecks(app);
        calendarUiChecks(app);
        statisticsUiChecks(app);
        runOnMainSync(()->a.finish());waitForIdleSync();
    }
    void statisticsUiChecks(MainActivity app)throws Exception {
        runOnMainSync(()->{try{set(app,"month",YearMonth.of(2026,10));set(app,"statisticsType",1);set(app,"tab",2);invoke(app,"render");}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        eq(true,findDesc(app.getWindow().getDecorView(),"지출 분류별 원형 차트, 합계 17,000원, 2개 분류")!=null);
        eq(true,findDesc(app.getWindow().getDecorView(),"10월 지출 합계 17,000원")!=null);
        runOnMainSync(()->findDesc(app.getWindow().getDecorView(),"수입 통계").performClick());waitForIdleSync();
        eq(true,findDesc(app.getWindow().getDecorView(),"수입 분류별 원형 차트, 합계 3,000,000원, 1개 분류")!=null);
        eq(true,findDesc(app.getWindow().getDecorView(),"10월 수입 합계 3,000,000원")!=null);screenshot(getTargetContext(),"stats-single");
        runOnMainSync(()->findDesc(app.getWindow().getDecorView(),"저축 통계").performClick());waitForIdleSync();
        eq(true,findDesc(app.getWindow().getDecorView(),"저축 분류별 원형 차트, 합계 1,000,000원, 1개 분류")!=null);
        Bundle saved=new Bundle();runOnMainSync(()->app.onSaveInstanceState(saved));eq(2,saved.getInt("tab"));eq(2,saved.getInt("statisticsType"));
        runOnMainSync(()->findDesc(app.getWindow().getDecorView(),"이전 달").performClick());waitForIdleSync();
        eq(true,findDesc(app.getWindow().getDecorView(),"저축 분류별 원형 차트, 합계 7,000원, 1개 분류")!=null);
        runOnMainSync(()->findDesc(app.getWindow().getDecorView(),"이전 달").performClick());waitForIdleSync();
        eq(true,findDesc(app.getWindow().getDecorView(),"저축 분류별 원형 차트, 합계 0원, 0개 분류")!=null);
        eq(true,findText(app.getWindow().getDecorView(),"아직 저축 기록이 없어요.")!=null);screenshot(getTargetContext(),"stats-empty");
        runOnMainSync(()->findDesc(app.getWindow().getDecorView(),"수입, 지출, 저축 기록 추가").performClick());waitForIdleSync();Thread.sleep(200);
        List<AccessibilityNodeInfo> edits=new ArrayList<>();collectEdits(getUiAutomation().getRootInActiveWindow(),edits);setText(edits.get(0),"123");setText(edits.get(2),"통계 탭 저축 테스트");clickText("저장");waitForIdleSync();Thread.sleep(200);
        LedgerDb db=new LedgerDb(getTargetContext());eq(true,db.all().stream().anyMatch(e->e.date.equals("2026-08-01") && e.type.equals("저축") && e.amount==123));db.close();
        eq(true,findDesc(app.getWindow().getDecorView(),"저축 분류별 원형 차트, 합계 123원, 1개 분류")!=null);
    }
    void statisticsPreview()throws Exception {
        Thread.sleep(5000);
        Context ctx=getTargetContext();ctx.deleteDatabase("chagok.db");LedgerDb db=new LedgerDb(ctx);
        String[] names={"용돈","여행","옷","회사식비","데이트","친구 만남","선물","교통비","책·교육","편의점","이름이 긴 분류도 목록에서 자세하게 확인할 수 있어요"};
        long[] amounts={500000,377500,345800,251600,238550,168200,83000,49000,33600,24900,2000};
        List<Ledger.Entry> seed=new ArrayList<>();for(int i=0;i<names.length;i++)seed.add(new Ledger.Entry("preview-expense-"+i,"2026-10-27","지출",amounts[i],names[i],"화면 확인용 예시",0));
        seed.add(new Ledger.Entry("preview-i1","2026-10-01","수입",3000000,"월급","",0));seed.add(new Ledger.Entry("preview-i2","2026-10-15","수입",500000,"부수입","",0));seed.add(new Ledger.Entry("preview-i3","2026-10-20","수입",200000,"용돈","",0));
        seed.add(new Ledger.Entry("preview-s1","2026-10-05","저축",1000000,"적금","",0));seed.add(new Ledger.Entry("preview-s2","2026-10-10","저축",500000,"청약","",0));seed.add(new Ledger.Entry("preview-s3","2026-10-15","저축",300000,"비상금","",0));db.importAtomic(seed);db.close();
        Activity app=startActivitySync(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        for(int type:new int[]{1,0,2}){
            runOnMainSync(()->{try{set(app,"tab",2);set(app,"statisticsType",type);set(app,"month",YearMonth.of(2026,10));invoke(app,"render");}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();screenshot(ctx,type==1?"stats-expense":type==0?"stats-income":"stats-saving");
        }
        runOnMainSync(()->app.finish());waitForIdleSync();
    }
    void calendarUiChecks(MainActivity app)throws Exception {
        runOnMainSync(()->{try{set(app,"tab",0);set(app,"month",YearMonth.of(2026,9));set(app,"selected",LocalDate.of(2026,9,1));invoke(app,"render");}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        runOnMainSync(()->findDesc(app.getWindow().getDecorView(),"2026-09-14 새 기록").performClick());waitForIdleSync();Thread.sleep(200);
        List<AccessibilityNodeInfo> edits=new ArrayList<>();collectEdits(getUiAutomation().getRootInActiveWindow(),edits);
        eq(4,edits.size());eq("2026-09-14",edits.get(1).getText().toString());
        clickText("취소");waitForIdleSync();LedgerDb db=new LedgerDb(getTargetContext());eq(4,db.all().size());db.close();
        runOnMainSync(()->findDesc(app.getWindow().getDecorView(),"2026-09-30 새 기록").performClick());waitForIdleSync();Thread.sleep(200);
        edits.clear();collectEdits(getUiAutomation().getRootInActiveWindow(),edits);eq("2026-09-30",edits.get(1).getText().toString());
        setText(edits.get(0),"7000");setText(edits.get(2),"달력 날짜 테스트");clickText("저축");
        screenshot(getTargetContext(),"calendar-new-entry");clickText("저장");waitForIdleSync();Thread.sleep(200);
        db=new LedgerDb(getTargetContext());eq(5,db.all().size());eq(true,db.all().stream().anyMatch(e->e.date.equals("2026-09-30") && e.type.equals("저축") && e.amount==7000));db.close();
        runOnMainSync(()->findDesc(app.getWindow().getDecorView(),"2026-09-30 새 기록").performClick());waitForIdleSync();Thread.sleep(200);
        edits.clear();collectEdits(getUiAutomation().getRootInActiveWindow(),edits);eq("2026-09-30",edits.get(1).getText().toString());eq(true,edits.get(0).isShowingHintText());
        clickText("취소");waitForIdleSync();db=new LedgerDb(getTargetContext());eq(5,db.all().size());db.close();
    }
    void categoryUiChecks(MainActivity app)throws Exception {
        runOnMainSync(()->{try{set(app,"tab",0);invoke(app,"render");}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        runOnMainSync(()->findDesc(app.getWindow().getDecorView(),"수입, 지출, 저축 기록 추가").performClick());waitForIdleSync();Thread.sleep(200);
        List<AccessibilityNodeInfo> edits=new ArrayList<>();collectEdits(getUiAutomation().getRootInActiveWindow(),edits);setText(edits.get(0),"4500");
        clickText("+ 분류 추가");waitForIdleSync();Thread.sleep(200);edits.clear();collectEdits(getUiAutomation().getRootInActiveWindow(),edits);eq(1,edits.size());
        clickText("추가");waitForIdleSync();eq(true,getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("지출 분류 추가").size()>0);
        setText(edits.get(0),"반려동물 병원");clickText("추가");waitForIdleSync();Thread.sleep(200);
        edits.clear();collectEdits(getUiAutomation().getRootInActiveWindow(),edits);eq("반려동물 병원",edits.get(2).getText().toString());eq("4500",edits.get(0).getText().toString());
        screenshot(getTargetContext(),"category-entry");clickText("저장");waitForIdleSync();Thread.sleep(200);
        LedgerDb db=new LedgerDb(getTargetContext());eq(4,db.all().size());eq(true,db.all().stream().anyMatch(e->e.category.equals("반려동물 병원") && e.amount==4500));db.close();
        eq(true,new CategoryStore(getTargetContext()).choices("지출",Collections.emptyList()).contains("반려동물 병원"));
        runOnMainSync(()->findDesc(app.getWindow().getDecorView(),"수입, 지출, 저축 기록 추가").performClick());waitForIdleSync();Thread.sleep(200);
        clickText("분류 선택");waitForIdleSync();screenshot(getTargetContext(),"category-choices");clickListItem("반려동물 병원");waitForIdleSync();
        edits.clear();collectEdits(getUiAutomation().getRootInActiveWindow(),edits);eq("반려동물 병원",edits.get(2).getText().toString());
        clickText("수입");clickText("분류 선택");waitForIdleSync();clickText("부수입");waitForIdleSync();
        edits.clear();collectEdits(getUiAutomation().getRootInActiveWindow(),edits);eq("부수입",edits.get(2).getText().toString());
        clickText("취소");waitForIdleSync();db=new LedgerDb(getTargetContext());eq(4,db.all().size());db.close();
    }
    void clickListItem(String text)throws Exception {
        for(int i=0;i<12;i++){
            AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(n.getText())){eq(true,n.performAction(AccessibilityNodeInfo.ACTION_CLICK));return;}
            AccessibilityNodeInfo list=findClass(root,"android.widget.ListView");if(list==null || !list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))break;
            waitForIdleSync();Thread.sleep(150);
        }throw new AssertionError("Category not found: "+text);
    }
    AccessibilityNodeInfo findClass(AccessibilityNodeInfo node,String type){if(node==null)return null;if(type.contentEquals(node.getClassName()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findClass(node.getChild(i),type);if(found!=null)return found;}return null;}
    static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static void invoke(Object o,String name)throws Exception{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}
    void awaitIdle(MainActivity app)throws Exception{Thread.sleep(200);for(int i=0;i<100;i++){final boolean[] active={false};runOnMainSync(()->{try{Field f=MainActivity.class.getDeclaredField("busy");f.setAccessible(true);active[0]=f.getBoolean(app);}catch(Exception e){throw new RuntimeException(e);}});if(!active[0]){waitForIdleSync();Thread.sleep(150);return;}Thread.sleep(100);}throw new AssertionError("File operation timed out");}
    View findText(View v,String text){if(v instanceof TextView && ((TextView)v).getText().toString().equals(text))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View result=findText(g.getChildAt(i),text);if(result!=null)return result;}}return null;}
    View findDesc(View v,String text){if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View result=findDesc(g.getChildAt(i),text);if(result!=null)return result;}}return null;}
    void collectEdits(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> list){if(n==null)return;if("android.widget.EditText".contentEquals(n.getClassName()))list.add(n);for(int i=0;i<n.getChildCount();i++)collectEdits(n.getChild(i),list);}
    void setText(AccessibilityNodeInfo n,String text){Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);eq(true,n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b));}
    void clickText(String text){List<AccessibilityNodeInfo> nodes=getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(text);for(AccessibilityNodeInfo n:nodes)if(text.contentEquals(n.getText())){eq(true,n.performAction(AccessibilityNodeInfo.ACTION_CLICK));return;}throw new AssertionError("Cannot click "+text);}
    void screenshot(Context c,String name)throws Exception{Thread.sleep(250);File file=new File(c.getExternalFilesDir(null),name+".png");try(FileOutputStream out=new FileOutputStream(file)){getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,out);}}
}
