package kr.chagok.ledger;

import android.content.Context;
import android.widget.NumberPicker;
import java.time.*;

/** Calendar browsing is independent of persisted statistics and a new launcher session. */
final class CalendarStartupChecks {
 static void call(DeviceChecks t,MainActivity app,String name,Class[] types,Object... args)throws Exception{
  t.runOnMainSync(()->{try{DeviceChecks.invoke(app,name,types,args);}catch(Exception e){throw new RuntimeException(e);}});t.waitForIdleSync();
 }
 static void nav(DeviceChecks t,MainActivity app,int tab)throws Exception{call(t,app,"navigate",new Class[]{int.class},tab);}
 static void before(DeviceChecks t)throws Exception{
  Context ctx=t.getTargetContext();new AppPreferences(ctx).statistics(0,YearMonth.of(2024,2),2024,LocalDate.of(2024,2,1),LocalDate.of(2024,2,29));
  MainActivity app=StartupChecks.start(t,false);nav(t,app,0);
  t.eq(true,t.findDesc(app.getWindow().getDecorView(),"2024-02-29 날짜 선택")!=null);
  StartupChecks.reopen(t);t.eq(0,UserGuideChecks.get(app,"tab"));
  StartupChecks.close(t,app);
 }
 static void run(DeviceChecks t)throws Exception{
  Context ctx=t.getTargetContext();AppPreferences prefs=new AppPreferences(ctx);prefs.setPinHash("");prefs.startTab(5);
  YearMonth past=YearMonth.of(2024,2),todayMonth=YearMonth.now();LocalDate today=LocalDate.now();
  prefs.statistics(0,past,2024,past.atDay(1),past.atEndOfMonth());String original=StartupChecks.financial(t);
  MainActivity app=StartupChecks.start(t,false);t.eq(5,UserGuideChecks.get(app,"tab"));nav(t,app,0);
  t.eq(todayMonth,UserGuideChecks.get(app,"calendarMonth"));t.eq(today,UserGuideChecks.get(app,"selected"));t.eq(past,UserGuideChecks.get(app,"month"));
  t.eq(true,t.findDesc(app.getWindow().getDecorView(),today+" 날짜 선택").isSelected());
  float density=app.getResources().getDisplayMetrics().density;
  t.gesture(app,-110*density,0);t.eq(todayMonth.plusMonths(1),UserGuideChecks.get(app,"calendarMonth"));t.eq(past,prefs.statisticsMonth());
  t.gesture(app,110*density,0);t.eq(todayMonth,UserGuideChecks.get(app,"calendarMonth"));
  UserGuideChecks.click(t,app,"연월 선택");t.runOnMainSync(()->{((NumberPicker)t.windowDesc("이동 연도")).setValue(2024);((NumberPicker)t.windowDesc("이동 월")).setValue(2);});t.clickText("확인");t.waitForIdleSync();
  t.eq(past,UserGuideChecks.get(app,"calendarMonth"));t.eq(true,t.findDesc(app.getWindow().getDecorView(),"2024-02-29 날짜 선택")!=null);
  UserGuideChecks.click(t,app,"2024-02-29 날짜 선택");t.eq(LocalDate.of(2024,2,29),UserGuideChecks.get(app,"selected"));t.eq(false,t.activeText("새 기록"));
  UserGuideChecks.click(t,app,"수입, 지출, 저축, 투자, 저축회수 기록 추가");t.eq(true,t.activeText("지출 금액 입력"));t.assertDraftDate("2024-02-29");t.clickText("취소");t.waitForIdleSync();
  app=StartupChecks.recreate(t,app);t.eq(0,UserGuideChecks.get(app,"tab"));t.eq(past,UserGuideChecks.get(app,"calendarMonth"));t.eq(LocalDate.of(2024,2,29),UserGuideChecks.get(app,"selected"));
  nav(t,app,2);call(t,app,"moveMonth",new Class[]{int.class},1);YearMonth march=past.plusMonths(1);t.eq(march,prefs.statisticsMonth());
  nav(t,app,0);t.eq(todayMonth,UserGuideChecks.get(app,"calendarMonth"));t.eq(march,UserGuideChecks.get(app,"month"));
  call(t,app,"moveMonth",new Class[]{int.class},-2);t.clickText("오늘");t.waitForIdleSync();t.eq(todayMonth,UserGuideChecks.get(app,"calendarMonth"));t.eq(today,UserGuideChecks.get(app,"selected"));t.eq(march,prefs.statisticsMonth());
  // A saved entry in an older month stays visible in the calendar without changing the statistics period.
  call(t,app,"recordSavedDate",new Class[]{LocalDate.class},past.atEndOfMonth());call(t,app,"render",new Class[]{});
  t.eq(past,UserGuideChecks.get(app,"calendarMonth"));t.eq(march,prefs.statisticsMonth());t.eq(true,t.findDesc(app.getWindow().getDecorView(),"2024-02-29 날짜 선택").isSelected());
  // Check month/year edges against the displayed calendar, not the separate statistics month.
  call(t,app,"recordSavedDate",new Class[]{LocalDate.class},LocalDate.of(2026,12,31));call(t,app,"moveMonth",new Class[]{int.class},1);t.eq(YearMonth.of(2027,1),UserGuideChecks.get(app,"calendarMonth"));
  for(int root:new int[]{0,1,2,3,4,5,7,8}){nav(t,app,root);StartupChecks.reopen(t);t.eq(5,UserGuideChecks.get(app,"tab"));nav(t,app,0);t.eq(todayMonth,UserGuideChecks.get(app,"calendarMonth"));t.eq(today,UserGuideChecks.get(app,"selected"));}
  nav(t,app,3);call(t,app,"chooseImport",new Class[]{});Thread.sleep(500);BackNavigationChecks.back(t);t.eq(3,UserGuideChecks.get(app,"tab"));
  nav(t,app,0);t.eq(todayMonth,UserGuideChecks.get(app,"calendarMonth"));t.screenshot(ctx,"calendar-current-month");
  StartupChecks.close(t,app);app=StartupChecks.start(t,false);t.eq(5,UserGuideChecks.get(app,"tab"));t.eq(march,prefs.statisticsMonth());t.eq(original,StartupChecks.financial(t));t.screenshot(ctx,"calendar-start-home");StartupChecks.close(t,app);
 }
}
