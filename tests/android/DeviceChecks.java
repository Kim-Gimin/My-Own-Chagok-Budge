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
    void eq(Object a,Object b){checks++;if(!Objects.equals(a,b))throw new AssertionError("Expected "+a+", actual "+b);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle out=new Bundle();
        try{runChecks();out.putString("result","PASS: "+checks+" Android integration checks");finish(Activity.RESULT_OK,out);}
        catch(Throwable e){StringWriter sw=new StringWriter();e.printStackTrace(new PrintWriter(sw));out.putString("error",sw.toString());finish(Activity.RESULT_CANCELED,out);}
    }
    void runChecks()throws Exception {
        Context ctx=getTargetContext();ctx.deleteDatabase("chagok.db");LedgerDb db=new LedgerDb(ctx);
        List<Ledger.Entry> seed=Arrays.asList(new Ledger.Entry("i","2026-10-01","수입",3000000,"월급","10월 급여",2),new Ledger.Entry("e","2026-10-27","지출",12500,"식비","점심 식사",3),new Ledger.Entry("s","2026-10-05","저축",1000000,"적금","차곡차곡 모으기",4));
        Ledger.ImportPlan p=db.importAtomic(seed);eq(3,p.additions.size());eq(3,db.all().size());
        p=db.importAtomic(seed);eq(3,p.duplicates);eq(3,db.all().size());
        p=db.importAtomic(Arrays.asList(new Ledger.Entry("new","2026-10-27","지출",20,"임시","",2),new Ledger.Entry("i","2026-10-01","수입",2,"월급","변경",3)));eq(1,p.errors.size());eq(3,db.all().size());
        db.save(new Ledger.Entry("temp","2026-10-27","지출",20,"임시","",0),false);eq(4,db.all().size());db.delete("temp");eq(3,db.all().size());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();XlsxIO.write(bytes,db.all(),false);XlsxIO.ReadResult r=XlsxIO.read(new ByteArrayInputStream(bytes.toByteArray()));eq(3,r.entries.size());eq(0,r.invalidRows);eq(1987500L,Ledger.available(Ledger.totals(r.entries)));
        try(InputStream sample=getContext().getAssets().open("example.xlsx")){r=XlsxIO.read(sample);eq(3,r.entries.size());eq(0,r.invalidRows);eq(1000000L,Ledger.totals(r.entries)[2]);}
        db.close();db=new LedgerDb(ctx);eq(3,db.all().size());db.close();
        Activity a=startActivitySync(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        runOnMainSync(()->{try{set(a,"month",YearMonth.of(2026,10));set(a,"selected",LocalDate.of(2026,10,27));invoke(a,"render");}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
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
        runOnMainSync(()->a.finish());waitForIdleSync();
    }
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
