package kr.chagok.ledger;
import android.content.*;
import android.view.*;
import java.util.*;

/** 1.0.0 하단 메뉴 편집: 취소 미저장, 추가·빼기·최소 개수, 접근성 이동, 저장·재생성, 기본 배치, 손상 값 복구. 가상 장부만 사용한다. */
final class BottomNavChecks {
 static List<String> saved(Context c){return new AppPreferences(c).bottomNav();}
 static void tap(DeviceChecks t,String desc)throws Exception{View v=t.windowDesc(desc);t.runOnMainSync(()->v.performClick());t.waitForIdleSync();Thread.sleep(200);}
 static void open(DeviceChecks t,MainActivity app)throws Exception{t.runOnMainSync(()->{try{DeviceChecks.invoke(app,"customizeBottomNav");}catch(Exception e){throw new RuntimeException(e);}});t.waitForIdleSync();Thread.sleep(300);t.awaitActiveText("하단 메뉴 편집",true);}
 static void run(DeviceChecks t)throws Exception{
  Context ctx=t.getTargetContext();SharedPreferences raw=ctx.getSharedPreferences("app_preferences",0);AppPreferences prefs=new AppPreferences(ctx);
  prefs.setPinHash("");raw.edit().remove("bottom_nav").remove("start_tab").commit();String financial=StartupChecks.financial(t);
  MainActivity app=StartupChecks.start(t,false);View decor=app.getWindow().getDecorView();
  for(String name:new String[]{"결산","통계","홈","달력","내역","엑셀"})t.eq(true,t.findDesc(decor,name+" 탭")!=null);
  t.eq(null,t.findDesc(decor,"예산 탭"));t.eq(true,t.findDesc(decor,"홈 탭").isSelected());
  // 편집 후 취소하면 저장하지 않는다.
  open(t,app);tap(t,"엑셀 백업·가져오기 하단에서 빼기");tap(t,"월별 추이·전월 비교 하단에 추가");t.clickText("취소");t.waitForIdleSync();Thread.sleep(300);
  t.eq(false,raw.contains("bottom_nav"));t.eq(BottomNav.DEFAULT,saved(ctx));t.eq(true,t.findDesc(decor,"엑셀 탭")!=null);t.eq(null,t.findDesc(decor,"추이 탭"));
  // 빼기는 최소 3개에서 멈추고, 추가·접근성 이동 후 저장한다.
  open(t,app);for(String name:new String[]{"엑셀 백업·가져오기","결산","원형·막대 통계"})tap(t,name+" 하단에서 빼기");
  tap(t,"홈 하단에서 빼기, 최소 개수라 뺄 수 없음");t.eq(true,t.windowDesc("하단 1번째 홈, 끌어서 순서 변경")!=null);
  tap(t,"예산·목표·반복·자주 쓰는 기록 하단에 추가");tap(t,"저축 해지·배분 하단에 추가");
  View planning=t.windowDesc("하단 4번째 예산·목표·반복·자주 쓰는 기록, 끌어서 순서 변경");final boolean[] moved={false};t.runOnMainSync(()->moved[0]=planning.performAccessibilityAction(CategoryDrag.ACTION_MOVE_UP,null));t.waitForIdleSync();t.eq(true,moved[0]);
  t.eq(true,t.windowDesc("하단 3번째 예산·목표·반복·자주 쓰는 기록, 끌어서 순서 변경")!=null);t.eq(BottomNav.DEFAULT,saved(ctx));
  t.clickText("저장");t.waitForIdleSync();Thread.sleep(400);List<String> expected=Arrays.asList("home","calendar","planning","records","movement");t.eq(expected,saved(ctx));
  decor=app.getWindow().getDecorView();t.eq(true,t.findDesc(decor,"예산 탭")!=null);t.eq(true,t.findDesc(decor,"저축 해지·배분 열기")!=null);t.eq(null,t.findDesc(decor,"엑셀 탭"));t.eq(null,t.findDesc(decor,"결산 탭"));
  UserGuideChecks.click(t,app,"예산 탭");t.eq(7,UserGuideChecks.get(app,"tab"));t.eq(true,t.findDesc(app.getWindow().getDecorView(),"예산 탭").isSelected());t.screenshot(ctx,"bottomnav-custom");
  app=StartupChecks.recreate(t,app);t.eq(7,UserGuideChecks.get(app,"tab"));t.eq(true,t.findDesc(app.getWindow().getDecorView(),"예산 탭").isSelected());t.eq(expected,saved(ctx));
  // 기본 배치는 저장을 눌러야 적용된다.
  open(t,app);t.clickText("기본 배치");t.waitForIdleSync();Thread.sleep(200);t.eq(expected,saved(ctx));t.clickText("저장");t.waitForIdleSync();Thread.sleep(400);t.eq(BottomNav.DEFAULT,saved(ctx));t.eq(true,t.findDesc(app.getWindow().getDecorView(),"엑셀 탭")!=null);
  // 손상·범위 밖 값은 기본 배치로 읽는다.
  raw.edit().putString("bottom_nav","BNV1:home").commit();t.eq(BottomNav.DEFAULT,prefs.bottomNav());raw.edit().putString("bottom_nav","garbage").commit();t.eq(BottomNav.DEFAULT,prefs.bottomNav());
  raw.edit().remove("bottom_nav").commit();t.eq(financial,StartupChecks.financial(t));StartupChecks.close(t,app);
 }
}
