package kr.chagok.ledger;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.method.DigitsKeyListener;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private int BG=0xFFF7F8FA, INK=0xFF202A33, MUTED=0xFF78818A, GREEN=0xFF19765B, RED=0xFFD76862, PURPLE=0xFF8071BC, LINE=0xFFE8ECEF;
    private int SURFACE=Color.WHITE, INPUT=0xFFF3F5F6, SUBTLE=0xFFF0F4F3, PINK=0xFFED1761;
    private static final int BLUE=0xFF2D99E7;
    private final int[] COLORS={GREEN,RED,PURPLE,0xFFBD781A};
    private static final String MIME="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int EXPORT=10, TEMPLATE=11, IMPORT=12, EXPORT_MONTH=13,ARCHIVE_EXPORT=14,ARCHIVE_IMPORT=15;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final NumberFormat number=NumberFormat.getIntegerInstance(Locale.KOREA);
    private LedgerDb db;
    private BackupStatus backupStatus;
    private BackupArchive.Snapshot pendingArchive;
    private int planSection,trendMonths=6,guideSection=-1;
    private YearMonth trendSelected;
    private CategoryStore categories;
    private AppPreferences preferences;
    private Typeface appFont=Typeface.DEFAULT;
    private boolean unlocked;
    private boolean stopped;
    private Dialog lockDialog;
    private XlsxIO.ReadResult pendingRead;
    private Ledger.ImportPlan pendingPlan;
    private String pendingErrorTitle,pendingError;
    private float[] chartRotations=new float[Ledger.TYPES.length];
    private float comparisonRotation;
    private int pendingRequest,pendingResult;
    private Intent pendingIntent;
    private List<Ledger.Entry> all=new ArrayList<>(), monthly=new ArrayList<>();
    private YearMonth month=YearMonth.now();
    private LocalDate selected=LocalDate.now();
    private int tab=2, statisticsType=-1, filter, displayLimit=100;
    private int statisticsMode=StatisticsPeriod.MONTH,statisticsYear=LocalDate.now().getYear();
    private LocalDate statisticsFrom=LocalDate.now().withDayOfMonth(1),statisticsTo=LocalDate.now();
    private LocalDate renderedStatisticsToday;
    private int settlementMode,settlementYear=LocalDate.now().getYear(),settingsReturnTab=5;
    private YearMonth periodFrom=YearMonth.of(LocalDate.now().getYear(),1),periodTo=YearMonth.of(LocalDate.now().getYear(),12);
    private String search="";
    private boolean recordsAll=true;
    private int recordSort=HistoryData.DATE;
    private Set<String> recordCategories=new HashSet<>();
    private LinearLayout root,page,recordList;
    private LinearLayout recordFooter;
    private LinearLayout bodyContent;
    private ScrollView pageScroll;
    private LinearLayout historyRail;
    private TextView historyToggle;
    private HistoryActionsState historyActions;
    private Runnable revealHistoryRail;
    private AlertDialog progress;
    private boolean busy,resetting;
    private YearMonth pendingExportMonth;

    @Override public void onCreate(Bundle state) {
        preferences=new AppPreferences(this);
        historyActions=new HistoryActionsState(preferences.historyActionsExpanded());
        setTheme(getResources().getIdentifier(preferences.dark()?"AppThemeDark":"AppTheme","style",getPackageName()));
        super.onCreate(state);
        db=new LedgerDb(this);backupStatus=new BackupStatus(this);BackupReminderService.schedule(this);
        categories=new CategoryStore(this);
        tab=preferences.startTab();appFont=preferences.font();applyAppearance();unlocked=!preferences.hasPin();
        statisticsMode=preferences.statisticsMode();statisticsYear=preferences.statisticsYear();month=preferences.statisticsMonth();statisticsFrom=preferences.statisticsDate(true);statisticsTo=preferences.statisticsDate(false);
        if(statisticsFrom.isAfter(statisticsTo)){statisticsFrom=LocalDate.now().withDayOfMonth(1);statisticsTo=LocalDate.now();}
        recordSort=preferences.recordSort();
        if(state!=null) {
            month=YearMonth.parse(state.getString("month",month.toString()));
            selected=LocalDate.parse(state.getString("selected",selected.toString()));
            tab=state.containsKey("statisticsType")?state.getInt("tab",tab):tab; statisticsType=state.getInt("statisticsType",-1);if(statisticsType< -1||statisticsType>=Ledger.TYPES.length)statisticsType=-1; filter=state.getInt("filter"); search=state.getString("search","");
            statisticsMode=state.getInt("statisticsMode",statisticsMode);statisticsYear=state.getInt("statisticsYear",statisticsYear);statisticsFrom=LocalDate.parse(state.getString("statisticsFrom",statisticsFrom.toString()));statisticsTo=LocalDate.parse(state.getString("statisticsTo",statisticsTo.toString()));
            settlementMode=state.getInt("settlementMode");settlementYear=state.getInt("settlementYear",settlementYear);settingsReturnTab=state.getInt("settingsReturnTab",5);
            periodFrom=YearMonth.parse(state.getString("periodFrom",periodFrom.toString()));periodTo=YearMonth.parse(state.getString("periodTo",periodTo.toString()));
            comparisonRotation=state.getFloat("comparisonRotation",0);
            float[] rotations=state.getFloatArray("rotations");if(rotations!=null)System.arraycopy(rotations,0,chartRotations,0,Math.min(rotations.length,chartRotations.length));
            String exportMonth=state.getString("exportMonth"); if(exportMonth!=null) pendingExportMonth=YearMonth.parse(exportMonth);
            recordsAll=state.getBoolean("recordsAll",true);recordSort=state.getInt("recordSort",recordSort);ArrayList<String> categoryState=state.getStringArrayList("recordCategories");if(categoryState!=null)recordCategories=new HashSet<>(categoryState);
        }
        if(state!=null){guideSection=state.getInt("guideSection",-1);planSection=state.getInt("planSection",0);trendMonths=state.getInt("trendMonths",6);String tm=state.getString("trendSelected");if(tm!=null)trendSelected=YearMonth.parse(tm);}
        if(getIntent().getBooleanExtra("open_backup",false))tab=3;
        render();
    }
    @Override public void onSaveInstanceState(Bundle out) {
        out.putInt("guideSection",guideSection);out.putInt("planSection",planSection);out.putInt("trendMonths",trendMonths);if(trendSelected!=null)out.putString("trendSelected",trendSelected.toString());
        super.onSaveInstanceState(out); out.putString("month",month.toString()); out.putString("selected",selected.toString());
        out.putInt("tab",tab); out.putInt("statisticsType",statisticsType); out.putInt("filter",filter); out.putString("search",search);
        out.putInt("settlementMode",settlementMode);out.putInt("settlementYear",settlementYear);out.putInt("settingsReturnTab",settingsReturnTab);out.putString("periodFrom",periodFrom.toString());out.putString("periodTo",periodTo.toString());
        out.putFloatArray("rotations",chartRotations);out.putFloat("comparisonRotation",comparisonRotation);
        out.putInt("statisticsMode",statisticsMode);out.putInt("statisticsYear",statisticsYear);out.putString("statisticsFrom",statisticsFrom.toString());out.putString("statisticsTo",statisticsTo.toString());
        if(pendingExportMonth!=null) out.putString("exportMonth",pendingExportMonth.toString());
        out.putBoolean("recordsAll",recordsAll);out.putInt("recordSort",recordSort);out.putStringArrayList("recordCategories",new ArrayList<>(recordCategories));
    }
    @Override public void onResume(){super.onResume();stopped=false;if(preferences!=null && preferences.hasPin() && !unlocked)render();else if(tab==2&&statisticsMode==StatisticsPeriod.YTD&&!LocalDate.now().equals(renderedStatisticsToday))render();else if(tab==1&&historyRail!=null){historyActions.endScroll();updateHistoryActions(false);}}
    @Override public void onStop(){stopped=true;if(historyRail!=null&&revealHistoryRail!=null)historyRail.removeCallbacks(revealHistoryRail);if(preferences!=null && preferences.hasPin() && !isChangingConfigurations())unlocked=false;super.onStop();}
    @Override public void onBackPressed(){if(preferences.hasPin()&&!unlocked){super.onBackPressed();return;}if(tab==9){tab=6;render();}else if(tab==6){tab=settingsReturnTab;render();}else super.onBackPressed();}
    private void applyAppearance(){
        setTheme(getResources().getIdentifier(preferences.dark()?"AppThemeDark":"AppTheme","style",getPackageName()));
        if(preferences.dark()){BG=0xFF2C3646;SURFACE=0xFF333D50;INPUT=0xFF3B4659;SUBTLE=0xFF3B4659;INK=0xFFE6E7E0;MUTED=0xFFAFB7C4;LINE=0xFF414B5E;GREEN=0xFF35A0EF;RED=0xFFEF4A87;PINK=RED;PURPLE=0xFFB6A2EA;}
        else{BG=0xFFF7F8FA;SURFACE=Color.WHITE;INPUT=0xFFF3F5F6;SUBTLE=0xFFF0F4F3;INK=0xFF202A33;MUTED=0xFF78818A;LINE=0xFFE8ECEF;GREEN=0xFF19765B;RED=0xFFD76862;PINK=0xFFED1761;PURPLE=0xFF8071BC;}
        COLORS[0]=GREEN;COLORS[1]=RED;COLORS[2]=PURPLE;COLORS[3]=preferences.dark()?0xFFF3BD62:0xFFBD781A;
        getWindow().setNavigationBarColor(preferences.dark()?BG:SURFACE);
        getWindow().getDecorView().setSystemUiVisibility(preferences.dark()?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(preferences.hasPin())getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    @Override public void onDestroy() {
        clearHistoryActions();
        if(lockDialog!=null){lockDialog.dismiss();lockDialog=null;}
        if(progress!=null) { progress.dismiss(); progress=null; }
        io.execute(()->db.close()); io.shutdown();
        super.onDestroy();
    }
    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; }
        catch(android.content.pm.PackageManager.NameNotFoundException ex) { throw new IllegalStateException("설치된 앱 정보를 읽지 못했어요.",ex); }
    }
    private int dp(float n) { return Math.round(getResources().getDisplayMetrics().density*n); }
    private GradientDrawable bg(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("tnum"); t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(appFont,bold?Typeface.BOLD:Typeface.NORMAL));
        return t;
    }
    private TextView label(String value,int size,int color) { return text(value,size,color,false); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void space(LinearLayout p,int height) { View v=new View(this); p.addView(v,new LinearLayout.LayoutParams(1,dp(height))); }
    private void line(LinearLayout p) { View v=new View(this); v.setBackgroundColor(LINE); p.addView(v,new LinearLayout.LayoutParams(-1,dp(1))); }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c=column(); c.setPadding(dp(18),dp(18),dp(18),dp(18)); c.setBackground(bg(SURFACE,20));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(14); parent.addView(c,lp); return c;
    }
    private TextView button(String caption,int color,boolean solid,Runnable click) {
        TextView t=text(caption,14,solid?Color.WHITE:color,true); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(48));
        t.setPadding(dp(16),dp(13),dp(16),dp(13)); t.setBackground(bg(solid?color:SUBTLE,12));
        t.setOnClickListener(v->click.run()); t.setClickable(true); return t;
    }
    private void render() {
        if(isFinishing() || isDestroyed()) return;
        if(preferences.hasPin() && !unlocked){renderLock();return;}
        clearHistoryActions();
        all=db.all(); monthly=Ledger.month(all,month);
        boolean stats=tab==2;int surface=stats&&!preferences.dark()?SURFACE:BG;
        root=column(); root.setBackgroundColor(surface);getWindow().setStatusBarColor(surface);
        root.setOnApplyWindowInsetsListener((v,insets)-> { v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets(); });
        setContentView(root); root.requestApplyInsets();
        LinearLayout header=row();
        header.setPadding(dp(HEADER_SIDE),dp(4),dp(HEADER_SIDE),dp(4));
        if(stats){
            header.addView(headerIcon("menu","메뉴 열기",this::showMenu),headerIconParams());header.addView(new View(this),new LinearLayout.LayoutParams(dp(41),1));
            boolean canMove=statisticsMode==StatisticsPeriod.MONTH||statisticsMode==StatisticsPeriod.YEAR;
            if(canMove)header.addView(monthArrow("‹",statisticsMode==StatisticsPeriod.MONTH?"이전 달":"이전 연도",()->moveStatistics(-1)));
            TextView monthTitle=text(statisticsTitle(),20,INK,true);monthTitle.setSingleLine(true);monthTitle.setHorizontallyScrolling(false);monthTitle.setAutoSizeTextTypeUniformWithConfiguration(10,20,1,TypedValueCompat.SP);monthTitle.setGravity(Gravity.CENTER);monthTitle.setContentDescription("통계 기간 선택");monthTitle.setOnClickListener(v->{if(statisticsMode==StatisticsPeriod.MONTH)pickMonth();else if(statisticsMode==StatisticsPeriod.YEAR)pickStatisticsYear();else if(statisticsMode==StatisticsPeriod.CUSTOM)pickStatisticsDates();else new AlertDialog.Builder(this).setTitle("올해 누적").setMessage("올해 1월 1일부터 오늘까지의 내역을 집계해요. 미래 날짜의 기록은 포함하지 않아요.").setPositiveButton("확인",null).show();});header.addView(monthTitle,new LinearLayout.LayoutParams(0,dp(36),1));
            if(canMove)header.addView(monthArrow("›",statisticsMode==StatisticsPeriod.MONTH?"다음 달":"다음 연도",()->moveStatistics(1)));
        }else if(tab==4 || tab==5 || tab==6 || tab==3 || tab==1 || tab==7 || tab==8 || tab==9){
            header.addView(tab==9?headerIcon("back","설명서 뒤로",()->{tab=6;render();}):tab==6?headerIcon("back","설정 뒤로",()->{tab=settingsReturnTab;render();}):headerIcon("menu","메뉴 열기",this::showMenu),headerIconParams());
            if(tab==4)header.addView(new View(this),new LinearLayout.LayoutParams(dp(51),1));
            String caption=tab==9?"설명서":tab==7?"예산·목표·반복":tab==8?"월별 추이·비교":tab==1?(recordsAll?"전체 내역":month.format(DateTimeFormatter.ofPattern("yyyy년 M월"))+" 내역"):tab==4?"결산":tab==5?LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE",Locale.KOREA)):tab==6?"설정":"엑셀 백업·가져오기";
            TextView title=text(caption,tab==5?16:20,INK,true);title.setSingleLine(true);title.setHorizontallyScrolling(false);title.setGravity(Gravity.CENTER);title.setAutoSizeTextTypeUniformWithConfiguration(11,tab==5?16:20,1,TypedValueCompat.SP);header.addView(title,new LinearLayout.LayoutParams(0,dp(HEADER_TOUCH),1));
        }else{
            IconView mark=new IconView("stack",GREEN); LinearLayout.LayoutParams markParams=new LinearLayout.LayoutParams(dp(28),dp(28));markParams.leftMargin=dp(14); header.addView(mark,markParams);
            TextView title=text("차곡",25,INK,true); LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,1); tl.leftMargin=dp(9); header.addView(title,tl);
            TextView today=button("오늘",GREEN,false,()->{ month=YearMonth.now(); selected=LocalDate.now(); displayLimit=100; render(); }); today.setMinHeight(dp(38)); today.setPadding(dp(14),dp(10),dp(14),dp(10)); header.addView(today);
        }
        root.addView(header);
        FrameLayout body=new FrameLayout(this); root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        bodyContent=column();body.addView(bodyContent,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); bodyContent.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        pageScroll=scroll;
        page=tab==0?new MonthSwipeLayout(this,delta->moveMonth(delta)):column(); page.setPadding(dp(stats||tab==1?16:20),dp(stats?0:6),dp(stats||tab==1?16:20),dp(tab==0?88:32)); scroll.addView(page);
        if(tab==0)monthBar();
        if(tab==0) calendar(); else if(tab==1) records(); else if(tab==2) statistics(); else if(tab==3)files();else if(tab==4)settlements();else if(tab==5)home();else if(tab==7)planningPage();else if(tab==8)trendPage();else if(tab==9)new UserGuidePage(this,SURFACE,INK,MUTED,GREEN,appFont).populate(page,pageScroll,guideSection,value->guideSection=value);else settings();
        TextView add=text("+",stats?24:26,stats?PINK:Color.WHITE,false); add.setGravity(Gravity.CENTER); add.setContentDescription("수입, 지출, 저축, 투자, 저축회수 기록 추가"); add.setBackground(bg(stats?(preferences.dark()?SUBTLE:0xFFFDECF2):GREEN,14));
        LinearLayout.LayoutParams addParams=new LinearLayout.LayoutParams(dp(stats?32:42),dp(stats?32:42)); addParams.leftMargin=dp(9); if(tab==2 || tab==4) header.addView(add,addParams);add.setOnClickListener(v->edit(null));
        if(tab==6||tab==9)header.addView(new View(this),headerIconParams());else header.addView(settingsShortcut(),headerIconParams());
        if(tab==0||tab==5){add.setBackground(bg(preferences.dark()?INK:GREEN,30));add.setTextColor(preferences.dark()?BG:Color.WHITE);FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(58),dp(58),Gravity.BOTTOM|Gravity.END);fp.setMargins(0,0,dp(22),dp(20));body.addView(add,fp);}
        if(tab==1)historyActions(body);
        if(tab==6||tab==9)return;
        line(root);
        LinearLayout nav=row(); nav.setPadding(dp(4),dp(8),dp(4),dp(8)); nav.setBackgroundColor(preferences.dark()?BG:SURFACE);
        String[] names={"결산","통계","홈","달력","내역","엑셀"}, icons={"summary","pie","home","calendar","list","file"};int[] targets={4,2,5,0,1,3};
        for(int i=0;i<6;i++) {
            final int target=targets[i];boolean active=tab==target;int tint=active?(target==2?PINK:GREEN):MUTED;LinearLayout item=column(); item.setGravity(Gravity.CENTER); item.setPadding(0,dp(3),0,dp(3));
            item.addView(new IconView(icons[i],tint),new LinearLayout.LayoutParams(dp(23),dp(23))); space(item,5);
            TextView navLabel=text(names[i],11,tint,active); navLabel.setGravity(Gravity.CENTER); item.addView(navLabel); item.setContentDescription(names[i]+" 탭");item.setSelected(active);
            item.setOnClickListener(v->navigate(target)); nav.addView(item,new LinearLayout.LayoutParams(0,dp(51),1));
        }
        root.addView(nav);
    }
    private static final int HEADER_SIDE=8,HEADER_TOUCH=48,HEADER_ICON=24;
    private View settingsShortcut(){return headerIcon("settings","설정 열기",()->navigate(6));}
    /** 상단 아이콘 버튼: 48dp 정사각형 터치 영역 가운데에 24dp 아이콘. 모든 화면이 같은 크기·위치를 사용한다. */
    private View headerIcon(String kind,String description,Runnable action){IconView icon=new IconView(kind,INK);int inset=dp((HEADER_TOUCH-HEADER_ICON)/2f);icon.setPadding(inset,inset,inset,inset);icon.setContentDescription(description);icon.setFocusable(true);icon.setClickable(true);icon.setBackground(headerRipple());icon.setOnClickListener(v->action.run());return icon;}
    private LinearLayout.LayoutParams headerIconParams(){return new LinearLayout.LayoutParams(dp(HEADER_TOUCH),dp(HEADER_TOUCH));}
    private android.graphics.drawable.Drawable headerRipple(){GradientDrawable mask=new GradientDrawable();mask.setShape(GradientDrawable.OVAL);mask.setColor(Color.WHITE);return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(preferences.dark()?0x33FFFFFF:0x1F000000),null,mask);}
    private void navigate(int target){hideKeyboard();if(target==6)settingsReturnTab=tab;if(target==1)recordsAll=true;tab=target;displayLimit=100;render();}
    private void showMenu(){String[] names={"저축 해지·배분","기초자산·현재 보유","결산","원형·막대 통계","월별 추이·전월 비교","예산·목표·반복·자주 쓰는 기록","홈","달력","내역","엑셀 백업·가져오기","설정"};int[] targets={-1,-2,4,2,8,7,5,0,1,3,6};new AlertDialog.Builder(this).setTitle("메뉴").setItems(names,(d,i)->{if(targets[i]==-2)openingList();else if(targets[i]<0)movementList();else navigate(targets[i]);}).setNegativeButton("닫기",null).show();}
    private void settlements(){
        LinearLayout controls=column();controls.setPadding(dp(18),0,dp(18),dp(12));LinearLayout tabs=row();
        String[] names={"연간 결산","종합 결산","기간 결산"};
        for(int i=0;i<3;i++){final int mode=i;LinearLayout item=column();item.setGravity(Gravity.CENTER);space(item,10);TextView name=text(names[i],15,settlementMode==i?INK:MUTED,true);name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setAutoSizeTextTypeUniformWithConfiguration(11,15,1,TypedValueCompat.SP);item.addView(name,new LinearLayout.LayoutParams(-1,-2));space(item,12);View bar=new View(this);bar.setBackgroundColor(settlementMode==i?BLUE:Color.TRANSPARENT);item.addView(bar,new LinearLayout.LayoutParams(dp(58),dp(3)));item.setContentDescription(names[i]);item.setSelected(settlementMode==i);item.setOnClickListener(v->{settlementMode=mode;displayLimit=100;render();});tabs.addView(item,new LinearLayout.LayoutParams(0,dp(49),1));}
        controls.addView(tabs);
        if(settlementMode==0){LinearLayout years=row();TextView prev=button("‹",INK,false,()->{if(settlementYear>1900){settlementYear--;render();}});prev.setContentDescription("이전 해");years.addView(prev,new LinearLayout.LayoutParams(dp(48),dp(46)));TextView year=text(settlementYear+"년",19,INK,true);year.setGravity(Gravity.CENTER);year.setContentDescription("결산 연도 선택");year.setOnClickListener(v->pickSettlementYear());years.addView(year,new LinearLayout.LayoutParams(0,dp(48),1));TextView next=button("›",INK,false,()->{if(settlementYear<9999){settlementYear++;render();}});next.setContentDescription("다음 해");years.addView(next,new LinearLayout.LayoutParams(dp(48),dp(46)));space(controls,10);controls.addView(years);}
        if(settlementMode==2){TextView range=text(periodFrom.format(DateTimeFormatter.ofPattern("yyyy년 MM월"))+" ~ "+periodTo.format(DateTimeFormatter.ofPattern("yyyy년 MM월")),16,INK,true);range.setGravity(Gravity.CENTER);range.setContentDescription("결산 기간 선택");range.setOnClickListener(v->pickSettlementPeriod());controls.addView(range,new LinearLayout.LayoutParams(-1,dp(55)));}
        bodyContent.addView(controls,0);
        YearMonth from=settlementMode==0?YearMonth.of(settlementYear,1):settlementMode==2?periodFrom:null,to=settlementMode==0?YearMonth.of(settlementYear,12):settlementMode==2?periodTo:null;
        OverviewData.Settlement data=OverviewData.settle(all,from,to);
        for(int i=0;i<Math.min(displayLimit,data.months.size());i++){OverviewData.MonthTotal m=data.months.get(i);String prefix=m.month.format(DateTimeFormatter.ofPattern("yyyy년 MM월"));LinearLayout block=column();block.setPadding(0,dp(16),0,dp(16));valueRow(block,prefix+" 합계",m.balance(),INK,16,true,0);valueRow(block,prefix+" 수입",m.totals[0],BLUE,13,false,14);valueRow(block,prefix+" 지출",-m.totals[1],PINK,13,false,14);valueRow(block,prefix+" 저축",-m.totals[2],PURPLE,13,false,14);valueRow(block,prefix+" 투자",-m.totals[3],COLORS[3],13,false,14);block.setContentDescription(prefix+" 월 내역 열기");block.setOnClickListener(v->{month=m.month;tab=1;recordsAll=false;filter=0;search="";recordCategories.clear();recordSort=HistoryData.DATE;render();});page.addView(block);}
        if(data.months.isEmpty())empty(page,"해당 기간의 기록이 없어요","수입·지출·저축·투자를 입력하면 월별 결산을 볼 수 있어요.");
        if(data.months.size()>displayLimit)page.addView(button("이전 100개월 더 보기",BLUE,false,()->{displayLimit+=100;render();}));
        LinearLayout footer=column();footer.setBackgroundColor(preferences.dark()?BG:SURFACE);line(footer);LinearLayout content=column();content.setPadding(dp(20),dp(14),dp(20),dp(16));content.addView(label("기준일 : 1일",12,MUTED));space(content,8);valueRow(content,"총 합계",data.balance(),INK,18,true,0);space(content,5);content.addView(label("수입 − 지출 − 저축 − 투자",11,MUTED));footer.addView(content);bodyContent.addView(footer);
    }
    private void valueRow(LinearLayout parent,String name,long amount,int color,int size,boolean bold,int indent){LinearLayout r=row();r.setPadding(dp(indent),dp(3),0,dp(3));TextView title=text(name,size,INK,bold);title.setMaxLines(2);r.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView price=text(won(amount),size,color,true);price.setSingleLine(true);price.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);price.setAutoSizeTextTypeUniformWithConfiguration(10,size,1,TypedValueCompat.SP);r.addView(price,new LinearLayout.LayoutParams(dp(155),dp(size+8)));r.setContentDescription(name+" "+won(amount));parent.addView(r);}
    private NumberPicker numberPicker(int min,int max,int value,String desc){NumberPicker p=new NumberPicker(this);p.setMinValue(min);p.setMaxValue(max);p.setWrapSelectorWheel(false);p.setFormatter(n->n+(desc.contains("연도")?"년":"월"));p.setValue(value);p.setContentDescription(desc);if(Build.VERSION.SDK_INT>=29){p.setTextColor(INK);p.setTextSize(dp(16));}return p;}
    private void pickSettlementYear(){NumberPicker year=numberPicker(1900,9999,settlementYear,"결산 연도");new AlertDialog.Builder(this).setTitle("연도 선택").setView(year).setNegativeButton("취소",null).setPositiveButton("확인",(d,w)->{year.clearFocus();settlementYear=year.getValue();render();}).show();}
    private void pickSettlementPeriod(){
        LinearLayout form=column();form.setPadding(dp(18),dp(8),dp(18),dp(8));form.addView(label("결산 시작 월과 끝 월을 선택하세요.",14,INK));space(form,8);LinearLayout picks=row();NumberPicker sy=numberPicker(1900,9999,periodFrom.getYear(),"시작 연도"),sm=numberPicker(1,12,periodFrom.getMonthValue(),"시작 월"),ey=numberPicker(1900,9999,periodTo.getYear(),"끝 연도"),em=numberPicker(1,12,periodTo.getMonthValue(),"끝 월");for(NumberPicker p:new NumberPicker[]{sy,sm})picks.addView(p,new LinearLayout.LayoutParams(0,dp(160),1));picks.addView(label("~",18,INK));for(NumberPicker p:new NumberPicker[]{ey,em})picks.addView(p,new LinearLayout.LayoutParams(0,dp(160),1));form.addView(picks);TextView error=label("",12,RED);form.addView(error);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("기간 선택").setView(form).setNegativeButton("취소",null).setPositiveButton("확인",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{for(NumberPicker p:new NumberPicker[]{sy,sm,ey,em})p.clearFocus();YearMonth start=YearMonth.of(sy.getValue(),sm.getValue()),end=YearMonth.of(ey.getValue(),em.getValue());if(start.isAfter(end)){error.setText("시작 월은 끝 월보다 늦을 수 없어요.");return;}periodFrom=start;periodTo=end;displayLimit=100;d.dismiss();render();}));d.show();
    }
    private void home(){
        planningShortcut();page.setGravity(Gravity.CENTER);Set<String> chosen=preferences.homeMetrics();for(OverviewData.Metric metric:OverviewData.metrics(all,LocalDate.now()))if(chosen.contains(metric.id))valueRow(page,metric.label+" :",metric.amount,INK,16,true,0);if(chosen.isEmpty()){page.addView(label("홈에 표시할 결산을 선택하세요.",15,MUTED));space(page,16);page.addView(button("홈 화면 커스텀",GREEN,false,this::customizeHome));}space(page,70);}
    private void settingItem(String title,String desc,Runnable action){LinearLayout c=card(page);LinearLayout r=row();r.addView(text(title,16,INK,true),new LinearLayout.LayoutParams(0,-2,1));r.addView(text("›",25,MUTED,false));c.addView(r);if(!desc.isEmpty()){space(c,11);TextView sub=label(desc,13,MUTED);sub.setLineSpacing(dp(3),1);c.addView(sub);}c.setContentDescription(title);c.setOnClickListener(v->action.run());}
    private void settings(){
        settingItem("설명서 보기","저축회수, 기초자산, 해지·배분과 엑셀 입력 방법을 예시로 확인해요.",()->navigate(9));
        settingItem("기초자산·현재 보유","기록 시작 전 보유한 현금·저축·투자와 현재 금액을 따로 관리해요.",this::openingList);
        settingItem("저축 해지·배분","원금과 수령액을 입력하고 예금·투자로 나눠요.",this::movementList);
        settingItem("예산·목표·반복 관리","예산, 납입 목표와 자주 쓰는 기록을 관리해요.",()->navigate(7));
        settingItem("월별 추이·전월 비교","최근 6개월 또는 12개월의 네 유형을 비교해요.",()->navigate(8));
        space(page,16);settingItem("홈 화면 커스텀","오늘·이번 주·이번 달·올해·전체의 수입, 지출, 저축, 투자, 합계를 골라 표시해요.",this::customizeHome);
        settingItem("상세 분류 관리","수입·지출·저축·투자 상세 분류를 삭제하거나 복원해요. 기존 거래는 유지해요.",()->manageCategories("지출",null));
        settingItem("시작 화면 설정","앱을 처음 열 때 나타날 화면을 선택해요.",this::chooseStartScreen);
        settingItem("폰트 설정","휴대폰 기본 글꼴, 나눔고딕, 나눔명조를 선택해요.",this::chooseFont);
        LinearLayout dark=card(page);Switch toggle=new Switch(this);toggle.setText("다크 모드");toggle.setTextSize(16);toggle.setTextColor(INK);toggle.setTypeface(appFont,Typeface.BOLD);toggle.setChecked(preferences.dark());toggle.setContentDescription("다크 모드");dark.addView(toggle);toggle.setOnCheckedChangeListener((v,on)->{preferences.dark(on);applyAppearance();render();});
        settingItem(preferences.hasPin()?"비밀번호 잠금 해제":"비밀번호 설정",preferences.hasPin()?"앱을 다시 열 때 숫자 비밀번호로 잠금을 해제해요.":"4~12자리 숫자로 앱을 잠글 수 있어요.",()->{if(preferences.hasPin())verifyPinDialog("잠금 해제",()->{preferences.setPinHash("");unlocked=true;applyAppearance();render();});else setupPin();});
        if(preferences.hasPin())settingItem("비밀번호 변경","현재 비밀번호 확인 후 새 비밀번호를 저장해요.",()->verifyPinDialog("현재 비밀번호 확인",this::setupPin));
        settingItem("엑셀 백업·가져오기","전체 기록을 백업하거나 여러 내역을 한 번에 추가해요.",()->navigate(3));
        settingItem("의견 공유","의견을 적고 원하는 앱으로 직접 공유할 수 있어요.",this::shareFeedback);
        settingItem("앱 전체 초기화(포맷)","장부와 사용자 분류·화면·잠금 설정을 모두 삭제하고 처음 상태로 돌아가요.",this::confirmFullReset);
        LinearLayout version=card(page);version.addView(text("버전 정보",16,INK,true));space(version,10);version.addView(label("차곡가계부 "+appVersion(),13,MUTED));space(version,8);version.addView(label("나눔고딕·나눔명조 © NAVER · SIL Open Font License 1.1",10,MUTED));
    }
    private void confirmFullReset(){
        if(busy||resetting){toast("진행 중인 작업이 끝난 뒤 다시 시도해 주세요.");return;}
        LinearLayout form=column();form.setPadding(dp(22),dp(12),dp(22),dp(12));
        TextView warning=text("앱 안의 모든 데이터가 삭제돼요.",17,RED,true);form.addView(warning);space(form,14);
        TextView details=label("삭제되는 항목\n• 수입·지출·저축·투자·저축회수 내역\n• 기초자산과 저축 해지·배분\n• 예산·목표·반복·즐겨찾기\n• 사용자 분류와 삭제한 분류 설정\n• 화면·글꼴·다크 모드·비밀번호\n• 백업 알림 설정과 앱 내부 파일\n\n파일 선택 화면에서 앱 밖에 따로 저장한 엑셀·ZIP 백업은 지우지 않아요. 휴대폰의 다른 앱과 파일에도 영향을 주지 않아요.\n\n초기화 후 앱이 종료돼요. 다시 실행하면 처음 상태로 시작해요. 되돌리려면 미리 저장한 백업이 필요해요.",14,INK);details.setLineSpacing(dp(4),1);form.addView(details);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        new AlertDialog.Builder(this).setTitle("앱 전체 초기화").setView(scroll).setNegativeButton("취소",null)
            .setNeutralButton("먼저 백업",(d,w)->navigate(3))
            .setPositiveButton("계속",(d,w)->{if(preferences.hasPin())verifyPinDialog("초기화 전 비밀번호 확인",this::typeFullReset);else typeFullReset();}).show();
    }
    private void typeFullReset(){
        if(busy||resetting)return;
        LinearLayout form=column();form.setPadding(dp(22),dp(12),dp(22),dp(12));
        TextView note=label("전체 삭제를 진행하려면 아래에 ‘초기화’를 직접 입력하세요. 저장한 백업이 없으면 기록을 복구할 수 없어요.",14,INK);note.setLineSpacing(dp(4),1);form.addView(note);space(form,12);
        EditText phrase=field(form,"초기화 확인 문구","",android.text.InputType.TYPE_CLASS_TEXT);phrase.setSingleLine(true);phrase.setHint("초기화");
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("모든 데이터를 삭제할까요?").setView(scroll).setNegativeButton("취소",null).setPositiveButton("전체 삭제",null).create();
        dialog.setOnShowListener(d->{
            android.widget.Button delete=dialog.getButton(-1);delete.setTextColor(RED);delete.setEnabled(false);
            phrase.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){delete.setEnabled("초기화".contentEquals(s)&&!resetting);}public void afterTextChanged(Editable value){}});
            delete.setOnClickListener(v->{
                if(!"초기화".contentEquals(phrase.getText())||busy||resetting)return;
                if(preferences.hasPin()&&!unlocked){dialog.dismiss();render();return;}
                delete.setEnabled(false);hideKeyboard();resetting=true;
                try{
                    ActivityManager manager=getSystemService(ActivityManager.class);
                    if(manager==null||!manager.clearApplicationUserData())throw new IllegalStateException("Android가 초기화를 시작하지 못했어요. 잠시 후 다시 시도해 주세요.");
                    // Android clears this app's complete data and terminates its process.
                }catch(Exception ex){resetting=false;delete.setEnabled(true);error("초기화하지 못했어요",message(ex));}
            });
        });dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void chooseStartScreen(){String[] labels={"결산 화면","차트 화면","홈 화면","달력 화면","리스트 화면"};int[] ids={4,2,5,0,1};int current=1;for(int i=0;i<ids.length;i++)if(ids[i]==preferences.startTab())current=i;final int[] draft={current};new AlertDialog.Builder(this).setTitle("시작 화면 설정").setSingleChoiceItems(labels,current,(d,i)->draft[0]=i).setNegativeButton("취소",null).setPositiveButton("확인",(d,w)->{preferences.startTab(ids[draft[0]]);toast("다음에 앱을 열 때 적용돼요.");}).show();}
    private void customizeHome(){
        Set<String> draft=preferences.homeMetrics();List<OverviewData.Metric> metrics=OverviewData.metrics(db.all(),LocalDate.now());LinearLayout form=column();form.setPadding(dp(18),dp(10),dp(18),dp(12));form.addView(label("홈에 표시할 결산을 선택하세요.",14,INK));space(form,12);LinearLayout preview=column();Runnable refresh=()->{preview.removeAllViews();preview.addView(text("홈 화면 미리보기",14,INK,true));space(preview,12);for(OverviewData.Metric m:metrics)if(draft.contains(m.id))valueRow(preview,m.label,m.amount,INK,13,true,0);if(draft.isEmpty())preview.addView(label("선택한 항목이 없어요.",12,MUTED));};
        for(int i=0;i<metrics.size();i+=2){LinearLayout r=row();for(int j=i;j<Math.min(i+2,metrics.size());j++){OverviewData.Metric m=metrics.get(j);CheckBox check=new CheckBox(this);check.setText(m.label);check.setTextColor(INK);check.setTextSize(13);check.setTypeface(appFont);check.setChecked(draft.contains(m.id));check.setOnCheckedChangeListener((b,on)->{if(on)draft.add(m.id);else draft.remove(m.id);refresh.run();});r.addView(check,new LinearLayout.LayoutParams(0,dp(45),1));}form.addView(r);}space(form,15);line(form);space(form,18);form.addView(preview);refresh.run();ScrollView scroll=new ScrollView(this);scroll.addView(form);new AlertDialog.Builder(this).setTitle("홈 화면 커스텀").setView(scroll).setNegativeButton("취소",null).setPositiveButton("저장",(d,w)->{preferences.homeMetrics(draft);render();}).show();
    }
    private void chooseFont(){
        String[] ids={"system","gothic","serif"},names={"내 휴대폰 폰트","나눔고딕","나눔명조"};final String[] chosen={preferences.fontId()};LinearLayout form=column();form.setPadding(dp(20),dp(10),dp(20),dp(10));RadioGroup radios=new RadioGroup(this);for(int i=0;i<ids.length;i++){RadioButton b=new RadioButton(this);b.setId(100+i);b.setText(names[i]);b.setTextColor(INK);b.setTypeface(preferences.typeface(ids[i]));radios.addView(b,new RadioGroup.LayoutParams(-1,dp(48)));if(ids[i].equals(chosen[0]))radios.check(b.getId());}form.addView(radios);space(form,15);form.addView(label("선택한 폰트 미리보기",14,INK));space(form,10);EditText preview=new EditText(this);preview.setText("차곡차곡 모으는 나의 가계부\n수입 3,000,000원 · 저축 500,000원");preview.setTextColor(INK);preview.setTextSize(17);preview.setTypeface(preferences.typeface(chosen[0]));preview.setBackground(bg(INPUT,10));preview.setPadding(dp(12),dp(12),dp(12),dp(12));preview.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);preview.setTypeface(preferences.typeface(chosen[0]));form.addView(preview,new LinearLayout.LayoutParams(-1,dp(130)));radios.setOnCheckedChangeListener((g,id)->{chosen[0]=ids[id-100];preview.setTypeface(preferences.typeface(chosen[0]));});ScrollView scroll=new ScrollView(this);scroll.addView(form);new AlertDialog.Builder(this).setTitle("폰트 변경").setView(scroll).setNegativeButton("취소",null).setPositiveButton("저장",(d,w)->{preferences.fontId(chosen[0]);appFont=preferences.font();hideKeyboard();render();}).show();
    }
    private void shareFeedback(){LinearLayout form=column();form.setPadding(dp(20),dp(10),dp(20),dp(8));EditText opinion=field(form,"의견","",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);new AlertDialog.Builder(this).setTitle("의견 공유").setView(form).setNegativeButton("취소",null).setPositiveButton("공유",(d,w)->{Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,"차곡가계부 "+appVersion()+" 의견\n"+opinion.getText());startActivity(Intent.createChooser(send,"공유할 앱 선택"));}).show();}
    private EditText pinField(LinearLayout form,String title){EditText pin=field(form,title,"",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);pin.setSingleLine(true);pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});pin.setHint("숫자 4~12자리");return pin;}
    private void setupPin(){
        LinearLayout form=column();form.setPadding(dp(22),dp(12),dp(22),dp(8));EditText first=pinField(form,"새 비밀번호"),second=pinField(form,"비밀번호 확인");form.addView(label("비밀번호를 잊으면 복구할 수 없어요. 따로 기억해 주세요.",12,MUTED));AlertDialog d=new AlertDialog.Builder(this).setTitle("비밀번호 설정").setView(form).setNegativeButton("취소",null).setPositiveButton("저장",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String pin=first.getText().toString();if(!pin.matches("[0-9]{4,12}")){first.setError("숫자 4~12자리로 입력하세요.");return;}if(!pin.equals(second.getText().toString())){second.setError("비밀번호가 일치하지 않아요.");return;}d.getButton(-1).setEnabled(false);io.execute(()->{String hash=PinCrypto.create(pin);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;preferences.setPinHash(hash);unlocked=!stopped;applyAppearance();d.dismiss();render();});});}));d.show();
    }
    private void verifyPinDialog(String title,Runnable success){
        LinearLayout form=column();form.setPadding(dp(22),dp(12),dp(22),dp(8));EditText pin=pinField(form,"현재 비밀번호");AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(form).setNegativeButton("취소",null).setPositiveButton("확인",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{if(preferences.blockedSeconds()>0){pin.setError(preferences.blockedSeconds()+"초 후 다시 시도하세요.");return;}String candidate=pin.getText().toString();d.getButton(-1).setEnabled(false);io.execute(()->{boolean ok=preferences.checkPin(candidate);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;d.getButton(-1).setEnabled(true);if(ok&&!stopped){d.dismiss();success.run();}else pin.setError(preferences.blockedSeconds()>0?preferences.blockedSeconds()+"초 후 다시 시도하세요.":"비밀번호를 확인하세요.");});});}));d.show();
    }
    private void renderLock(){
        if(lockDialog!=null&&lockDialog.isShowing())return;
        LinearLayout form=column();form.setGravity(Gravity.CENTER);form.setPadding(dp(30),dp(50),dp(30),dp(30));form.setBackgroundColor(BG);form.addView(text("차곡가계부 잠금",24,INK,true));space(form,22);EditText pin=pinField(form,"비밀번호");pin.setContentDescription("잠금 비밀번호");TextView hint=label("숫자 비밀번호를 입력해 주세요.",13,MUTED);form.addView(hint);space(form,18);TextView unlock=button("잠금 해제",GREEN,true,()->{});form.addView(unlock,new LinearLayout.LayoutParams(-1,dp(50)));space(form,12);form.addView(button("앱 닫기",MUTED,false,()->finish()));
        Dialog gate=new Dialog(this);lockDialog=gate;gate.setCancelable(false);gate.setContentView(form);gate.getWindow().setBackgroundDrawableResource(android.R.color.transparent);gate.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);gate.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);gate.show();gate.getWindow().setLayout(-1,-1);
        unlock.setOnClickListener(v->{if(preferences.blockedSeconds()>0){hint.setText(preferences.blockedSeconds()+"초 후 다시 시도하세요.");return;}String candidate=pin.getText().toString();unlock.setEnabled(false);io.execute(()->{boolean ok=preferences.checkPin(candidate);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;unlock.setEnabled(true);if(ok&&!stopped){unlocked=true;((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(pin.getWindowToken(),0);gate.dismiss();lockDialog=null;render();if(pendingIntent!=null){Intent result=pendingIntent;pendingIntent=null;onActivityResult(pendingRequest,pendingResult,result);}else if(pendingArchive!=null){BackupArchive.Snapshot archive=pendingArchive;pendingArchive=null;previewArchive(archive);}else if(pendingRead!=null){XlsxIO.ReadResult read=pendingRead;Ledger.ImportPlan plan=pendingPlan;pendingRead=null;pendingPlan=null;preview(read,plan);}else if(pendingError!=null){String message=pendingError,title=pendingErrorTitle;pendingError=null;pendingErrorTitle=null;error(title,message);}}else{pin.setText("");hint.setText(preferences.blockedSeconds()>0?preferences.blockedSeconds()+"초 후 다시 시도하세요.":"비밀번호가 맞지 않아요.");}});});});
    }
    private void hideKeyboard() { View focus=getCurrentFocus(); if(focus!=null) ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0); }
    private TextView monthArrow(String glyph,String description,Runnable click){
        TextView arrow=text(glyph,30,INK,false);arrow.setGravity(Gravity.CENTER);arrow.setContentDescription(description);arrow.setTooltipText(description);
        arrow.setLayoutParams(new LinearLayout.LayoutParams(dp(HEADER_TOUCH),dp(HEADER_TOUCH)));arrow.setOnClickListener(v->click.run());return arrow;
    }
    private DatePickerDialog datePicker(LocalDate date,String title,DatePickerDialog.OnDateSetListener listener){
        DatePickerDialog picker=new DatePickerDialog(this,listener,date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());picker.setTitle(title);localizeDatePicker(picker);
        picker.getDatePicker().setMinDate(LocalDate.of(1900,1,1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        picker.getDatePicker().setMaxDate(LocalDate.of(9999,12,31).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        picker.getDatePicker().updateDate(date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());return picker;
    }
    private TextView categoryAction(String title,Runnable click){
        TextView action=button(title,GREEN,false,click);action.setSingleLine(true);action.setPadding(dp(8),dp(8),dp(8),dp(8));action.setAutoSizeTextTypeUniformWithConfiguration(9,14,1,TypedValueCompat.SP);return action;
    }
    private void monthBar() {
        LinearLayout r=row(); r.setPadding(0,dp(2),0,dp(10));
        r.addView(monthArrow("‹","이전 달",()->moveMonth(-1)));
        TextView value=text(month.getYear()+"년 "+month.getMonthValue()+"월",23,INK,true); value.setGravity(Gravity.CENTER);value.setContentDescription("연월 선택"); value.setOnClickListener(v->pickMonth()); r.addView(value,new LinearLayout.LayoutParams(0,dp(46),1));
        r.addView(monthArrow("›","다음 달",()->moveMonth(1))); page.addView(r);
    }
    private void moveMonth(int delta) { if((month.getYear()==1900 && month.getMonthValue()==1 && delta<0) || (month.getYear()==9999 && month.getMonthValue()==12 && delta>0)) return; month=month.plusMonths(delta); selected=month.atDay(1); displayLimit=100;if(tab==2)saveStatisticsPeriod(); render(); }
    private void pickMonth(){pickMonth(null);}
    private void pickMonth(Runnable confirmed) {
        LinearLayout form=row();form.setPadding(dp(24),dp(12),dp(24),dp(12));NumberPicker year=numberPicker(1900,9999,month.getYear(),"이동 연도"),value=numberPicker(1,12,month.getMonthValue(),"이동 월");form.addView(year,new LinearLayout.LayoutParams(0,dp(175),1));form.addView(value,new LinearLayout.LayoutParams(0,dp(175),1));
        new AlertDialog.Builder(this).setTitle("연·월 선택").setView(form).setNegativeButton("취소",null).setPositiveButton("확인",(d,w)->{year.clearFocus();value.clearFocus();month=YearMonth.of(year.getValue(),value.getValue());selected=month.atDay(1);displayLimit=100;if(confirmed!=null)confirmed.run();if(tab==2)saveStatisticsPeriod();render();}).show();
    }
    private void localizeDatePicker(DatePickerDialog picker){picker.setButton(DialogInterface.BUTTON_POSITIVE,"확인",picker);picker.setButton(DialogInterface.BUTTON_NEGATIVE,"취소",picker);}
    private StatisticsPeriod statisticsPeriod(){return StatisticsPeriod.of(statisticsMode,month,statisticsYear,statisticsFrom,statisticsTo,LocalDate.now());}
    private String statisticsTitle(){return statisticsMode==StatisticsPeriod.MONTH?month.format(DateTimeFormatter.ofPattern("yyyy년 MM월")):statisticsMode==StatisticsPeriod.YEAR?statisticsYear+"년":statisticsMode==StatisticsPeriod.YTD?"올해 누적":"기간 지정";}
    private String statisticsPeriodLabel(){StatisticsPeriod p=statisticsPeriod();return statisticsMode==StatisticsPeriod.MONTH?month.format(DateTimeFormatter.ofPattern("yyyy년 M월")):p.from.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))+" ~ "+p.to.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));}
    private String statisticsTotalPrefix(){return statisticsMode==StatisticsPeriod.MONTH?month.getMonthValue()+"월":statisticsMode==StatisticsPeriod.YEAR?statisticsYear+"년":statisticsMode==StatisticsPeriod.YTD?"올해 누적":"지정 기간";}
    private void saveStatisticsPeriod(){preferences.statistics(statisticsMode,month,statisticsYear,statisticsFrom,statisticsTo);}
    private void moveStatistics(int delta){if(statisticsMode==StatisticsPeriod.MONTH)moveMonth(delta);else if(statisticsMode==StatisticsPeriod.YEAR){int next=statisticsYear+delta;if(next<1900||next>9999)return;statisticsYear=next;displayLimit=100;saveStatisticsPeriod();render();}}
    private void pickStatisticsYear(){NumberPicker year=numberPicker(1900,9999,statisticsYear,"통계 연도");new AlertDialog.Builder(this).setTitle("통계 연도 선택").setView(year).setNegativeButton("취소",null).setPositiveButton("확인",(d,w)->{year.clearFocus();statisticsYear=year.getValue();displayLimit=100;saveStatisticsPeriod();render();}).show();}
    private void statisticsPeriodControls(){
        renderedStatisticsToday=LocalDate.now();
        LinearLayout controls=column();controls.setPadding(dp(16),dp(4),dp(16),dp(5));LinearLayout modes=row();String[] names={"월별","연도별","올해 누적","기간 지정"};
        for(int i=0;i<names.length;i++){final int mode=i;TextView option=text(names[i],12,statisticsMode==i?PINK:MUTED,statisticsMode==i);option.setGravity(Gravity.CENTER);option.setBackground(bg(statisticsMode==i?SUBTLE:Color.TRANSPARENT,10));option.setContentDescription(names[i]+" 통계 기간");option.setSelected(statisticsMode==i);option.setOnClickListener(v->{if(mode==StatisticsPeriod.CUSTOM){pickStatisticsDates();return;}statisticsMode=mode;displayLimit=100;saveStatisticsPeriod();render();});modes.addView(option,new LinearLayout.LayoutParams(0,dp(42),1));}controls.addView(modes);
        StatisticsPeriod p=statisticsPeriod();TextView dates=label(p.from.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))+" ~ "+p.to.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))+(statisticsMode==StatisticsPeriod.YTD?" · 오늘까지":""),11,MUTED);dates.setGravity(Gravity.CENTER);dates.setPadding(0,dp(8),0,dp(7));dates.setContentDescription("통계 집계 기간 "+p.from+" ~ "+p.to);if(statisticsMode==StatisticsPeriod.CUSTOM)dates.setOnClickListener(v->pickStatisticsDates());controls.addView(dates);bodyContent.addView(controls,0);
    }
    private void pickStatisticsDates(){
        final LocalDate[] draft={statisticsMode==StatisticsPeriod.CUSTOM?statisticsFrom:statisticsPeriod().from,statisticsMode==StatisticsPeriod.CUSTOM?statisticsTo:statisticsPeriod().to};LinearLayout form=column();form.setPadding(dp(22),dp(10),dp(22),dp(8));form.addView(label("시작일과 종료일을 모두 포함해 집계해요.",13,MUTED));space(form,15);TextView[] fields=new TextView[2];String[] titles={"시작일","종료일"};
        for(int i=0;i<2;i++){final int index=i;form.addView(label(titles[i],12,MUTED));space(form,6);TextView field=button(draft[i].toString(),INK,false,()->{});fields[i]=field;field.setContentDescription("통계 "+titles[i]+" 선택");field.setOnClickListener(v->{LocalDate date=draft[index];datePicker(date,titles[index]+" 선택",(view,y,m,d)->{draft[index]=LocalDate.of(y,m+1,d);fields[index].setText(draft[index].toString());}).show();});form.addView(field,new LinearLayout.LayoutParams(-1,dp(48)));space(form,12);}TextView error=label("",12,RED);form.addView(error);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("통계 기간 지정").setView(form).setNegativeButton("취소",null).setPositiveButton("확인",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{if(draft[0].isAfter(draft[1])){error.setText("시작일은 종료일보다 늦을 수 없어요.");return;}statisticsFrom=draft[0];statisticsTo=draft[1];statisticsMode=StatisticsPeriod.CUSTOM;displayLimit=100;saveStatisticsPeriod();dialog.dismiss();render();}));dialog.show();
    }
    private String won(long value) { return number.format(value)+"원"; }
    private String shortMoney(long value) {
        if(value>=100_000_000) return String.format(Locale.KOREA,"%.1f억",value/100_000_000.0);
        if(value>=10_000) return String.format(Locale.KOREA,value%10_000==0?"%.0f만":"%.1f만",value/10_000.0);
        return number.format(value);
    }
    private void summary() {
        long[] total=Ledger.totals(monthly);
        LinearLayout c=card(page); c.setPadding(dp(17),dp(14),dp(17),dp(14));
        c.addView(label("이번 달 사용 가능 금액",11,MUTED)); space(c,4);
        TextView balance=text(won(Ledger.available(total)),26,INK,true); balance.setAutoSizeTextTypeUniformWithConfiguration(15,26,1,TypedValueCompat.SP); c.addView(balance,new LinearLayout.LayoutParams(-1,dp(32)));
        space(c,10); line(c); space(c,10);
        LinearLayout r=row();
        for(int i=0;i<Ledger.TYPES.length;i++) {
            LinearLayout item=column(); item.setPadding(0,0,dp(7),0); item.addView(label(Ledger.TYPES[i],11,COLORS[i])); space(item,3);
            TextView amount=text(number.format(total[i]),13,INK,true); amount.setSingleLine(true); amount.setAutoSizeTextTypeUniformWithConfiguration(9,13,1,TypedValueCompat.SP); item.addView(amount,new LinearLayout.LayoutParams(-1,dp(18))); r.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        c.addView(r); space(c,6); c.addView(label("수입 − 지출 − 저축 − 투자  ·  단위: 원",10,MUTED));
    }
    private void selectCalendarDate(LocalDate date){int position=pageScroll.getScrollY();selected=date;displayLimit=100;render();ScrollView scroll=pageScroll;scroll.post(()->{if(tab==0&&pageScroll==scroll)scroll.scrollTo(0,position);});}
    private void calendar() {
        LinearLayout c=card(page); c.setPadding(dp(8),dp(15),dp(8),dp(10));
        LinearLayout weekdays=row(); String[] labels={"일","월","화","수","목","금","토"};
        for(int i=0;i<7;i++) { TextView t=text(labels[i],12,i==0?RED:i==6?0xFF6485B7:MUTED,false); t.setGravity(Gravity.CENTER); weekdays.addView(t,new LinearLayout.LayoutParams(0,dp(30),1)); } c.addView(weekdays);
        Map<String,long[]> daily=new HashMap<>();
        for(Ledger.Entry e:monthly) { long[] d=daily.get(e.date); if(d==null) {d=new long[Ledger.TYPES.length]; daily.put(e.date,d);} d[Ledger.index(e.type)]+=e.effect(); }
        int offset=month.atDay(1).getDayOfWeek().getValue()%7, n=month.lengthOfMonth();
        int weeks=(offset+n+6)/7;
        for(int w=0;w<weeks;w++) {
            LinearLayout week=row(); week.setGravity(Gravity.TOP);
            for(int d=0;d<7;d++) {
                int day=w*7+d-offset+1; LinearLayout cell=column(); cell.setGravity(Gravity.CENTER_HORIZONTAL); cell.setPadding(dp(1),dp(3),dp(1),dp(3));
                week.addView(cell,new LinearLayout.LayoutParams(0,dp(78),1));
                if(day<1 || day>n) continue;
                LocalDate date=month.atDay(day); boolean active=date.equals(selected), today=date.equals(LocalDate.now());
                if(active) cell.setBackground(bg(preferences.dark()?SUBTLE:0xFFEAF3EF,12));
                TextView dayText=text(String.valueOf(day),13,today?Color.WHITE:(d==0?RED:d==6?0xFF6485B7:INK),today || active); dayText.setGravity(Gravity.CENTER);
                if(today) dayText.setBackground(bg(GREEN,20));
                cell.addView(dayText,new LinearLayout.LayoutParams(dp(23),dp(23))); space(cell,2);
                long[] t=daily.get(date.toString());
                if(t!=null) for(int i=0;i<Ledger.TYPES.length;i++) if(t[i]>0) {
                    TextView amount=text((i==0?"+":"−")+shortMoney(t[i]),9,COLORS[i],false); amount.setSingleLine(true); amount.setGravity(Gravity.CENTER); cell.addView(amount,new LinearLayout.LayoutParams(-1,dp(11)));
                }
                cell.setContentDescription(date+" 날짜 선택");cell.setSelected(active); cell.setOnClickListener(v->selectCalendarDate(date));
            }
            c.addView(week);
        }
        LinearLayout legend=row(); legend.setGravity(Gravity.CENTER); legend.setPadding(0,dp(5),0,dp(3));
        for(int i=0;i<Ledger.TYPES.length;i++) { TextView t=label("● "+Ledger.TYPES[i]+"   ",11,COLORS[i]); legend.addView(t); } c.addView(legend);
        space(page,7);
        LinearLayout heading=row(); TextView date=text(selected.format(DateTimeFormatter.ofPattern("M월 d일 EEEE",Locale.KOREA)),17,INK,true); heading.addView(date,new LinearLayout.LayoutParams(0,-2,1));
        List<Ledger.Entry> entries=new ArrayList<>(); for(Ledger.Entry e:monthly) if(e.date.equals(selected.toString())) entries.add(e);
        heading.addView(label(entries.size()+"건",12,MUTED)); page.addView(heading); space(page,12);
        if(entries.isEmpty()) empty(page,"아직 기록이 없어요","오른쪽 아래 + 버튼으로 선택한 날짜에 기록해 보세요.");
        else showEntries(page,entries,false);
        space(page,16);summary();
    }
    private void records() {
        LinearLayout controls=column();controls.setPadding(dp(16),dp(3),dp(16),dp(8));LinearLayout scope=row();
        TextView allScope=button("전체 기간",recordsAll?GREEN:MUTED,false,()->{recordsAll=true;displayLimit=100;render();});allScope.setContentDescription("전체 기간 내역");allScope.setSelected(recordsAll);allScope.setPadding(dp(4),dp(10),dp(4),dp(10));scope.addView(allScope,new LinearLayout.LayoutParams(0,dp(40),1));TextView monthScope=button(recordsAll?"월 선택":month.format(DateTimeFormatter.ofPattern("yyyy.MM")),recordsAll?MUTED:GREEN,false,()->pickMonth(()->recordsAll=false));monthScope.setContentDescription("내역 월 선택");monthScope.setPadding(dp(4),dp(10),dp(4),dp(10));LinearLayout.LayoutParams sm=new LinearLayout.LayoutParams(0,dp(40),1);sm.leftMargin=dp(7);scope.addView(monthScope,sm);controls.addView(scope);space(controls,8);
        LinearLayout filters=row();String[] names={"전체","지출","수입","저축","투자"};
        // Keep saved filter IDs, but display the same income/expense/saving/investment order everywhere.
        for(int i:new int[]{0,2,1,3,4}) {
            final int f=i; TextView t=text(names[i],12,filter==i?Color.WHITE:MUTED,filter==i); t.setGravity(Gravity.CENTER);t.setContentDescription(names[i]+" 내역 필터");t.setSelected(filter==i); t.setPadding(dp(3),dp(10),dp(3),dp(10)); t.setBackground(bg(filter==i?GREEN:SURFACE,10)); t.setOnClickListener(v->{filter=f; displayLimit=100; render();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),1); if(i>0) lp.leftMargin=dp(5); filters.addView(t,lp);
        } controls.addView(filters);
        if(!search.isEmpty()){space(controls,8);TextView query=label("메모: "+search+"  ×",12,GREEN);query.setMaxLines(2);query.setEllipsize(TextUtils.TruncateAt.END);query.setContentDescription("메모 검색 해제");query.setOnClickListener(v->{search="";displayLimit=100;render();});controls.addView(query);}
        if(!recordCategories.isEmpty()||recordSort==HistoryData.CATEGORY){space(controls,7);TextView category=label((recordCategories.isEmpty()?"모든 상세 분류":recordCategories.size()+"개 상세 분류 선택")+" · "+(recordSort==HistoryData.CATEGORY?"상세 분류순":"날짜순"),11,MUTED);category.setContentDescription("상세 분류 선택 상태");category.setOnClickListener(v->chooseRecordCategories());controls.addView(category);}
        bodyContent.addView(controls,0);recordList=column();page.addView(recordList);recordFooter=column();bodyContent.addView(recordFooter);refreshRecords();
    }
    private void refreshRecords() {
        recordList.removeAllViews();String[] types={"","지출","수입","저축","투자"};HistoryData.Result data=HistoryData.build(all,recordsAll?null:month,types[filter],search,recordCategories,recordSort);
        recordList.addView(label(data.entries.size()+"건 · "+(recordSort==HistoryData.CATEGORY?"상세 분류순":"최신 날짜순"),11,MUTED));space(recordList,12);int shown=0;String previousCategory=null;
        if(data.entries.isEmpty())empty(recordList,"내역이 없어요",search.isEmpty()&&recordCategories.isEmpty()?"+로 첫 기록을 남겨 보세요. 도구가 접혀 있으면 ⋯를 누르세요.":"검색어나 선택 상세 분류를 확인하세요.");
        for(HistoryData.Month group:data.months){if(shown>=displayLimit)break;if(recordSort==HistoryData.CATEGORY&&!group.category.equals(previousCategory)){space(recordList,8);TextView title=text(group.category,17,GREEN,true);title.setContentDescription("상세 분류 그룹 "+group.category);recordList.addView(title);space(recordList,10);previousCategory=group.category;}
            int monthShown=0;for(HistoryData.Day day:group.days){if(shown>=displayLimit)break;historyDateHeader(day.date);int count=0;for(Ledger.Entry entry:day.entries){if(shown>=displayLimit)break;historyEntry(entry);shown++;monthShown++;count++;}space(recordList,10);historyTotal(recordList,"일일 합계"+(count<day.entries.size()?" · 전체 "+day.entries.size()+"건":""),Ledger.available(day.totals),false);space(recordList,16);}
            historyTotal(recordList,group.month.format(DateTimeFormatter.ofPattern("yyyy년 M월"))+" 합계",Ledger.available(group.totals),true);if(monthShown<group.count){recordList.addView(label("이 달 전체 "+group.count+"건의 합계 · 더 보기로 이어집니다.",10,MUTED));}space(recordList,24);
        }
        if(shown<data.entries.size())recordList.addView(button("내역 100건 더 보기",GREEN,false,()->{int position=pageScroll.getScrollY();displayLimit+=100;refreshRecords();pageScroll.post(()->pageScroll.scrollTo(0,position));}));
        recordFooter.removeAllViews();line(recordFooter);LinearLayout footer=column();footer.setPadding(dp(16),dp(10),dp(84),dp(12));boolean filtered=filter!=0||!search.isEmpty()||!recordCategories.isEmpty();historyTotal(footer,filtered?"결과 합계":recordsAll?"전체 합계":"선택 월 합계",Ledger.available(data.totals),true);space(footer,4);footer.addView(label("수입 − 지출 − 저축 − 투자 · "+data.entries.size()+"건",10,MUTED));recordFooter.addView(footer);
    }
    private void historyTotal(LinearLayout parent,String caption,long amount,boolean bold){LinearLayout r=row();r.setPadding(0,dp(4),0,dp(4));TextView name=text(caption,bold?13:12,INK,bold);name.setMaxLines(2);r.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView value=text(won(amount),bold?15:13,INK,true);value.setMaxLines(1);value.setHorizontallyScrolling(false);value.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);value.setAutoSizeTextTypeUniformWithConfiguration(5,bold?15:13,1,TypedValueCompat.SP);r.addView(value,new LinearLayout.LayoutParams(dp(118),dp(24)));r.setContentDescription(caption+" "+won(amount));parent.addView(r);}
    private void historyDateHeader(String date){LinearLayout heading=row();for(int i=0;i<3;i++){if(i==1){TextView label=text(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 EEEE",Locale.KOREA)),12,MUTED,true);label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setAutoSizeTextTypeUniformWithConfiguration(9,12,1,TypedValueCompat.SP);heading.addView(label,new LinearLayout.LayoutParams(dp(192),dp(29)));}else{View line=new View(this);line.setBackgroundColor(LINE);heading.addView(line,new LinearLayout.LayoutParams(0,dp(1),1));}}recordList.addView(heading);space(recordList,7);}
    private void historyEntry(Ledger.Entry entry){LinearLayout r=row();r.setPadding(0,dp(8),0,dp(8));int kind=Ledger.index(entry.type);TextView type=text(entry.type.equals("저축회수")?"회수":entry.type,12,COLORS[kind],true);r.addView(type,new LinearLayout.LayoutParams(dp(31),-2));TextView price=text((kind==0||entry.type.equals("저축회수")?"+":"−")+won(entry.amount),13,COLORS[kind],true);price.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);price.setMaxLines(1);price.setHorizontallyScrolling(false);price.setAutoSizeTextTypeUniformWithConfiguration(5,13,1,TypedValueCompat.SP);r.addView(price,new LinearLayout.LayoutParams(dp(106),dp(27)));LinearLayout description=column();TextView memo=text(entry.memo.isEmpty()?entry.category:entry.memo,13,INK,true);memo.setMaxLines(3);memo.setEllipsize(TextUtils.TruncateAt.END);description.addView(memo);if(!entry.memo.isEmpty()){space(description,4);description.addView(label(entry.category,10,MUTED));}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(8);r.addView(description,p);r.setContentDescription(entry.date+" "+entry.type+" "+entry.category+" "+won(entry.amount)+". 눌러서 수정");r.setOnClickListener(v->edit(entry));recordList.addView(r);}
    private void historyActions(FrameLayout parent){
        LinearLayout rail=column();rail.setGravity(Gravity.CENTER_HORIZONTAL);rail.setClipChildren(false);rail.setClipToPadding(false);historyRail=rail;
        revealHistoryRail=()->{if(tab==1&&historyRail==rail&&!stopped&&!isFinishing()){historyActions.endScroll();updateHistoryActions(true);}};
        pageScroll.setOnScrollChangeListener((view,x,y,oldX,oldY)->{
            if(y==oldY||historyRail!=rail)return;historyActions.beginScroll();updateHistoryActions(false);rail.removeCallbacks(revealHistoryRail);
            if(historyActions.expanded())rail.postDelayed(revealHistoryRail,600);
        });
        String[] descriptions={"메모 검색","상세 분류 정렬","수입, 지출, 저축, 투자, 저축회수 기록 추가"};
        for(int i=0;i<3;i++){
            final int action=i;FrameLayout circle=new FrameLayout(this);circle.setBackground(bg(preferences.dark()?INK:SURFACE,30));circle.setContentDescription(descriptions[i]);circle.setElevation(dp(3));View icon;
            if(i==0)icon=new IconView("search",preferences.dark()?BG:INK);
            else{TextView label=text(i==1?"A–Z":"+",i==1?17:29,preferences.dark()?BG:INK,true);label.setGravity(Gravity.CENTER);label.setMaxLines(1);label.setHorizontallyScrolling(false);label.setAutoSizeTextTypeUniformWithConfiguration(9,i==1?17:29,1,TypedValueCompat.SP);icon=label;}
            int size=i==0?30:44;circle.addView(icon,new FrameLayout.LayoutParams(dp(size),dp(size),Gravity.CENTER));
            circle.setOnClickListener(v->{if(action==0)searchRecords();else if(action==1)chooseRecordCategories();else edit(null);});rail.addView(circle,new LinearLayout.LayoutParams(dp(52),dp(52)));if(i<2)space(rail,12);
        }
        FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(68),-2,Gravity.BOTTOM|Gravity.END);rp.setMargins(0,0,dp(5),dp(80));parent.addView(rail,rp);
        TextView toggle=text("",24,preferences.dark()?BG:INK,true);toggle.setGravity(Gravity.CENTER);toggle.setBackground(bg(preferences.dark()?INK:SURFACE,30));toggle.setElevation(dp(3));historyToggle=toggle;
        toggle.setOnClickListener(v->{historyActions.toggle();preferences.historyActionsExpanded(historyActions.expanded());rail.removeCallbacks(revealHistoryRail);updateHistoryActions(false);});
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM|Gravity.END);tp.setMargins(0,0,dp(15),dp(20));parent.addView(toggle,tp);updateHistoryActions(false);
    }
    private void updateHistoryActions(boolean animate){
        if(historyRail==null)return;historyRail.animate().cancel();boolean visible=historyActions.visible();
        historyRail.setVisibility(visible?View.VISIBLE:historyActions.expanded()?View.INVISIBLE:View.GONE);
        if(visible&&animate){historyRail.setAlpha(0);historyRail.animate().alpha(1).setDuration(140).start();}else historyRail.setAlpha(visible?1:0);
        if(historyToggle!=null){String title=historyActions.expanded()?"내역 도구 접기":"내역 도구 펼치기";historyToggle.setText(historyActions.expanded()?"⌄":"⋯");historyToggle.setContentDescription(title);historyToggle.setTooltipText(title);historyToggle.setSelected(historyActions.expanded());}
    }
    private void clearHistoryActions(){
        if(historyRail!=null){if(revealHistoryRail!=null)historyRail.removeCallbacks(revealHistoryRail);historyRail.animate().cancel();}
        if(historyActions!=null)historyActions.endScroll();historyRail=null;historyToggle=null;revealHistoryRail=null;
    }
    private void searchRecords(){LinearLayout form=column();form.setPadding(dp(22),dp(10),dp(22),dp(8));EditText query=field(form,"메모 검색",search,android.text.InputType.TYPE_CLASS_TEXT);query.setSingleLine(true);query.setHint("예: 점심, 월세");query.setContentDescription("메모 검색어");query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});form.addView(label("현재 기간·돈의 성격·상세 분류 안에서 메모를 찾아요.",12,MUTED));new AlertDialog.Builder(this).setTitle("메모로 검색하기").setView(form).setNegativeButton("취소",null).setNeutralButton("검색 해제",(d,w)->{search="";displayLimit=100;render();}).setPositiveButton("검색",(d,w)->{search=query.getText().toString().trim();displayLimit=100;hideKeyboard();render();}).show();}
    private void chooseRecordCategories(){
        Set<String> draft=new HashSet<>(recordCategories);final int[] order={HistoryData.CATEGORY};TreeSet<String> options=new TreeSet<>((a,b)->{int result=java.text.Collator.getInstance(Locale.KOREA).compare(a,b);return result!=0?result:a.compareTo(b);});for(String type:Ledger.TYPES)options.addAll(categories.choices(type,all));for(Ledger.Entry entry:all)options.add(entry.category);options.addAll(draft);
        LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(10));form.addView(label("선택하지 않으면 모든 상세 분류를 보여줘요.",12,MUTED));RadioGroup modes=new RadioGroup(this);modes.setOrientation(LinearLayout.HORIZONTAL);for(int i=0;i<2;i++){RadioButton mode=new RadioButton(this);mode.setId(300+i);mode.setText(i==0?"날짜순":"상세 분류순");mode.setTextColor(INK);mode.setTextSize(13);mode.setTypeface(appFont);modes.addView(mode,new RadioGroup.LayoutParams(0,dp(45),1));}modes.check(301);modes.setOnCheckedChangeListener((g,id)->order[0]=id-300);form.addView(modes);line(form);space(form,8);for(String category:options){CheckBox check=new CheckBox(this);check.setText(category);check.setTextColor(INK);check.setTextSize(14);check.setTypeface(appFont);check.setChecked(draft.contains(category));check.setContentDescription(category+" 상세 분류 선택");check.setPadding(0,dp(6),0,dp(6));check.setOnCheckedChangeListener((b,on)->{if(on)draft.add(category);else draft.remove(category);});form.addView(check,new LinearLayout.LayoutParams(-1,-2));}ScrollView scroll=new ScrollView(this);scroll.addView(form);new AlertDialog.Builder(this).setTitle("선택한 상세 분류로 정렬").setView(scroll).setNegativeButton("취소",null).setNeutralButton("전체 보기",(d,w)->{recordCategories.clear();recordSort=HistoryData.DATE;preferences.recordSort(recordSort);displayLimit=100;render();}).setPositiveButton("확인",(d,w)->{recordCategories=draft;recordSort=order[0];preferences.recordSort(recordSort);displayLimit=100;render();}).show();
    }
    private void showEntries(LinearLayout parent,List<Ledger.Entry> entries,boolean groupDates) {
        String last="";
        for(int i=0;i<Math.min(displayLimit,entries.size());i++) {
            Ledger.Entry e=entries.get(i);
            if(groupDates && !last.equals(e.date)) { if(i>0) space(parent,16); parent.addView(text(LocalDate.parse(e.date).format(DateTimeFormatter.ofPattern("M월 d일 EEEE",Locale.KOREA)),13,MUTED,true)); space(parent,10); last=e.date; }
            LinearLayout r=row(); r.setPadding(dp(14),dp(15),dp(14),dp(15)); r.setBackground(bg(SURFACE,14));
            int index=Ledger.index(e.type); TextView icon=text(index==0?"↙":index==1?"↗":"=",22,COLORS[index],true); icon.setGravity(Gravity.CENTER); icon.setBackground(bg(preferences.dark()?SUBTLE:index==0?0xFFEAF4EF:index==1?0xFFFFF0EE:0xFFF1EFF8,12)); r.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
            LinearLayout desc=column(); TextView cat=text(e.category,14,INK,true); cat.setSingleLine(true); cat.setEllipsize(TextUtils.TruncateAt.END); desc.addView(cat); space(desc,5);
            TextView memo=label(e.memo.isEmpty()?e.type:e.memo,11,MUTED); memo.setSingleLine(true); memo.setEllipsize(TextUtils.TruncateAt.END); desc.addView(memo);
            LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(0,-2,1); dl.leftMargin=dp(12); dl.rightMargin=dp(8); r.addView(desc,dl);
            TextView amount=text((index==0||e.type.equals("저축회수")?"+":"−")+number.format(e.amount),14,COLORS[index],true); amount.setGravity(Gravity.END|Gravity.CENTER_VERTICAL); amount.setSingleLine(true); amount.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,TypedValueCompat.SP); r.addView(amount,new LinearLayout.LayoutParams(dp(128),dp(36)));
            r.setContentDescription(e.date+" "+e.type+" "+e.category+" "+won(e.amount)+". 눌러서 수정"); r.setOnClickListener(v->edit(e)); parent.addView(r); space(parent,7);
        }
        if(entries.size()>displayLimit) { TextView more=button("100건 더 보기",GREEN,false,()->{displayLimit+=100; if(tab==1) refreshRecords(); else render();}); parent.addView(more); }
    }
    private void empty(LinearLayout parent,String title,String subtitle) {
        LinearLayout c=card(parent); c.setGravity(Gravity.CENTER); c.setPadding(dp(12),dp(28),dp(12),dp(28)); c.addView(text(title,15,INK,true)); space(c,10); TextView sub=label(subtitle,12,MUTED); sub.setGravity(Gravity.CENTER); c.addView(sub);
    }
    private void statistics() {
        statisticsPeriodControls();StatisticsPeriod period=statisticsPeriod();List<Ledger.Entry> periodEntries=period.entries(all);
        LinearLayout typeTabs=row();
        for(int type:new int[]{-1,0,1,2,3}){
            boolean active=statisticsType==type;LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(0,dp(8),0,dp(4));
            String title=type<0?"전체":Ledger.TYPES[type];TextView name=text(title,16,active?INK:MUTED,active);name.setGravity(Gravity.CENTER);item.addView(name);space(item,7);
            View underline=new View(this);underline.setBackgroundColor(active?PINK:Color.TRANSPARENT);item.addView(underline,new LinearLayout.LayoutParams(dp(44),dp(3)));
            item.setContentDescription(type<0?"전체 비교 통계":Ledger.TYPES[type]+" 통계");item.setSelected(active);
            item.setOnClickListener(v->{statisticsType=type;displayLimit=100;render();});typeTabs.addView(item,new LinearLayout.LayoutParams(0,dp(52),1));
        }
        page.addView(typeTabs);
        if(statisticsType<0){statisticsComparison(periodEntries);return;}
        StatisticsData.Summary data=StatisticsData.aggregate(periodEntries,Ledger.TYPES[statisticsType]);
        TextView note=label(data.hasTinyCategory?"* 1% 미만인 상세 분류가 있어요.":"",11,INK);note.setGravity(Gravity.CENTER_VERTICAL);page.addView(note,new LinearLayout.LayoutParams(-1,dp(22)));
        int kind=statisticsType;page.addView(new DonutChartView(this,data,preferences.dark()?BG:SURFACE,INK,MUTED,LINE,appFont,chartRotations[kind],new DonutChartView.Listener(){public void rotated(float angle){chartRotations[kind]=angle;}public void selected(int index){if(data.groupedCount>0&&index==data.chartCategories.size()-1){List<StatisticsData.Category> rest=data.categories.subList(StatisticsData.MAX_CHART_CATEGORIES-1,data.categories.size());String[] choices=new String[rest.size()];for(int i=0;i<rest.size();i++)choices[i]=rest.get(i).name+"  "+won(rest.get(i).amount);new AlertDialog.Builder(MainActivity.this).setTitle("그 외 상세 분류").setItems(choices,(d,i)->showCategoryTransactions(data.type,rest.get(i).name)).setNegativeButton("닫기",null).show();}else showCategoryTransactions(data.type,data.chartCategories.get(index).name);}}),new LinearLayout.LayoutParams(-1,-2));
        TextView gesture=label("원을 잡고 돌려 보세요 · 상세 분류를 누르면 상세 내역",11,MUTED);gesture.setGravity(Gravity.CENTER);page.addView(gesture);space(page,12);
        if(data.groupedCount>0){TextView grouped=label("차트의 ‘그 외 "+data.groupedCount+"개’는 아래 목록에서 각각 확인할 수 있어요.",11,MUTED);grouped.setLineSpacing(dp(3),1);page.addView(grouped);space(page,8);}
        line(page);
        statisticsAmountRow(page,statisticsTotalPrefix()+" "+data.type+" 합계",data.total,-1,true);
        for(int i=0;i<Math.min(data.categories.size(),displayLimit);i++){
            StatisticsData.Category category=data.categories.get(i);
            LinearLayout categoryRow=statisticsAmountRow(page,category.name,category.amount,DonutChartView.color(i),false);categoryRow.setOnClickListener(v->showCategoryTransactions(data.type,category.name));
        }
        if(data.categories.size()>displayLimit)page.addView(button("상세 분류 100개 더 보기",PINK,false,()->{displayLimit+=100;render();}));
        if(data.type.equals("저축"))page.addView(label("저축 합계는 납입 − 회수예요. 회수로 음수인 분류는 원형 비중에서 제외해요.",12,MUTED));
        if(data.categories.isEmpty()){space(page,18);TextView empty=label("아직 "+data.type+" 기록이 없어요.",14,MUTED);empty.setGravity(Gravity.CENTER);page.addView(empty);space(page,14);page.addView(button(data.type+" 기록하기",PINK,true,()->newEntry(data.type)));}
        long[] periodTotals=Ledger.totals(periodEntries);space(page,24);line(page);
        statisticsAmountRow(page,"선택 기간 사용 가능",Ledger.available(periodTotals),-1,false);
        page.addView(label("수입 − 지출 − 저축 − 투자",11,MUTED));space(page,8);
        double[] shares=StatisticsData.incomeShares(periodTotals);
        page.addView(label(shares==null?"수입을 기록하면 저축률·투자률·모은 비율을 볼 수 있어요.":String.format(Locale.KOREA,"수입 대비 지출 %.1f%% · 저축 %.1f%% · 투자 %.1f%%",shares[0],shares[1],shares[2]),11,MUTED));
        if(shares!=null){space(page,6);TextView kept=label(String.format(Locale.KOREA,"모은 비율 (저축+투자) %.1f%% · 모은 돈 ",shares[3])+won(StatisticsData.saved(periodTotals)),13,INK);kept.setContentDescription("수입 대비 모은 비율 "+String.format(Locale.KOREA,"%.1f%%",shares[3])+", 모은 돈 "+won(StatisticsData.saved(periodTotals)));page.addView(kept);}
        space(page,24);line(page);space(page,18);
        page.addView(text("선택 기간 합계",15,INK,true));
        for(int i=0;i<Ledger.TYPES.length;i++)statisticsAmountRow(page,Ledger.TYPES[i],periodTotals[i],COLORS[i],false);
        statisticsAmountRow(page,"모은 돈 (저축+투자)",StatisticsData.saved(periodTotals),-1,false);
        statisticsAmountRow(page,"사용 가능",Ledger.available(periodTotals),-1,false);
        TextView foot=label("저축과 투자는 지출 통계에 포함하지 않아요.",11,MUTED);page.addView(foot);
    }
    private void statisticsComparison(List<Ledger.Entry> entries) {
        StatisticsData.Comparison data=StatisticsData.compare(entries);
        space(page,18);page.addView(text("돈의 성격별 비중",18,INK,true));space(page,7);
        page.addView(label("양수인 유형 금액을 100%로 비교해요. 순저축이 회수로 음수이면 비중에서 제외해요.",11,MUTED));
        int[] colors=new int[data.chart.chartCategories.size()];
        for(int i=0;i<colors.length;i++)colors[i]=COLORS[Ledger.index(data.chart.chartCategories.get(i).name)];
        DonutChartView pie=new DonutChartView(this,data.chart,preferences.dark()?BG:SURFACE,INK,MUTED,LINE,appFont,comparisonRotation,new DonutChartView.Listener(){
            public void rotated(float angle){comparisonRotation=angle;}
            public void selected(int index){statisticsType=Ledger.index(data.chart.chartCategories.get(index).name);displayLimit=100;render();}
        },colors,"전체 비중");
        pie.setContentDescription("네 유형 비교 원형 그래프, "+statisticsPeriodLabel()+", 네 유형 금액 합계 "+won(data.total));
        page.addView(pie,new LinearLayout.LayoutParams(-1,-2));
        if(data.total==0){page.addView(label("선택한 기간에 기록이 없어요.",13,MUTED));space(page,12);}
        page.addView(text("금액 비교",17,INK,true));space(page,7);
        page.addView(label("막대는 가장 큰 금액을 기준으로 그려요. %는 전체 비중이에요.",11,MUTED));space(page,6);
        LinearLayout bars=column();bars.setContentDescription("네 유형 비교 막대그래프");
        for(int i=0;i<Ledger.TYPES.length;i++) {
            final int type=i;String percent=String.format(Locale.KOREA,"%.1f%%",data.percent(i));
            LinearLayout block=column();
            statisticsAmountRow(block,Ledger.TYPES[i]+"  "+percent,data.amount(i),COLORS[i],false);
            TypeComparisonBarView bar=new TypeComparisonBarView(this,data.barFraction(i),COLORS[i],LINE);bar.setTag(Ledger.TYPES[i]+" 비교 막대");
            block.addView(bar,new LinearLayout.LayoutParams(-1,dp(10)));space(block,10);
            block.setContentDescription(Ledger.TYPES[i]+" 비교 금액 "+won(data.amount(i))+", 전체 비중 "+percent);
            block.setOnClickListener(v->{statisticsType=type;displayLimit=100;render();});bars.addView(block);
        }
        page.addView(bars);space(page,14);
        page.addView(label("원형 조각이나 금액을 누르면 상세 분류를 볼 수 있어요.",11,MUTED));space(page,22);line(page);space(page,18);
        page.addView(text("수입 대비 배분",17,INK,true));space(page,8);
        if(data.hasIncome()) {
            for(int i=1;i<Ledger.TYPES.length;i++) {
                String percent=String.format(Locale.KOREA,"%.1f%%",data.incomePercent(i));
                LinearLayout r=row();r.setPadding(0,dp(10),0,dp(10));
                r.addView(label(Ledger.TYPES[i],14,INK),new LinearLayout.LayoutParams(0,-2,1));
                TextView rate=text(percent,15,COLORS[i],true);rate.setGravity(Gravity.END);r.addView(rate);
                r.setContentDescription("수입 대비 "+Ledger.TYPES[i]+" "+percent);page.addView(r);
            }
        } else page.addView(label("이 기간에 수입이 없어 수입 대비 비율을 계산할 수 없어요.",12,MUTED));
        space(page,12);line(page);
        statisticsAmountRow(page,"지출·저축·투자 합계",data.outgoing,-1,false);
        statisticsAmountRow(page,"선택 기간 사용 가능",data.balance,-1,false);
        page.addView(label("수입 − 지출 − 저축 − 투자",11,MUTED));space(page,8);
        page.addView(label("저축과 투자는 지출 합계에 포함하지 않아요.",11,MUTED));
    }
    private LinearLayout statisticsAmountRow(LinearLayout parent,String name,long amount,int color,boolean total){
        LinearLayout r=row();r.setPadding(0,dp(12),0,dp(12));
        if(color!=-1){View marker=new View(this);marker.setBackgroundColor(color);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(3),dp(19));mp.rightMargin=dp(8);r.addView(marker,mp);}
        TextView label=text(name,total?15:14,INK,total);label.setMaxLines(3);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=dp(9);r.addView(label,lp);
        TextView price=text(won(amount),total?16:15,INK,true);price.setSingleLine(true);price.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);price.setAutoSizeTextTypeUniformWithConfiguration(10,total?16:15,1,TypedValueCompat.SP);r.addView(price,new LinearLayout.LayoutParams(dp(145),dp(24)));
        r.setContentDescription(name+" "+won(amount));parent.addView(r);if(total)line(parent);return r;
    }
    private void showCategoryTransactions(String type,String category){
        List<Ledger.Entry> entries=new ArrayList<>();long total=0;for(Ledger.Entry e:statisticsPeriod().entries(db.all()))if(Ledger.matches(e,type)&&e.category.equals(category)){entries.add(e);total+=e.effect();}
        LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(12));form.addView(text(statisticsPeriodLabel()+" · "+entries.size()+"건 · "+won(total),14,COLORS[Ledger.index(type)],true));space(form,10);line(form);LinearLayout rows=column();form.addView(rows);ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(category+" "+type+" 내역").setView(scroll).setPositiveButton("닫기",null).create();
        final int[] shown={0};TextView more=button("100건 더 보기",GREEN,false,()->{});Runnable append=()->{rows.removeView(more);int end=Math.min(shown[0]+100,entries.size());for(int i=shown[0];i<end;i++){Ledger.Entry e=entries.get(i);LinearLayout r=column();r.setPadding(0,dp(13),0,dp(13));valueRow(r,e.date, e.amount,COLORS[Ledger.index(type)],14,true,0);if(!e.memo.isEmpty()){space(r,5);r.addView(label(e.memo,13,MUTED));}r.setContentDescription(e.date+" "+e.memo+" "+won(e.amount)+" 수정");r.setOnClickListener(v->{dialog.dismiss();edit(e);});rows.addView(r);line(rows);}shown[0]=end;if(end<entries.size())rows.addView(more);};more.setOnClickListener(v->append.run());append.run();dialog.show();
    }
    private void files() {
        backupPanel();
        space(page,12); page.addView(text("엑셀로 간편하게",25,INK,true)); space(page,10); TextView sub=label("기록은 안전하게 보관하고,\n여러 내역은 한 번에 입력하세요.",14,MUTED); sub.setLineSpacing(dp(5),1); page.addView(sub); space(page,24);
        LinearLayout backup=card(page); backup.addView(new IconView("file",GREEN),new LinearLayout.LayoutParams(dp(29),dp(29))); space(backup,13); backup.addView(text("엑셀 백업",18,INK,true)); space(backup,8); backup.addView(label("전체 "+all.size()+"건을 .xlsx 파일로 저장해요.\n돈의 성격과 상세 분류를 별도 열로 저장해요.",13,MUTED)); space(backup,18); backup.addView(button("전체 내역 백업",GREEN,true,()->chooseExport(EXPORT))); space(backup,10); backup.addView(button(month.getYear()+"년 "+month.getMonthValue()+"월만 내보내기",GREEN,false,()->chooseExport(EXPORT_MONTH)));
        LinearLayout input=card(page); input.addView(text("엑셀 한 번에 가져오기",18,INK,true)); space(input,8); TextView desc=label("양식의 ‘입력예시’에서 돈의 성격과 작성법을 확인하세요.\n금액은 모두 양수로 적으면 유형에 따라 더하거나 차감해요.\n저장 전에 오류와 중복을 확인할 수 있어요.",13,MUTED); desc.setLineSpacing(dp(5),1); input.addView(desc); space(input,18); input.addView(button("엑셀 파일 가져오기",GREEN,true,this::chooseImport)); space(input,10); input.addView(button("빈 입력 양식 내려받기",GREEN,false,()->chooseExport(TEMPLATE)));
        LinearLayout info=card(page); info.addView(text("알아두세요",14,INK,true)); space(info,12);
        TextView note=label("• 돈의 성격은 수입·지출·저축·투자·저축회수, 상세 분류는 월급·식비·적금·주식 등이에요.\n\n• 새 입력 양식의 금액은 모두 양수로 적으세요. 백업은 수입·저축회수(+), 지출·저축·투자(−)로 표시해요. 저축회수는 순저축에서 빼고 사용 가능 금액에 더해요. 예전 양식도 읽어요.\n\n• .xlsx 파일을 지원해요. 한 번에 최대 10,000건을 가져와요.\n\n• 백업 파일의 고유ID가 같으면 중복을 건너뛰어요. ID가 없으면 날짜·돈의 성격·금액·상세 분류·메모를 비교해요.\n\n• 오류나 ID 충돌이 있으면 전체를 추가하지 않아요. 기존 내역은 지우지 않아요.\n\n• 기록은 이 휴대폰에만 저장돼요. 앱을 삭제하면 기록도 삭제되니, 백업 파일을 따로 보관하세요.",12,MUTED); note.setLineSpacing(dp(4),1); info.addView(note);
        TextView version=label("차곡가계부 "+appVersion()+"  ·  로그인 없이, 광고 없이",11,MUTED); version.setGravity(Gravity.CENTER); page.addView(version);
    }
    private EditText field(LinearLayout parent,String title,String value,int inputType) {
        parent.addView(text(title,12,MUTED,true)); space(parent,7); EditText edit=new EditText(this);edit.setContentDescription(title); edit.setTextSize(15); edit.setTextColor(INK); edit.setPadding(dp(12),dp(10),dp(12),dp(10)); edit.setBackground(bg(INPUT,10)); edit.setInputType(inputType); edit.setTypeface(appFont); edit.setHintTextColor(MUTED); edit.setText(value); parent.addView(edit,new LinearLayout.LayoutParams(-1,dp(47))); space(parent,15); return edit;
    }
    private LocalDate newRecordDate(){if(tab==0)return selected;LocalDate today=LocalDate.now();if(tab==2){StatisticsPeriod period=statisticsPeriod();return period.contains(today)?today:period.from;}if(tab==4||tab==5||(tab==1&&recordsAll))return today;return YearMonth.from(today).equals(month)?today:month.atDay(1);}
    private static final class NewEntryDraft {
        LocalDate date;String type,amount="0",category="",memo="",repeatId,occurrence,entryId;boolean saved;
        NewEntryDraft(LocalDate date,String type){this.date=date;this.type=type;}
    }
    private void newEntry(){newEntry("지출");}
    private void newEntry(String type){if(busy)return;entryAmountStep(new NewEntryDraft(newRecordDate(),Ledger.normalizeType(type)));}
    private void addNumberPad(LinearLayout form,AmountKeypad draft,TextView error,Runnable refresh){
        String[] keys={"1","2","3","4","5","6","7","8","9","0","00","⌫"};
        for(int r=0;r<4;r++){LinearLayout line=row();for(int c=0;c<3;c++){String key=keys[r*3+c];FrameLayout slot=new FrameLayout(this);TextView digit=text(key,22,INK,true);digit.setGravity(Gravity.CENTER);GradientDrawable circle=bg(INPUT,40);circle.setStroke(dp(1),LINE);digit.setBackground(circle);digit.setContentDescription("⌫".equals(key)?"키패드 한 자리 지우기":"키패드 "+key);digit.setOnClickListener(v->{if(draft.press(key))refresh.run();else error.setText("금액은 최대 12자리까지 입력할 수 있어요.");});if("⌫".equals(key))digit.setOnLongClickListener(v->{draft.clear();refresh.run();return true;});slot.addView(digit,new FrameLayout.LayoutParams(dp(58),dp(58),Gravity.CENTER));line.addView(slot,new LinearLayout.LayoutParams(0,dp(68),1));}form.addView(line);}
    }
    private void inputTypeLabel(TextView view){view.setMaxLines(1);view.setHorizontallyScrolling(false);view.setAutoSizeTextTypeUniformWithConfiguration(8,14,1,TypedValueCompat.SP);}
    private void entryAmountStep(NewEntryDraft draft){
        if(isFinishing()||isDestroyed()||draft.saved)return;final AlertDialog[] amountDialog={null};AmountKeypad keypad=new AmountKeypad(draft.amount);LinearLayout form=column();form.setPadding(dp(20),0,dp(20),dp(8));
        TextView date=text(draft.date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E)",Locale.KOREA)),14,INK,true);date.setGravity(Gravity.CENTER);date.setContentDescription("새 기록 날짜 선택");date.setOnClickListener(v->{LocalDate initial=draft.date;datePicker(initial,"날짜 선택",(view,y,m,d)->{draft.date=LocalDate.of(y,m+1,d);date.setText(draft.date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E)",Locale.KOREA)));}).show();});form.addView(date,new LinearLayout.LayoutParams(-1,dp(32)));
        form.addView(label("돈의 성격 · 저축회수는 기존 저축을 돌려받을 때",12,MUTED));space(form,8);LinearLayout types=row();TextView[] tabs=new TextView[Ledger.INPUT_TYPES.length];TextView amount=text("",30,INK,true);amount.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);amount.setMaxLines(1);amount.setHorizontallyScrolling(false);amount.setAutoSizeTextTypeUniformWithConfiguration(8,30,1,TypedValueCompat.SP);amount.setContentDescription("키패드 금액");
        Runnable updateTypes=()->{for(int i=0;i<Ledger.INPUT_TYPES.length;i++){boolean active=Ledger.INPUT_TYPES[i].equals(draft.type);tabs[i].setTextColor(active?Color.WHITE:COLORS[Ledger.index(Ledger.INPUT_TYPES[i])]);tabs[i].setBackground(bg(active?COLORS[Ledger.index(Ledger.INPUT_TYPES[i])]:SUBTLE,10));tabs[i].setSelected(active);}amount.setTextColor(COLORS[Ledger.index(draft.type)]);if(amountDialog[0]!=null)amountDialog[0].setTitle(draft.type+" 금액 입력");};
        for(int i=0;i<Ledger.INPUT_TYPES.length;i++){final int index=i;TextView type=text(Ledger.INPUT_TYPES[i],14,COLORS[Ledger.index(Ledger.INPUT_TYPES[i])],true);type.setGravity(Gravity.CENTER);inputTypeLabel(type);type.setContentDescription(Ledger.INPUT_TYPES[i]+" 새 기록 돈의 성격");tabs[i]=type;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);if(i>0)p.leftMargin=dp(4);types.addView(type,p);type.setOnClickListener(v->{String next=Ledger.INPUT_TYPES[index];if(!next.equals(draft.type))draft.category="";draft.type=next;updateTypes.run();});}form.addView(types);space(form,9);form.addView(amount,new LinearLayout.LayoutParams(-1,dp(47)));TextView error=label("",11,RED);form.addView(error);TextView clear=label("전체 지우기",12,MUTED);clear.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);form.addView(clear,new LinearLayout.LayoutParams(-1,dp(30)));Runnable refresh=()->{draft.amount=keypad.digits();amount.setText(number.format(keypad.value())+" 원");error.setText("");};clear.setOnClickListener(v->{keypad.clear();refresh.run();});addNumberPad(form,keypad,error,refresh);refresh.run();updateTypes.run();
        ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(draft.type+" 금액 입력").setView(scroll).setNegativeButton("취소",null).setPositiveButton("적용",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{Ledger.parseAmount(draft.amount);dialog.dismiss();entryDetailsStep(draft);}catch(Exception ex){error.setText("금액은 1원 이상 입력하세요.");}}));amountDialog[0]=dialog;dialog.show();
    }
    private void entryDetailsStep(NewEntryDraft draft){
        if(isFinishing()||isDestroyed()||draft.saved)return;LinearLayout form=column();form.setPadding(dp(22),dp(8),dp(22),dp(12));TextView summary=text(draft.date+" · "+draft.type+"\n"+won(Long.parseLong(draft.amount)),18,COLORS[Ledger.index(draft.type)],true);summary.setLineSpacing(dp(5),1);form.addView(summary);space(form,20);EditText category=field(form,"상세 분류",draft.category,android.text.InputType.TYPE_CLASS_TEXT);category.setSingleLine(true);category.setHint(draft.type+"의 상세 분류를 선택하거나 입력하세요");category.setContentDescription("새 기록 상세 분류");category.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});LinearLayout actions=row();actions.addView(categoryAction("상세 분류 선택",()->chooseCategory(draft.type,category,true)),new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams add=new LinearLayout.LayoutParams(0,dp(48),1);add.leftMargin=dp(4);actions.addView(categoryAction("+ 상세 분류 추가",()->addCategory(draft.type,category)),add);form.addView(actions);space(form,15);EditText memo=field(form,"메모 (선택)",draft.memo,android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);memo.setContentDescription("새 기록 메모");memo.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});form.addView(label(draft.type.equals("저축회수")?"기존 저축 상세 분류를 선택하세요. 수입에 더하지 않고 순저축에서 빼며 사용 가능 금액에 돌려놓아요.":"금액이나 돈의 성격을 바꾸려면 이전을 누르세요.",12,MUTED));
        ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("새 기록").setView(scroll).setNeutralButton("취소",null).setNegativeButton("이전",null).setPositiveButton("저장",null).create();Runnable remember=()->{draft.category=category.getText().toString();draft.memo=memo.getText().toString();};Runnable back=()->{remember.run();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(category.getWindowToken(),0);dialog.dismiss();entryAmountStep(draft);};dialog.setOnCancelListener(d->{remember.run();entryAmountStep(draft);});dialog.setOnShowListener(d->{dialog.getButton(-2).setOnClickListener(v->back.run());dialog.getButton(-1).setOnClickListener(v->{if(draft.saved)return;if(category.getText().toString().trim().isEmpty()){category.setError("상세 분류를 선택하거나 입력하세요.");return;}remember.run();try{Ledger.Entry entry=new Ledger.Entry(draft.entryId==null?UUID.randomUUID().toString():draft.entryId,draft.date.toString(),draft.type,Ledger.parseAmount(draft.amount),draft.category,draft.memo,0);dialog.getButton(-1).setEnabled(false);if(draft.repeatId==null)db.save(entry,false);else db.saveOccurrence(entry,draft.repeatId,draft.occurrence);draft.saved=true;selected=draft.date;month=YearMonth.from(selected);if(tab==2)saveStatisticsPeriod();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(memo.getWindowToken(),0);dialog.dismiss();render();toast("저장했어요.");}catch(Exception ex){dialog.getButton(-1).setEnabled(true);category.setError(ex.getMessage());}});});dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void amountKeypad(EditText target,String date,String type){
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(target.getWindowToken(),0);AmountKeypad draft=new AmountKeypad(target.getText().toString());LinearLayout form=column();form.setPadding(dp(20),dp(5),dp(20),dp(10));TextView when=label(date+" · "+type,13,MUTED);when.setGravity(Gravity.CENTER);form.addView(when);space(form,14);TextView amount=text("",30,COLORS[Ledger.index(type)],true);amount.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);amount.setSingleLine(true);amount.setAutoSizeTextTypeUniformWithConfiguration(17,30,1,TypedValueCompat.SP);amount.setContentDescription("키패드 금액");form.addView(amount,new LinearLayout.LayoutParams(-1,dp(47)));TextView error=label("",11,RED);form.addView(error);TextView clear=label("전체 지우기",12,MUTED);clear.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);clear.setMinHeight(dp(35));form.addView(clear,new LinearLayout.LayoutParams(-1,dp(35)));Runnable refresh=()->{amount.setText(number.format(draft.value())+" 원");error.setText("");};clear.setOnClickListener(v->{draft.clear();refresh.run();});
        String[] keys={"1","2","3","4","5","6","7","8","9","0","00","⌫"};for(int r=0;r<4;r++){LinearLayout line=row();for(int c=0;c<3;c++){String key=keys[r*3+c];FrameLayout slot=new FrameLayout(this);TextView digit=text(key,22,INK,true);digit.setGravity(Gravity.CENTER);GradientDrawable circle=bg(INPUT,40);circle.setStroke(dp(1),LINE);digit.setBackground(circle);digit.setContentDescription("⌫".equals(key)?"키패드 한 자리 지우기":"키패드 "+key);digit.setOnClickListener(v->{if(draft.press(key))refresh.run();else error.setText("금액은 최대 12자리까지 입력할 수 있어요.");});if("⌫".equals(key))digit.setOnLongClickListener(v->{draft.clear();refresh.run();return true;});slot.addView(digit,new FrameLayout.LayoutParams(dp(62),dp(62),Gravity.CENTER));line.addView(slot,new LinearLayout.LayoutParams(0,dp(73),1));}form.addView(line);}refresh.run();ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(type+" 금액 입력").setView(scroll).setNegativeButton("취소",null).setPositiveButton("적용",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{Ledger.parseAmount(draft.digits());target.setText(draft.digits());target.setSelection(target.length());dialog.dismiss();}catch(Exception ex){error.setText("금액은 1원 이상 입력하세요.");}}));dialog.show();
    }
    private void edit(Ledger.Entry entry) {
        if(busy) return;
        if(entry==null){newEntry();return;}
        if(entry.id.startsWith("move:")){String[] parts=entry.id.split(":");SavingsMovement movement=parts.length>1?db.movement(parts[1]):null;if(movement!=null){movementEditor(movement);return;}}
        
        boolean updating=entry!=null;
        LinearLayout form=column(); form.setPadding(dp(22),dp(10),dp(22),dp(15));
        LinearLayout switches=row(); final String[] chosen={updating?entry.type:(tab==2&&statisticsType>=0?Ledger.INPUT_TYPES[statisticsType]:"지출")}; final TextView[] tabs=new TextView[Ledger.INPUT_TYPES.length];
        Runnable updateTabs=()->{for(int i=0;i<Ledger.INPUT_TYPES.length;i++){boolean on=Ledger.INPUT_TYPES[i].equals(chosen[0]);tabs[i].setTextColor(on?Color.WHITE:COLORS[Ledger.index(Ledger.INPUT_TYPES[i])]);tabs[i].setBackground(bg(on?COLORS[Ledger.index(Ledger.INPUT_TYPES[i])]:SUBTLE,10));tabs[i].setSelected(on);}};
        for(int i=0;i<Ledger.INPUT_TYPES.length;i++) { final int index=i; TextView t=text(Ledger.INPUT_TYPES[i],14,COLORS[Ledger.index(Ledger.INPUT_TYPES[i])],true); t.setGravity(Gravity.CENTER); inputTypeLabel(t);t.setContentDescription(Ledger.INPUT_TYPES[i]+" 수정 돈의 성격"); tabs[i]=t; LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1); if(i>0)p.leftMargin=dp(4); switches.addView(t,p); t.setOnClickListener(v->{chosen[0]=Ledger.INPUT_TYPES[index];updateTabs.run();}); }
        form.addView(label("돈의 성격",12,MUTED));space(form,8);updateTabs.run(); form.addView(switches); space(form,20);
        EditText amount=field(form,"금액 (원)",updating?String.valueOf(entry.amount):"",android.text.InputType.TYPE_CLASS_NUMBER); amount.setHint("0"); amount.setTextSize(24); amount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        LocalDate defaultDate=updating?LocalDate.parse(entry.date):newRecordDate();
        EditText date=field(form,"날짜",defaultDate.toString(),android.text.InputType.TYPE_CLASS_TEXT); date.setFocusable(false); date.setContentDescription("날짜 선택");
        amount.setShowSoftInputOnFocus(false);amount.setContentDescription("금액 입력 · 숫자 키패드 열기");amount.setOnClickListener(v->amountKeypad(amount,date.getText().toString(),chosen[0]));
        date.setOnClickListener(v->{LocalDate d=LocalDate.parse(date.getText().toString()); datePicker(d,"날짜 선택",(view,y,m,day)->date.setText(LocalDate.of(y,m+1,day).toString())).show();});
        EditText category=field(form,"상세 분류",updating?entry.category:"",android.text.InputType.TYPE_CLASS_TEXT); category.setHint("예: 월급, 식비, 적금, 주식"); category.setSingleLine(true); category.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        LinearLayout categoryActions=row();
        TextView choose=categoryAction("상세 분류 선택",()->chooseCategory(chosen[0],category));
        TextView add=categoryAction("+ 상세 분류 추가",()->addCategory(chosen[0],category));
        categoryActions.addView(choose,new LinearLayout.LayoutParams(0,dp(48),1));
        LinearLayout.LayoutParams addCategoryParams=new LinearLayout.LayoutParams(0,dp(48),1); addCategoryParams.leftMargin=dp(8); categoryActions.addView(add,addCategoryParams);
        form.addView(categoryActions); space(form,15);
        EditText memo=field(form,"메모 (선택)",updating?entry.memo:"",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); memo.setHint("어떤 기록인가요?"); memo.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        TextView hint=label("저축·투자는 사용 가능 금액에서 차감해요. 저축회수는 수입에 더하지 않고 순저축에서 빼며 사용 가능 금액에 돌려놓아요.",11,MUTED); form.addView(hint);
        final AlertDialog[] sourceEditor={null};
        if(updating){space(form,12);form.addView(button("이 기록을 오늘 날짜로 복사",GREEN,false,()->{try{Ledger.Entry copy=new Ledger.Entry("",date.getText().toString(),chosen[0],Ledger.parseAmount(amount.getText().toString()),category.getText().toString(),memo.getText().toString(),0);hideKeyboard();sourceEditor[0].dismiss();copyEntry(copy);}catch(Exception ex){amount.setError(message(ex));}}));space(form,8);form.addView(button("자주 쓰는 기록에 저장",GREEN,false,()->{try{favoriteName(new Ledger.Entry("",date.getText().toString(),chosen[0],Ledger.parseAmount(amount.getText().toString()),category.getText().toString(),memo.getText().toString(),0));}catch(Exception ex){error("기록을 확인해 주세요",message(ex));}}));}
        ScrollView scroll=new ScrollView(this); scroll.addView(form);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(updating?"기록 수정":"새 기록").setView(scroll).setNegativeButton("취소",null).setPositiveButton("저장",null);
        if(updating) builder.setNeutralButton("삭제",null);
        AlertDialog dialog=builder.create();sourceEditor[0]=dialog; dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try {
                    Ledger.Entry next=new Ledger.Entry(updating?entry.id:UUID.randomUUID().toString(),date.getText().toString(),chosen[0],Ledger.parseAmount(amount.getText().toString()),category.getText().toString(),memo.getText().toString(),0);
                    db.save(next,updating); selected=LocalDate.parse(next.date); month=YearMonth.from(selected);if(tab==2)saveStatisticsPeriod(); hideKeyboard(); dialog.dismiss(); render(); toast("저장했어요.");
                } catch(Exception ex){amount.setError(ex.getMessage());}
            });
            if(updating) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new AlertDialog.Builder(this).setTitle("이 기록을 삭제할까요?").setMessage(entry.category+"  "+won(entry.amount)).setNegativeButton("취소",null).setPositiveButton("삭제",(a,b)->{try{db.delete(entry.id);dialog.dismiss();render();toast("삭제했어요.");}catch(Exception ex){error("삭제하지 못했어요",message(ex));}}).show());
        });
        dialog.show(); dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void chooseCategory(String type,EditText category) {
        chooseCategory(type,category,false);
    }
    private void chooseCategory(String type,EditText category,boolean clearDeleted) {
        hideKeyboard();
        List<String> choices=categories.choices(type,db.all());
        String current=category.getText().toString().trim();
        if(!current.isEmpty() && !choices.contains(current)&&!categories.deleted(type).contains(current)) choices.add(0,current);
        new AlertDialog.Builder(this).setTitle(type+" 상세 분류 선택")
            .setItems(choices.toArray(new String[0]),(d,index)->category.setText(choices.get(index)))
            .setNegativeButton("취소",null)
            .setNeutralButton("상세 분류 관리",(d,w)->manageCategories(type,(kind,name)->{if(clearDeleted&&kind.equals(CategoryStore.kind(type))&&category.getText().toString().trim().equals(name))category.setText("");}))
            .setPositiveButton("새 상세 분류 추가",(d,w)->addCategory(type,category)).show();
    }
    private void manageCategories(String initial,java.util.function.BiConsumer<String,String> removed){
        final String[] type={CategoryStore.kind(initial)};LinearLayout form=column();form.setPadding(dp(18),dp(8),dp(18),dp(8));form.addView(label("상세 분류만 삭제하며 기존 기록과 합계는 유지해요.",12,MUTED));space(form,12);LinearLayout tabs=row(),list=column();TextView[] labels=new TextView[Ledger.TYPES.length];final Runnable[] refresh={null};refresh[0]=()->{for(int i=0;i<Ledger.TYPES.length;i++){boolean active=Ledger.TYPES[i].equals(type[0]);labels[i].setTextColor(active?Color.WHITE:COLORS[i]);labels[i].setBackground(bg(active?COLORS[i]:SUBTLE,10));labels[i].setSelected(active);}list.removeAllViews();List<String> choices=categories.choices(type[0],db.all());if(choices.isEmpty()){space(list,20);list.addView(label("상세 분류가 없어요. 새 상세 분류를 추가하거나 복원하세요.",13,MUTED));}for(String name:choices){LinearLayout r=row();r.setPadding(0,dp(4),0,dp(4));TextView title=text(name,14,INK,true);title.setMaxLines(3);r.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView delete=button("삭제",RED,false,()->{});delete.setPadding(dp(12),dp(10),dp(12),dp(10));delete.setContentDescription(type[0]+" "+name+" 상세 분류 삭제");delete.setOnClickListener(v->{String kind=type[0];try{categories.delete(kind,name);if(removed!=null)removed.accept(kind,name);refresh[0].run();}catch(Exception ex){error("상세 분류를 삭제하지 못했어요",message(ex));}});r.addView(delete,new LinearLayout.LayoutParams(dp(65),dp(44)));list.addView(r);}};
        for(int i=0;i<Ledger.TYPES.length;i++){final int index=i;TextView label=text(Ledger.TYPES[i],14,COLORS[i],true);label.setGravity(Gravity.CENTER);label.setContentDescription(Ledger.TYPES[i]+" 상세 분류 관리");labels[i]=label;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(40),1);if(i>0)p.leftMargin=dp(6);tabs.addView(label,p);label.setOnClickListener(v->{type[0]=Ledger.TYPES[index];refresh[0].run();});}form.addView(tabs);space(form,12);form.addView(list);ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("상세 분류 관리").setView(scroll).setPositiveButton("닫기",null).setNeutralButton("삭제한 상세 분류 복원",null).create();dialog.setOnShowListener(d->dialog.getButton(-3).setOnClickListener(v->{List<String> deleted=categories.deleted(type[0]);if(deleted.isEmpty()){toast("삭제한 상세 분류가 없어요.");return;}new AlertDialog.Builder(this).setTitle(type[0]+" 상세 분류 복원").setItems(deleted.toArray(new String[0]),(window,index)->{categories.remember(type[0],deleted.get(index));refresh[0].run();}).setNegativeButton("취소",null).show();}));refresh[0].run();dialog.show();
    }
    private void addCategory(String type,EditText category) {
        LinearLayout form=column(); form.setPadding(dp(22),dp(12),dp(22),dp(6));
        EditText name=field(form,"상세 분류 이름","",android.text.InputType.TYPE_CLASS_TEXT);
        name.setSingleLine(true); name.setHint("예: 반려동물, 보험, 운동");
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        TextView note=label(type+" 상세 분류에 저장하고 다음에도 선택할 수 있어요.",12,MUTED); form.addView(note);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(type+" 상세 분류 추가").setView(form)
            .setNegativeButton("취소",null).setPositiveButton("추가",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                boolean added=categories.remember(type,name.getText().toString());
                category.setText(name.getText().toString().trim());
                ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(name.getWindowToken(),0);
                dialog.dismiss(); if(!added) toast("이미 있는 상세 분류를 선택했어요.");
            } catch(Exception ex) { name.setError(ex.getMessage()); }
        }));
        dialog.show();
    }
    private void chooseExport(int mode) {
        if(busy) return;
        pendingExportMonth=month;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType(MIME);
        String name=mode==TEMPLATE?"차곡_입력양식.xlsx":mode==EXPORT_MONTH?"차곡_"+month+".xlsx":"차곡_전체백업_"+LocalDate.now()+".xlsx";
        intent.putExtra(Intent.EXTRA_TITLE,name);
        try{startActivityForResult(intent,mode);}catch(ActivityNotFoundException e){error("파일 앱을 찾을 수 없어요","휴대폰의 파일 선택 앱을 활성화해 주세요.");}
    }
    private void chooseImport() {
        if(busy) return;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{MIME,"application/octet-stream","application/zip"});
        try{startActivityForResult(intent,IMPORT);}catch(ActivityNotFoundException e){error("파일 앱을 찾을 수 없어요","휴대폰의 파일 선택 앱을 활성화해 주세요.");}
    }
    @Override public void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data); if(result!=RESULT_OK || data==null || data.getData()==null) return;
        if(preferences.hasPin()&&!unlocked){pendingRequest=request;pendingResult=result;pendingIntent=data;return;}
        Uri uri=data.getData();
        if(request==ARCHIVE_IMPORT){loadArchive(uri);return;}
        if(request==ARCHIVE_EXPORT){
            setBusy("계획과 내역을 백업하고 있어요");io.execute(()->{try{BackupArchive.Snapshot snapshot=db.snapshot();try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("파일을 열 수 없어요.");BackupArchive.write(out,snapshot.entries,snapshot.planning,snapshot.movements,snapshot.openingAssets);}runOnUiThread(()->{backupStatus.success("계획 포함 백업",snapshot.entries.size());clearBusy();render();toast("계획과 내역을 백업했어요.");});}catch(Exception ex){runOnUiThread(()->{clearBusy();error("백업하지 못했어요",message(ex));});}});return;
        }
        if(request==IMPORT) loadImport(uri);
        else if(request==EXPORT || request==TEMPLATE || request==EXPORT_MONTH) {
            BackupArchive.Snapshot exportSnapshot=db.snapshot();
            List<Ledger.Entry> exportData=request==TEMPLATE?new ArrayList<>():request==EXPORT_MONTH?Ledger.month(exportSnapshot.entries,pendingExportMonth==null?month:pendingExportMonth):exportSnapshot.entries;
            setBusy("엑셀 파일을 저장하고 있어요");
            io.execute(()->{
                try {
                    try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")){if(output==null)throw new IOException("파일을 열 수 없어요.");XlsxIO.write(output,exportData,request==TEMPLATE,request==TEMPLATE?Collections.emptyList():exportSnapshot.openingAssets);}
                    runOnUiThread(()->{if(request==EXPORT)backupStatus.success("엑셀 전체 내역",exportData.size());clearBusy();if(tab==3)render();toast(request==TEMPLATE?"입력 양식을 저장했어요.":exportData.size()+"건을 백업했어요.");});
                } catch(Exception ex) {runOnUiThread(()->{clearBusy();error("저장하지 못했어요",message(ex)+"\n만들어진 빈 파일이 있다면 삭제한 뒤 다시 시도해 주세요.");});}
            });
        }
    }
    private void loadImport(Uri uri) {
        setBusy("엑셀 내역을 확인하고 있어요");
        io.execute(()->{
            try(InputStream input=getContentResolver().openInputStream(uri)) {
                if(input==null) throw new IOException("파일을 열 수 없어요.");
                XlsxIO.ReadResult read=XlsxIO.read(input); Ledger.ImportPlan plan=db.planImport(read.entries,read.openingAssets);
                runOnUiThread(()->{clearBusy();preview(read,plan);});
            } catch(Exception ex) {runOnUiThread(()->{clearBusy();error("가져오지 못했어요",message(ex));});}
        });
    }
    private void preview(XlsxIO.ReadResult read,Ledger.ImportPlan plan) {
        if(isFinishing() || isDestroyed()) return;
        if(preferences.hasPin()&&!unlocked){pendingRead=read;pendingPlan=plan;return;}
        if(read.invalidRows>0 || !plan.errors.isEmpty()) {
            List<String> errors=new ArrayList<>(read.errors); errors.addAll(plan.errors);
            error("파일을 수정해 주세요","오류 "+(read.invalidRows+plan.errors.size())+"건을 찾았어요. 아직 아무 내역도 추가하지 않았어요.\n\n"+joinErrors(errors)+"\n\n엑셀에서 수정한 뒤 다시 가져와 주세요."); return;
        }
        if(plan.additions.isEmpty()&&plan.openingAdditions.isEmpty()) {error("추가할 내역이 없어요",plan.duplicates>0?"이미 저장된 "+plan.duplicates+"건은 중복으로 건너뛰었어요.":"내역 시트의 두 번째 행부터 기록을 입력해 주세요.");return;}
        long[] t=Ledger.totals(plan.additions);
        StringBuilder summary=new StringBuilder("새 내역 "+plan.additions.size()+"건\n중복 제외 "+plan.duplicates+"건\n\n");
        for(int i=0;i<Ledger.TYPES.length;i++) summary.append(Ledger.TYPES[i]).append("  ").append(won(t[i])).append('\n');
        if(!read.openingAssets.isEmpty())summary.append("기초자산 추가 ").append(plan.openingAdditions.size()).append("건 · 중복 ").append(plan.openingDuplicates).append("건\n수입·저축 합계에는 더하지 않아요.\n");
        summary.append("\n미리보기\n");
        for(int i=0;i<Math.min(5,plan.additions.size());i++){Ledger.Entry e=plan.additions.get(i);summary.append(e.date).append("  ").append(e.type).append("  ").append(won(e.amount)).append("\n").append(e.category).append(e.memo.isEmpty()?"":" · "+e.memo).append("\n\n");}
        for(int i=0;i<Math.min(5,plan.openingAdditions.size());i++){OpeningAsset a=plan.openingAdditions.get(i);summary.append("기초자산 ").append(a.date).append(" · ").append(a.kind).append(" · ").append(a.category).append(" · ").append(won(a.amount)).append("\n");}
        if(plan.additions.size()>5) summary.append("외 ").append(plan.additions.size()-5).append("건\n\n");
        summary.append("기존 내역에 추가하며, 기존 내역은 지우지 않아요.");
        new AlertDialog.Builder(this).setTitle("이 내역을 가져올까요?").setMessage(summary.toString()).setNegativeButton("취소",null).setPositiveButton((plan.additions.size()+plan.openingAdditions.size())+"건 추가",(d,w)->{
            setBusy("내역을 저장하고 있어요");
            io.execute(()->{
                try {
                    Ledger.ImportPlan committed=db.importAtomic(read.entries,read.openingAssets);
                    runOnUiThread(()->{clearBusy();if(!committed.errors.isEmpty()){error("가져오지 못했어요",joinErrors(committed.errors));return;} if(!committed.additions.isEmpty()){month=YearMonth.from(LocalDate.parse(committed.additions.get(0).date));selected=month.atDay(1);}render();toast((committed.additions.size()+committed.openingAdditions.size())+"건을 추가했어요.");});
                } catch(Exception ex){runOnUiThread(()->{clearBusy();error("저장하지 못했어요",message(ex));});}
            });
        }).show();
    }
    private String joinErrors(List<String> errors) { StringBuilder out=new StringBuilder();for(int i=0;i<Math.min(20,errors.size());i++)out.append(errors.get(i)).append('\n'); if(errors.size()>20)out.append("외 ").append(errors.size()-20).append("건");return out.toString(); }
    private String message(Exception e) { return e.getMessage()==null?"파일을 확인한 뒤 다시 시도해 주세요.":e.getMessage(); }
    private void setBusy(String title) { busy=true;LinearLayout l=row();l.setPadding(dp(24),dp(24),dp(24),dp(24));l.addView(new ProgressBar(this),new LinearLayout.LayoutParams(dp(28),dp(28)));TextView t=label(title,14,INK);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.leftMargin=dp(16);l.addView(t,p);progress=new AlertDialog.Builder(this).setView(l).setCancelable(false).create();progress.show(); }
    private void clearBusy() { busy=false;if(progress!=null){progress.dismiss();progress=null;} }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_SHORT).show(); }
    private void error(String title,String value) {if(preferences.hasPin()&&!unlocked){pendingErrorTitle=title;pendingError=value;return;} if(!isFinishing() && !isDestroyed())new AlertDialog.Builder(this).setTitle(title).setMessage(value).setPositiveButton("확인",null).show(); }

    private void planningShortcut(){
        PlanningData plan=db.planning();LinearLayout c=card(page);c.addView(text("이번 달 계획",18,INK,true));space(c,8);
        List<PlanningData.Limit> budgets=plan.effective(YearMonth.now(),"지출");for(PlanningData.Limit l:budgets)if(l.category.isEmpty()){PlanningData.Progress p=PlanningData.progress(l,YearMonth.now(),all);c.addView(label("생활비 "+won(p.actual)+" / "+won(p.target)+" · "+(p.remaining>=0?"남은 예산 "+won(p.remaining):"예산 초과 "+won(-p.remaining)),13,p.remaining<0?RED:MUTED));}
        long due=plan.repeats.stream().filter(r->r.due(LocalDate.now())).count();if(due>0){space(c,7);c.addView(label("확인할 반복 기록 "+due+"건",14,RED));}
        space(c,12);c.addView(button("예산·목표·반복 관리",GREEN,true,()->{month=YearMonth.now();navigate(7);}));space(c,8);c.addView(button("기초자산·현재 보유",GREEN,false,this::openingList));space(c,8);c.addView(button("자주 쓰는 기록",GREEN,false,this::pickFavorite));space(c,8);c.addView(button("월별 추이·전월 비교",GREEN,false,()->{month=YearMonth.now();trendSelected=month;navigate(8);}));
        if(backupStatus.stale(System.currentTimeMillis())){space(c,12);c.addView(button("전체 백업을 확인해 주세요",RED,false,()->navigate(3)));}
    }
    private Spinner spinner(LinearLayout form,String title,String[] values,String current){
        form.addView(label(title,12,MUTED));Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);for(int i=0;i<values.length;i++)if(values[i].equals(current))spinner.setSelection(i);spinner.setContentDescription(title);form.addView(spinner,new LinearLayout.LayoutParams(-1,dp(48)));space(form,10);return spinner;
    }
    private LinearLayout planForm(){LinearLayout form=column();form.setPadding(dp(20),dp(10),dp(20),dp(12));return form;}
    private void planDialog(String title,LinearLayout form,Runnable save,Runnable remove){
        TextView error=label("",12,RED);form.addView(error);ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("취소",null).setPositiveButton("저장",null);if(remove!=null)b.setNeutralButton("삭제",null);AlertDialog d=b.create();d.setOnShowListener(v->{d.getButton(-1).setOnClickListener(w->{try{save.run();hideKeyboard();d.dismiss();render();}catch(Exception ex){error.setText(message(ex));}});if(remove!=null)d.getButton(-3).setOnClickListener(w->new AlertDialog.Builder(this).setTitle("이 설정을 삭제할까요?").setMessage("이미 저장한 거래 내역은 유지해요.").setNegativeButton("취소",null).setPositiveButton("삭제",(x,y)->{remove.run();d.dismiss();render();}).show());});d.show();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void planningPage(){
        monthBar();LinearLayout tabs=row();String[] names={"예산","납입 목표","반복 기록","자주 쓰는"};
        for(int i=0;i<names.length;i++){final int index=i;TextView t=button(names[i],planSection==i?GREEN:MUTED,planSection==i,()->{planSection=index;render();});t.setPadding(dp(3),dp(8),dp(3),dp(8));t.setTextSize(12);t.setContentDescription("계획 "+names[i]);t.setSelected(planSection==i);tabs.addView(t,new LinearLayout.LayoutParams(0,-2,1));}page.addView(tabs);space(page,18);
        PlanningData plan=db.planning();
        if(planSection==0){page.addView(text("지출 예산",22,INK,true));space(page,7);page.addView(label("저축과 투자는 지출 예산에 포함하지 않아요. 월 한도와 상세 분류 한도는 각각 비교해요.",13,MUTED));space(page,12);page.addView(button("지출 예산 추가",GREEN,true,()->limitEditor(null,"지출")));space(page,15);limitCards(plan,"지출");}
        else if(planSection==1){page.addView(text("저축·투자 납입 목표",22,INK,true));space(page,7);page.addView(label("선택한 달에 실제 기록한 납입액으로 달성률을 계산해요. 잔액·투자 수익률을 뜻하지 않아요.",13,MUTED));space(page,12);page.addView(button("저축 목표 추가",PURPLE,true,()->limitEditor(null,"저축")));space(page,8);page.addView(button("투자 목표 추가",COLORS[3],true,()->limitEditor(null,"투자")));space(page,15);limitCards(plan,"저축");limitCards(plan,"투자");}
        else if(planSection==2){page.addView(text("반복 기록",22,INK,true));space(page,7);page.addView(label("예정일에 확인하고 저장해야 실제 합계에 들어가요. 말일 일정은 짧은 달의 마지막 날로 맞춰요.",13,MUTED));space(page,12);page.addView(button("반복 기록 추가",GREEN,true,()->repeatEditor(null)));space(page,15);
            List<PlanningData.Repeat> repeats=new ArrayList<>(plan.repeats);repeats.sort(Comparator.comparing(r->r.next));if(repeats.isEmpty())page.addView(label("월급·월세·보험·적금 등 정기 기록을 등록하세요.",14,MUTED));
            for(PlanningData.Repeat r:repeats){LinearLayout c=card(page);c.addView(text(r.name,18,COLORS[Ledger.index(r.type)],true));space(c,6);c.addView(label(r.type+" · "+r.category+" · "+won(r.amount),14,INK));space(c,5);c.addView(label((r.enabled?"다음 예정 ":"일시 중지 · ")+r.next+" · "+cadenceName(r.cadence),13,r.due(LocalDate.now())?RED:MUTED));space(c,10);TextView confirm=button(r.due(LocalDate.now())?"확인하고 기록":"예정일에 확인",GREEN,true,()->confirmRepeat(r));confirm.setEnabled(r.due(LocalDate.now()));confirm.setAlpha(r.due(LocalDate.now())?1:.45f);confirm.setContentDescription("반복 "+r.id+" 확인");c.addView(confirm);space(c,8);c.addView(button("수정·일시 중지·건너뛰기",GREEN,false,()->repeatActions(r)));}}
        else{page.addView(text("자주 쓰는 기록",22,INK,true));space(page,7);page.addView(label("저장된 틀을 불러와 날짜·금액을 확인하고 기록해요. 원본 내역은 바뀌지 않아요.",13,MUTED));space(page,12);page.addView(button("자주 쓰는 기록 추가",GREEN,true,()->favoriteEditor(null)));space(page,15);if(plan.favorites.isEmpty())page.addView(label("내역을 수정하는 화면에서도 자주 쓰는 기록으로 저장할 수 있어요.",14,MUTED));for(PlanningData.Favorite f:plan.favorites){LinearLayout c=card(page);c.addView(text(f.name,18,COLORS[Ledger.index(f.type)],true));space(c,6);c.addView(label(f.type+" · "+f.category+" · "+won(f.amount),14,INK));space(c,12);c.addView(button("이 틀로 기록",GREEN,true,()->useFavorite(f)));space(c,8);c.addView(button("틀 수정·삭제",GREEN,false,()->favoriteEditor(f)));}}
    }
    private void limitCards(PlanningData plan,String type){
        List<PlanningData.Limit> limits=plan.effective(month,type);if(limits.isEmpty()){page.addView(label(type+"의 설정된 "+("지출".equals(type)?"예산":"목표")+"가 없어요.",14,MUTED));space(page,12);}
        for(PlanningData.Limit l:limits){PlanningData.Progress p=PlanningData.progress(l,month,all);LinearLayout c=card(page);String name=l.category.isEmpty()?type+" 전체 "+("지출".equals(type)?"예산":"목표"):l.category+("지출".equals(type)?" 예산":" "+type+" 목표");c.addView(text(name,18,COLORS[Ledger.index(type)],true));space(c,8);c.addView(label(won(p.actual)+" / "+won(p.target)+" · "+String.format(Locale.KOREA,"%.1f%%",p.percent),15,INK));space(c,8);c.addView(new TypeComparisonBarView(this,Math.min(1,p.actual/(double)p.target),COLORS[Ledger.index(type)],LINE),new LinearLayout.LayoutParams(-1,dp(10)));space(c,8);String status="지출".equals(type)?(p.remaining>=0?"남은 예산 "+won(p.remaining):"예산 초과 "+won(-p.remaining)):(p.remaining>0?"목표까지 "+won(p.remaining):"목표 달성"+(p.remaining<0?" · 추가 납입 "+won(-p.remaining):""));c.addView(label(status,14,p.remaining<0&&"지출".equals(type)?RED:GREEN));space(c,4);c.addView(label(l.month.equals("*")?"매월 기본 금액":l.month+"에만 적용",12,MUTED));c.setContentDescription(name+" 진행 "+p.actual+" / "+p.target+"원");space(c,10);c.addView(button("금액·적용 범위 수정",GREEN,false,()->limitEditor(l,type)));}
    }
    private void limitEditor(PlanningData.Limit old,String type){
        LinearLayout form=planForm();EditText amount=field(form,"금액 (원)",old==null?"":String.valueOf(old.amount),android.text.InputType.TYPE_CLASS_NUMBER);EditText category=field(form,"상세 분류 (비우면 전체)",old==null?"":old.category,android.text.InputType.TYPE_CLASS_TEXT);category.setSingleLine(true);category.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});CheckBox monthly=new CheckBox(this);monthly.setText("매월 같은 금액으로 적용");monthly.setChecked(old!=null&&old.month.equals("*"));monthly.setTextColor(INK);monthly.setTypeface(appFont);form.addView(monthly);form.addView(label("선택하지 않으면 "+month+"에만 적용해요. 이 달에 따로 설정한 금액이 매월 기본값보다 우선해요.",12,MUTED));
        planDialog(type+("지출".equals(type)?" 예산 설정":" 목표 설정"),form,()->{PlanningData.Limit limit=new PlanningData.Limit(monthly.isChecked()?"*":month.toString(),type,category.getText().toString(),Ledger.parseAmount(amount.getText().toString()));PlanningData p=db.planning();if(old!=null)p.limits.removeIf(l->l.key().equals(old.key()));p.put(limit);db.savePlanning(p);},old==null?null:()->{PlanningData p=db.planning();p.limits.removeIf(l->l.key().equals(old.key()));db.savePlanning(p);});
    }
    private String cadenceName(String cadence){return "WEEKLY".equals(cadence)?"매주":"YEARLY".equals(cadence)?"매년":"매월";}
    private void repeatEditor(PlanningData.Repeat old){
        LinearLayout form=planForm();EditText name=field(form,"반복 이름",old==null?"":old.name,android.text.InputType.TYPE_CLASS_TEXT);Spinner type=spinner(form,"반복 돈의 성격",Ledger.INPUT_TYPES,old==null?"지출":old.type);EditText amount=field(form,"반복 금액 (원)",old==null?"":String.valueOf(old.amount),android.text.InputType.TYPE_CLASS_NUMBER);EditText category=field(form,"반복 상세 분류",old==null?"":old.category,android.text.InputType.TYPE_CLASS_TEXT);EditText memo=field(form,"반복 메모",old==null?"":old.memo,android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);Spinner cadence=spinner(form,"반복 주기",new String[]{"매월","매주","매년"},old==null?"매월":cadenceName(old.cadence));final LocalDate[] date={old==null?LocalDate.now():old.next};TextView pick=button("다음 예정일 "+date[0],GREEN,false,()->{});pick.setContentDescription("반복 다음 예정일");pick.setOnClickListener(v->datePicker(date[0],"다음 예정일",(view,y,m,d)->{date[0]=LocalDate.of(y,m+1,d);pick.setText("다음 예정일 "+date[0]);}).show());form.addView(pick);CheckBox enabled=new CheckBox(this);enabled.setText("반복 사용");enabled.setTextColor(INK);enabled.setTypeface(appFont);enabled.setChecked(old==null||old.enabled);form.addView(enabled);
        planDialog("반복 기록 설정",form,()->{String c=new String[]{"MONTHLY","WEEKLY","YEARLY"}[cadence.getSelectedItemPosition()];boolean same=old!=null&&old.next.equals(date[0])&&old.cadence.equals(c);PlanningData.Repeat r=new PlanningData.Repeat(old==null?UUID.randomUUID().toString():old.id,name.getText().toString(),type.getSelectedItem().toString(),Ledger.parseAmount(amount.getText().toString()),category.getText().toString(),memo.getText().toString(),c,date[0],same?old.anchorDay:date[0].getDayOfMonth(),same?old.anchorMonth:date[0].getMonthValue(),enabled.isChecked());PlanningData p=db.planning();p.put(r);db.savePlanning(p);},old==null?null:()->{PlanningData p=db.planning();p.repeats.removeIf(r->r.id.equals(old.id));db.savePlanning(p);});
    }
    private void repeatActions(PlanningData.Repeat r){new AlertDialog.Builder(this).setTitle(r.name).setItems(new String[]{"예정 수정",r.enabled?"일시 중지":"다시 사용","이번 예정 건너뛰기","삭제"},(d,i)->{if(i==0)repeatEditor(r);else if(i==1){PlanningData p=db.planning();PlanningData.Repeat current=p.repeat(r.id);p.put(current.with(current.next,!current.enabled));db.savePlanning(p);render();}else new AlertDialog.Builder(this).setTitle(i==2?"이번 예정만 건너뛸까요?":"반복 설정을 삭제할까요?").setMessage("실제 거래 내역은 바꾸지 않아요.").setNegativeButton("취소",null).setPositiveButton("확인",(x,y)->{try{if(i==2)db.skipOccurrence(r.id,r.next.toString());else{PlanningData p=db.planning();p.repeats.removeIf(v->v.id.equals(r.id));db.savePlanning(p);}render();}catch(Exception ex){error("일정을 확인해 주세요",message(ex));}}).show();}).setNegativeButton("닫기",null).show();}
    private void confirmRepeat(PlanningData.Repeat repeat){if(!repeat.due(LocalDate.now()))return;NewEntryDraft draft=new NewEntryDraft(repeat.next,repeat.type);draft.amount=String.valueOf(repeat.amount);draft.category=repeat.category;draft.memo=repeat.memo;draft.repeatId=repeat.id;draft.occurrence=repeat.next.toString();draft.entryId=repeat.entryId();entryAmountStep(draft);}
    private void copyEntry(Ledger.Entry entry){NewEntryDraft draft=new NewEntryDraft(LocalDate.now(),entry.type);draft.amount=String.valueOf(entry.amount);draft.category=entry.category;draft.memo=entry.memo;entryAmountStep(draft);}
    private void useFavorite(PlanningData.Favorite favorite){NewEntryDraft draft=new NewEntryDraft(newRecordDate(),favorite.type);draft.amount=String.valueOf(favorite.amount);draft.category=favorite.category;draft.memo=favorite.memo;entryAmountStep(draft);}
    private void pickFavorite(){PlanningData p=db.planning();if(p.favorites.isEmpty()){new AlertDialog.Builder(this).setTitle("자주 쓰는 기록").setMessage("아직 등록된 틀이 없어요. 계획 화면이나 내역 수정 화면에서 추가하세요.").setPositiveButton("틀 추가",(d,w)->favoriteEditor(null)).setNegativeButton("취소",null).show();return;}String[] names=new String[p.favorites.size()];for(int i=0;i<names.length;i++){PlanningData.Favorite f=p.favorites.get(i);names[i]=f.name+" · "+f.type+" · "+won(f.amount);}new AlertDialog.Builder(this).setTitle("자주 쓰는 기록").setItems(names,(d,i)->useFavorite(p.favorites.get(i))).setNegativeButton("취소",null).show();}
    private void favoriteName(Ledger.Entry entry){LinearLayout form=planForm();EditText name=field(form,"틀 이름",entry.category,android.text.InputType.TYPE_CLASS_TEXT);planDialog("자주 쓰는 기록에 저장",form,()->{PlanningData p=db.planning();p.put(new PlanningData.Favorite(UUID.randomUUID().toString(),name.getText().toString(),entry.type,entry.amount,entry.category,entry.memo));db.savePlanning(p);},null);}
    private void favoriteEditor(PlanningData.Favorite old){LinearLayout form=planForm();EditText name=field(form,"틀 이름",old==null?"":old.name,android.text.InputType.TYPE_CLASS_TEXT);Spinner type=spinner(form,"틀 돈의 성격",Ledger.INPUT_TYPES,old==null?"지출":old.type);EditText amount=field(form,"틀 금액 (원)",old==null?"":String.valueOf(old.amount),android.text.InputType.TYPE_CLASS_NUMBER);EditText category=field(form,"틀 상세 분류",old==null?"":old.category,android.text.InputType.TYPE_CLASS_TEXT);EditText memo=field(form,"틀 메모",old==null?"":old.memo,android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);planDialog("자주 쓰는 기록 설정",form,()->{PlanningData p=db.planning();p.put(new PlanningData.Favorite(old==null?UUID.randomUUID().toString():old.id,name.getText().toString(),type.getSelectedItem().toString(),Ledger.parseAmount(amount.getText().toString()),category.getText().toString(),memo.getText().toString()));db.savePlanning(p);},old==null?null:()->{PlanningData p=db.planning();p.favorites.removeIf(f->f.id.equals(old.id));db.savePlanning(p);});}
    private void trendPage(){
        monthBar();LinearLayout choices=row();for(int count:new int[]{6,12}){TextView b=button("최근 "+count+"개월",GREEN,trendMonths==count,()->{trendMonths=count;render();});b.setContentDescription("추이 "+count+"개월");choices.addView(b,new LinearLayout.LayoutParams(0,-2,1));}page.addView(choices);space(page,12);page.addView(label("선택한 달까지 비교해요. 순저축이 회수로 음수이면 막대에서 제외하고 아래 금액에 표시해요. 가로로 밀거나 월 막대를 누르세요.",13,MUTED));space(page,12);
        LinearLayout legend=row();for(int i=0;i<4;i++){TextView t=label("● "+Ledger.TYPES[i],12,COLORS[i]);legend.addView(t,new LinearLayout.LayoutParams(0,dp(36),1));}page.addView(legend);
        List<PlanningData.MonthTotal> months=PlanningData.trend(all,month,trendMonths);if(trendSelected==null||trendSelected.isBefore(months.get(0).month)||trendSelected.isAfter(month))trendSelected=month;
        long scale=0;for(PlanningData.MonthTotal total:months)for(long value:total.totals)scale=Math.max(scale,value);page.addView(label("공통 눈금 최대 "+won(scale),12,MUTED));
        HorizontalScrollView scroll=new HorizontalScrollView(this);TrendChartView chart=new TrendChartView(this,months,COLORS,INK,MUTED,appFont,m->{trendSelected=m;render();});scroll.addView(chart,new HorizontalScrollView.LayoutParams(dp(76*months.size()),dp(235)));page.addView(scroll);scroll.post(()->scroll.fullScroll(View.FOCUS_RIGHT));space(page,8);
        HorizontalScrollView monthScroll=new HorizontalScrollView(this);LinearLayout monthsRow=row();for(PlanningData.MonthTotal m:months){TextView pick=button(m.month.toString(),GREEN,m.month.equals(trendSelected),()->{trendSelected=m.month;render();});pick.setPadding(dp(8),dp(8),dp(8),dp(8));pick.setContentDescription("추이 "+m.month+" 선택");pick.setSelected(m.month.equals(trendSelected));monthsRow.addView(pick,new LinearLayout.LayoutParams(dp(90),dp(48)));}monthScroll.addView(monthsRow);page.addView(monthScroll);monthScroll.post(()->monthScroll.fullScroll(View.FOCUS_RIGHT));space(page,16);
        final YearMonth compare=trendSelected;page.addView(text(compare+" 전월 비교",21,INK,true));space(page,8);page.addView(label(compare.minusMonths(1)+"과 비교 · 기록이 없는 달은 0원",13,MUTED));space(page,10);
        for(String type:Ledger.TYPES){PlanningData.Change c=PlanningData.change(all,compare,type,null);LinearLayout card=card(page);valueRow(card,type,c.current,COLORS[Ledger.index(type)],17,true,0);space(card,5);card.addView(label(changeText(c),13,MUTED));card.setContentDescription(compare+" "+type+" 전월 비교 "+c.current+"원 이전 "+c.previous+"원");}
        page.addView(text("지출 상세 분류 변화 · 상위 10개",19,INK,true));space(page,10);Map<String,PlanningData.Change> changes=PlanningData.categoryChanges(all,compare,"지출");List<String> order=new ArrayList<>(changes.keySet());order.sort((a,b)->{int result=Long.compare(Math.abs(changes.get(b).difference),Math.abs(changes.get(a).difference));return result!=0?result:a.compareTo(b);});if(order.isEmpty())page.addView(label("비교할 지출 기록이 없어요.",14,MUTED));for(String name:order.subList(0,Math.min(10,order.size()))){PlanningData.Change c=changes.get(name);LinearLayout card=card(page);card.addView(text(name,16,INK,true));space(card,6);card.addView(label(won(c.current)+" · "+changeText(c),13,MUTED));}
    }
    private String changeText(PlanningData.Change c){String delta=c.difference>0?"+"+won(c.difference):won(c.difference);return "지난달 "+won(c.previous)+" → "+delta+" · "+(c.percent==null?(c.current==0?"변동 없음":"지난달 0원이라 비율 계산 안 함"):String.format(Locale.KOREA,"%+.1f%%",c.percent));}
    private void backupPanel(){
        LinearLayout c=card(page);c.addView(text("백업 관리",20,INK,true));space(c,8);String last=backupStatus.last()==0?"아직 전체 백업 기록이 없어요.":"최근 전체 백업 "+Instant.ofEpochMilli(backupStatus.last()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))+"\n"+backupStatus.kind()+" · "+backupStatus.count()+"건";c.addView(label(last,14,INK));if(backupStatus.stale(System.currentTimeMillis())){space(c,8);c.addView(label("전체 백업한 지 7일 이상 지났어요. 기록을 따로 보관하세요.",14,RED));}space(c,12);c.addView(button("지금 전체 내역 백업",GREEN,true,()->chooseExport(EXPORT)));space(c,8);c.addView(button("계획 포함 백업 만들기 (.zip)",GREEN,false,()->chooseArchive(true)));space(c,8);c.addView(button("계획 포함 백업 가져오기",GREEN,false,()->chooseArchive(false)));space(c,10);c.addView(label("엑셀은 거래 내역만 저장해요. 계획 포함 백업은 내역과 예산·목표·반복·자주 쓰는 기록을 함께 저장해요. 비밀번호·화면 설정·상세 분류 숨김은 포함하지 않아요.",12,MUTED));space(c,10);TextView reminder=button(backupStatus.enabled()?"백업 알림 끄기":"백업 알림 켜기",GREEN,false,this::toggleBackupReminder);c.addView(reminder);space(c,6);c.addView(label("7일 이상 백업하지 않으면 앱 안에서 알려 드려요. 시스템 알림은 선택해서 켜며, 기기 상태에 따라 전달 시점이 달라질 수 있어요.",12,MUTED));
    }
    private void toggleBackupReminder(){if(backupStatus.enabled()){backupStatus.enabled(false);BackupReminderService.schedule(this);render();return;}if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},61);return;}backupStatus.enabled(true);BackupReminderService.schedule(this);render();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==61){backupStatus.enabled(grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED);BackupReminderService.schedule(this);render();}}
    private void chooseArchive(boolean export){if(busy)return;Intent intent=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(export?"application/zip":"*/*");if(export)intent.putExtra(Intent.EXTRA_TITLE,"차곡_계획포함백업_"+LocalDate.now()+".zip");try{startActivityForResult(intent,export?ARCHIVE_EXPORT:ARCHIVE_IMPORT);}catch(ActivityNotFoundException ex){error("파일 앱을 찾을 수 없어요","휴대폰 파일 선택 앱을 확인해 주세요.");}}
    private void loadArchive(Uri uri){setBusy("계획 포함 백업을 확인하고 있어요");io.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("파일을 열 수 없어요.");BackupArchive.Snapshot snapshot=BackupArchive.read(in);runOnUiThread(()->{clearBusy();previewArchive(snapshot);});}catch(Exception ex){runOnUiThread(()->{clearBusy();error("가져오지 못했어요",message(ex));});}});}
    private void previewArchive(BackupArchive.Snapshot snapshot){
        if(isFinishing()||isDestroyed())return;if(preferences.hasPin()&&!unlocked){pendingArchive=snapshot;return;}
        try{Ledger.ImportPlan plan=db.planImport(snapshot.entries,snapshot.openingAssets,snapshot.movements);if(!plan.errors.isEmpty()){error("파일을 확인해 주세요",joinErrors(plan.errors));return;}PlanningData old=db.planning(),merged=PlanningData.merge(old,snapshot.planning);int added=merged.limits.size()+merged.repeats.size()+merged.favorites.size()-old.limits.size()-old.repeats.size()-old.favorites.size();String text="기초자산 "+snapshot.openingAssets.size()+"건 포함\n저축 이동 "+snapshot.movements.size()+"건 포함\n새 내역 "+plan.additions.size()+"건 · 중복 제외 "+plan.duplicates+"건\n새 계획 설정 "+added+"개\n\n기존 내역과 설정은 지우지 않아요. 같은 예산·목표 또는 같은 ID의 반복·자주 쓰는 기록은 현재 설정을 유지해요. 비밀번호와 화면 설정은 바꾸지 않아요.";
            new AlertDialog.Builder(this).setTitle("계획 포함 백업 가져오기").setMessage(text).setNegativeButton("취소",null).setPositiveButton("확인하고 추가",(d,w)->{setBusy("내역과 계획을 저장하고 있어요");io.execute(()->{try{Ledger.ImportPlan committed=db.importPlanningAtomic(snapshot);runOnUiThread(()->{clearBusy();if(!committed.errors.isEmpty())error("가져오지 못했어요",joinErrors(committed.errors));else{render();toast("내역과 계획을 가져왔어요.");}});}catch(Exception ex){runOnUiThread(()->{clearBusy();error("저장하지 못했어요",message(ex));});}});}).show();
        }catch(Exception ex){error("백업을 확인해 주세요",message(ex));}
    }
    private static final class TypedValueCompat { static final int SP=2; }
    private final class IconView extends View {
        final String kind;final int color;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        IconView(String kind,int color){super(MainActivity.this);this.kind=kind;this.color=color;}
        private Path gear;
        private Path gearPath(){if(gear==null){float[] o=IconGeometry.gearOutline();gear=new Path();gear.moveTo(o[0],o[1]);for(int i=2;i<o.length;i+=2)gear.lineTo(o[i],o[i+1]);gear.close();}return gear;}
        @Override protected void onDraw(Canvas raw){float[] fit=IconGeometry.fit(getWidth(),getHeight(),getPaddingLeft(),getPaddingTop(),getPaddingRight(),getPaddingBottom());raw.save();raw.translate(fit[1],fit[2]);raw.scale(fit[0],fit[0]);p.setColor(color);p.setStrokeWidth(1.8f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(kind.equals("calendar")){raw.drawRoundRect(3,5,21,22,3,3,p);raw.drawLine(3,10,21,10,p);raw.drawLine(8,2,8,7,p);raw.drawLine(16,2,16,7,p);p.setStyle(Paint.Style.FILL);raw.drawCircle(8,15,1,p);raw.drawCircle(13,15,1,p);raw.drawCircle(8,19,1,p);}
            else if(kind.equals("list")){for(int y=5;y<=19;y+=7){raw.drawLine(8,y,21,y,p);raw.drawCircle(3,y,.7f,p);}}
            else if(kind.equals("pie")){p.setStyle(Paint.Style.FILL);raw.drawArc(new RectF(2,2,22,22),5,265,true,p);raw.drawArc(new RectF(2,2,22,22),277,78,true,p);p.setColor(preferences.dark()?BG:SURFACE);raw.drawCircle(12,12,4.5f,p);}
            else if(kind.equals("file")){raw.drawRoundRect(5,2,20,22,2,2,p);raw.drawLine(9,8,16,8,p);raw.drawLine(9,12,16,12,p);raw.drawLine(9,16,14,16,p);}
            else if(kind.equals("home")){Path roof=new Path();roof.moveTo(2,11);roof.lineTo(12,2);roof.lineTo(22,11);raw.drawPath(roof,p);raw.drawRect(5,10,19,22,p);raw.drawRect(10,15,14,22,p);}
            else if(kind.equals("summary")){p.setStyle(Paint.Style.FILL);raw.drawRect(3,12,7,22,p);raw.drawRect(10,2,14,22,p);raw.drawRect(17,8,21,22,p);}
            else if(kind.equals("settings")){raw.drawPath(gearPath(),p);raw.drawCircle(12,12,IconGeometry.GEAR_HOLE,p);}
            else if(kind.equals("menu")){raw.drawLine(4,6,20,6,p);raw.drawLine(4,12,20,12,p);raw.drawLine(4,18,20,18,p);}
            else if(kind.equals("back")){Path chevron=new Path();chevron.moveTo(15,4.5f);chevron.lineTo(7.5f,12);chevron.lineTo(15,19.5f);raw.drawPath(chevron,p);}
            else if(kind.equals("search")){raw.drawCircle(10,10,6,p);raw.drawLine(14.5f,14.5f,21,21,p);}
            else {p.setStyle(Paint.Style.FILL);raw.drawRoundRect(1,3,23,7,1.5f,1.5f,p);raw.drawRoundRect(1,10,19,14,1.5f,1.5f,p);raw.drawRoundRect(1,17,15,21,1.5f,1.5f,p);}
            raw.restore();}
    }

    private long movementAmount(EditText field){String value=field.getText().toString().trim().replace(",","");return value.isEmpty()||value.matches("0+")?0:Ledger.parseAmount(value);}
    private String movementText(SavingsMovement m){return m.date+" · "+m.source+" 해지\n기록한 원금 "+won(m.principal)+"\n실제 수령 "+won(m.received)+"\n"+(m.interest()>=0?"이자·수령차액 수입 ":"원금 대비 손실 ")+won(Math.abs(m.interest()))+"\n"+m.savingCategory+" 배분 "+won(m.saving)+"\n"+m.investmentCategory+" 투자 "+won(m.investment)+"\n"+(m.available()>=0?"사용 가능 금액으로 돌려놓음 ":"사용 가능 금액에서 추가로 채움 ")+won(Math.abs(m.available()))+"\n\n합계에는 이자·손실, 순저축 증감 "+won(m.saving-m.principal)+", 투자만 반영해요. 원금을 다시 수입으로 잡지 않아요.";}
    private void movementList(){LinearLayout list=planForm();list.addView(label("이미 저축으로 기록한 원금을 해지해 예금·투자와 사용 가능 금액으로 나눠요. 원래 저축 기록은 보존해요. 이전 원금은 기초자산으로 분리하고 새 이동은 실제 원금 회수·재가입을 각각 기록해요.",14,INK));space(list,12);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("저축 해지·배분").setView(list).setPositiveButton("새 이동",(d,w)->movementEditor(null)).setNegativeButton("닫기",null).create();for(SavingsMovement m:db.movements()){TextView row=button(m.date+" · "+m.source+" → "+m.savingCategory+" / "+m.investmentCategory+"\n"+won(m.received),GREEN,false,()->{dialog.dismiss();movementEditor(m);});list.addView(row);space(list,8);}ScrollView scroll=new ScrollView(this);scroll.addView(list);dialog.setView(scroll);dialog.show();}
    private void openingList(){
        LinearLayout list=planForm();list.addView(label("기록을 시작하기 전에 갖고 있던 돈을 등록하세요. 수입이나 올해 납입액에 더하지 않아요. 기초자산 기준일 이후 거래를 합쳐 보유 금액을 계산해요. 투자 금액은 평가액이 아닌 기록한 원금이에요.",13,MUTED));space(list,12);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("기초자산·현재 보유").setNegativeButton("닫기",null).setPositiveButton("기초자산 추가",(d,w)->openingEditor(null)).create();
        list.addView(text(LocalDate.now()+" 기준 보유",16,INK,true));for(Map.Entry<String,Long> value:OpeningAsset.balances(db.openingAssets(),db.all(),LocalDate.now().toString(),db.movements()).entrySet())valueRow(list,value.getKey(),value.getValue(),GREEN,14,false,0);
        space(list,16);list.addView(text("등록한 기초자산",16,INK,true));if(db.openingAssets().isEmpty())list.addView(label("등록된 기초자산이 없어요.",13,MUTED));
        for(OpeningAsset a:db.openingAssets()){space(list,8);TextView item=button(a.date+" · "+a.kind+" · "+a.category+"\n"+won(a.amount),GREEN,false,()->{dialog.dismiss();openingEditor(a);});item.setContentDescription("기초자산 "+a.id+" 수정");list.addView(item);}
        ScrollView scroll=new ScrollView(this);scroll.addView(list);dialog.setView(scroll);dialog.show();
    }
    private void openingEditor(OpeningAsset old){
        if(old!=null&&old.id.startsWith("move-base:")){SavingsMovement m=db.movement(old.id.substring(10));if(m!=null){movementEditor(m);return;}}
        LinearLayout form=planForm();form.addView(label("기록 시작 시 이미 갖고 있던 돈이에요. 기존 거래를 중복해 기초자산으로 넣지 마세요. 거래 내역과 수입·납입 통계는 바꾸지 않아요.",13,MUTED));space(form,12);
        EditText date=field(form,"기초자산 기준일",old==null?LocalDate.now().withDayOfYear(1).toString():old.date,android.text.InputType.TYPE_CLASS_TEXT);date.setFocusable(false);date.setOnClickListener(v->datePicker(LocalDate.parse(date.getText().toString()),"기초자산 기준일",(view,y,m,d)->date.setText(LocalDate.of(y,m+1,d).toString())).show());
        Spinner kind=spinner(form,"기초자산 종류",OpeningAsset.KINDS,old==null?"저축":old.kind);EditText category=field(form,"기초자산 상세 분류",old==null?"":old.category,android.text.InputType.TYPE_CLASS_TEXT);EditText amount=field(form,"기초자산 금액 (원)",old==null?"":String.valueOf(old.amount),android.text.InputType.TYPE_CLASS_NUMBER);EditText memo=field(form,"기초자산 메모",old==null?"":old.memo,android.text.InputType.TYPE_CLASS_TEXT);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("기초자산 입력").setView(scroll).setNegativeButton("취소",null).setPositiveButton("저장",null).setNeutralButton(old==null?"안내":"삭제",null).create();
        dialog.setOnShowListener(d->{dialog.getButton(-1).setOnClickListener(v->{try{db.saveOpening(new OpeningAsset(old==null?UUID.randomUUID().toString():old.id,date.getText().toString(),kind.getSelectedItem().toString(),Ledger.parseAmount(amount.getText().toString()),category.getText().toString(),memo.getText().toString()),old!=null);hideKeyboard();dialog.dismiss();render();openingList();}catch(Exception ex){error("기초자산을 확인해 주세요",message(ex));}});dialog.getButton(-3).setOnClickListener(v->{if(old==null)error("기초자산 안내","예: 올해 시작 전 예시적금에6,500,000원이 있었다면 저축/예시적금로 등록해요. 올해 납입한3,500,000원은 기존 거래에만 둡니다.");else new AlertDialog.Builder(this).setTitle("이 기초자산을 삭제할까요?").setMessage("거래 내역은 삭제하지 않아요. 보유 금액 계산의 시작 잔액만 바뀌어요.").setNegativeButton("취소",null).setPositiveButton("삭제",(x,y)->{try{db.deleteOpening(old.id);dialog.dismiss();render();openingList();}catch(Exception ex){error("기초자산을 삭제하지 못했어요",message(ex));}}).show();});});dialog.show();
    }
    private void movementEditor(SavingsMovement old){
        if(old!=null&&!old.detailed){legacyMovementEditor(old);return;}
        LinearLayout form=planForm();form.addView(label("해지 원금과 재가입은 실제 금액을 적으세요. 이전 원금은 기초자산으로 분리해 수입에 넣지 않아요. 이미 수동으로 같은 해지·재가입을 기록했다면 중복 저장하지 마세요.",13,MUTED));space(form,12);
        EditText date=field(form,"해지 날짜",old==null?LocalDate.now().toString():old.date,android.text.InputType.TYPE_CLASS_TEXT);date.setFocusable(false);date.setOnClickListener(v->datePicker(LocalDate.parse(date.getText().toString()),"해지 날짜",(view,y,m,d)->date.setText(LocalDate.of(y,m+1,d).toString())).show());
        EditText source=field(form,"해지한 저축 분류",old==null?"적금":old.source,android.text.InputType.TYPE_CLASS_TEXT);
        EditText principal=field(form,"실제 해지 원금 (원)",old==null?"":String.valueOf(old.principal),android.text.InputType.TYPE_CLASS_NUMBER);
        EditText previous=field(form,"이전부터 보유한 원금 (원)",old==null?"0":String.valueOf(old.previousPrincipal),android.text.InputType.TYPE_CLASS_NUMBER);
        CheckBox create=new CheckBox(this);create.setText("이전 원금을 기초자산에 함께 등록 (해지 연도 1월1일)");create.setContentDescription("기초자산 함께 등록");create.setTextColor(INK);create.setChecked(old==null||old.createOpening);form.addView(create);form.addView(label("이미 기초자산에 등록했다면 위 선택을 끄세요. 다른 기준일을 쓰면 기초자산 화면에서 먼저 등록하세요. 모든 기초자산 기준일과 기존 분류 이름을 맞춰 주세요.",12,MUTED));
        EditText received=field(form,"실제 수령액 (원)",old==null?"":String.valueOf(old.received),android.text.InputType.TYPE_CLASS_NUMBER);
        EditText government=field(form,"수령액에 포함된 정부기여금 (원)",old==null?"0":String.valueOf(old.government),android.text.InputType.TYPE_CLASS_NUMBER);
        EditText saving=field(form,"예금·저축에 넣을 금액 (원)",old==null?"0":String.valueOf(old.saving),android.text.InputType.TYPE_CLASS_NUMBER);
        EditText savingCat=field(form,"받는 저축 분류",old==null?"예금":old.savingCategory,android.text.InputType.TYPE_CLASS_TEXT);
        EditText investment=field(form,"투자에 넣을 금액 (원)",old==null?"0":String.valueOf(old.investment),android.text.InputType.TYPE_CLASS_NUMBER);
        EditText investmentCat=field(form,"받는 투자 분류",old==null?"주식":old.investmentCategory,android.text.InputType.TYPE_CLASS_TEXT);EditText memo=field(form,"이동 메모",old==null?"":old.memo,android.text.InputType.TYPE_CLASS_TEXT);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("저축 해지·배분 입력").setView(scroll).setNegativeButton("취소",null).setPositiveButton("미리보기",null).setNeutralButton(old==null?"안내":"이동 삭제",null).create();String id=old==null?UUID.randomUUID().toString():old.id;
        dialog.setOnShowListener(d->{dialog.getButton(-1).setOnClickListener(v->{try{long prior=movementAmount(previous);SavingsMovement m=new SavingsMovement(id,date.getText().toString(),source.getText().toString(),movementAmount(principal),movementAmount(received),movementAmount(saving),savingCat.getText().toString(),movementAmount(investment),investmentCat.getText().toString(),memo.getText().toString(),prior,movementAmount(government),create.isChecked()&&prior>0,true);
            String previewText="실제 원금 회수 "+won(m.principal)+"\n이전 원금 "+won(m.previousPrincipal)+(m.createOpening?" · 기초자산 함께 등록":" · 기존 기초자산 사용")+"\n이자·수령차액 "+won(m.interest())+"\n정부기여금 수입 "+won(m.government)+"\n"+m.savingCategory+" 재가입 "+won(m.saving)+"\n"+m.investmentCategory+" 투자 "+won(m.investment)+"\n이번 배분 후 남는 돈 "+won(m.available())+"\n\n원금을 수입에 넣지 않아요. 올해 순저축은 기존 납입과 원금 회수·재가입을 합산해 계산해요. 기초자산은 통계에 더하지 않아요.";
            AlertDialog preview=new AlertDialog.Builder(this).setTitle("이동 결과 확인").setMessage(previewText).setNegativeButton("다시 입력",null).setPositiveButton("확인하고 저장",null).create();preview.setOnShowListener(x->preview.getButton(-1).setOnClickListener(y->{try{db.saveMovement(m,false);selected=LocalDate.parse(m.date);month=YearMonth.from(selected);if(tab==2)saveStatisticsPeriod();preview.dismiss();dialog.dismiss();render();toast("실제 금액과 이전 원금을 나눠 저장했어요.");}catch(Exception ex){error("저장하지 못했어요",message(ex));}}));preview.show();
        }catch(Exception ex){error("입력값을 확인해 주세요",message(ex));}});dialog.getButton(-3).setOnClickListener(v->{if(old==null)error("기록 예시","원금10,000,000원 / 이전 원금6,500,000원 / 수령10,200,000원 / 정부기여금80,000원 / 예금8,000,000원 / 투자1,500,000원. 이자120,000원과 남는 돈700,000원을 자동 계산해요.");else new AlertDialog.Builder(this).setTitle("이동과 연결 기록을 삭제할까요?").setMessage("이동에서 만든 회수·재가입·이자·기여금·투자와 연결 기초자산을 함께 삭제해요. 원래 납입과 따로 등록한 기초자산은 유지해요.").setNegativeButton("취소",null).setPositiveButton("삭제",(x,y)->{try{db.saveMovement(old,true);dialog.dismiss();render();}catch(Exception ex){error("삭제하지 못했어요",message(ex));}}).show();});});dialog.show();
    }
    private void legacyMovementEditor(SavingsMovement old){
        LinearLayout form=planForm();form.addView(label("기존 저축 기록에 반영된 원금만 입력하세요. 실제 받은 금액과 예금·투자 배분액을 따로 적습니다. 남는 금액은 사용 가능 금액으로 돌아오고, 모자라는 금액은 사용 가능 금액에서 채워요.",13,MUTED));space(form,12);
        EditText date=field(form,"해지 날짜",old==null?LocalDate.now().toString():old.date,android.text.InputType.TYPE_CLASS_TEXT);date.setFocusable(false);date.setOnClickListener(v->datePicker(LocalDate.parse(date.getText().toString()),"해지 날짜",(view,y,m,d)->date.setText(LocalDate.of(y,m+1,d).toString())).show());
        EditText source=field(form,"해지한 저축 분류",old==null?"적금":old.source,android.text.InputType.TYPE_CLASS_TEXT);
        EditText principal=field(form,"이미 기록한 원금 (원)",old==null?"":String.valueOf(old.principal),android.text.InputType.TYPE_CLASS_NUMBER);
        EditText received=field(form,"실제 수령액 (원)",old==null?"":String.valueOf(old.received),android.text.InputType.TYPE_CLASS_NUMBER);
        EditText saving=field(form,"예금·저축에 넣을 금액 (원)",old==null?"0":String.valueOf(old.saving),android.text.InputType.TYPE_CLASS_NUMBER);
        EditText savingCat=field(form,"받는 저축 분류",old==null?"예금":old.savingCategory,android.text.InputType.TYPE_CLASS_TEXT);
        EditText investment=field(form,"투자에 넣을 금액 (원)",old==null?"0":String.valueOf(old.investment),android.text.InputType.TYPE_CLASS_NUMBER);
        EditText investmentCat=field(form,"받는 투자 분류",old==null?"주식":old.investmentCategory,android.text.InputType.TYPE_CLASS_TEXT);
        EditText memo=field(form,"이동 메모",old==null?"":old.memo,android.text.InputType.TYPE_CLASS_TEXT);
        CheckBox confirmed=new CheckBox(this);confirmed.setText("이 원금은 기존 저축 기록에 이미 반영되어 있어요");confirmed.setTextColor(INK);confirmed.setChecked(old!=null);form.addView(confirmed);ScrollView scroll=new ScrollView(this);scroll.addView(form);
        final String id=old==null?UUID.randomUUID().toString():old.id;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("저축 해지·배분 입력").setView(scroll).setPositiveButton("미리보기",null).setNegativeButton("취소",null).setNeutralButton(old==null?"예시":"이동 삭제",null).create();
        dialog.setOnShowListener(d->{dialog.getButton(-1).setOnClickListener(v->{try{if(!confirmed.isChecked())throw new IllegalArgumentException("원금이 이미 저축으로 기록됐는지 확인해 주세요.");SavingsMovement m=new SavingsMovement(id,date.getText().toString(),source.getText().toString(),movementAmount(principal),movementAmount(received),movementAmount(saving),savingCat.getText().toString(),movementAmount(investment),investmentCat.getText().toString(),memo.getText().toString());AlertDialog preview=new AlertDialog.Builder(this).setTitle("이동 결과 확인").setMessage(movementText(m)).setNegativeButton("다시 입력",null).setPositiveButton("확인하고 저장",null).create();preview.setOnShowListener(x->preview.getButton(-1).setOnClickListener(y->{try{db.saveMovement(m,false);preview.dismiss();dialog.dismiss();render();toast("원금 중복 없이 배분했어요.");}catch(Exception ex){error("저장하지 못했어요",message(ex));}}));preview.show();}catch(Exception ex){error("입력값을 확인해 주세요",message(ex));}});dialog.getButton(-3).setOnClickListener(v->{if(old==null)new AlertDialog.Builder(this).setTitle("배분 예시").setMessage("가상 예시: 원금 1,000,000원 / 수령 1,200,000원 / 예금 900,000원 / 투자 300,000원\n→ 이자 수입 200,000원, 순저축 −100,000원, 투자 300,000원, 사용 가능 변화 0원\n\n배분액이 수령액보다 많으면 부족분은 기존 사용 가능 금액에서 충당해요.").setPositiveButton("확인",null).show();else new AlertDialog.Builder(this).setTitle("이동과 연결 내역을 삭제할까요?").setMessage("이 이동으로 자동 생성한 이자·순저축·투자 기록만 함께 삭제해요. 과거 원금 기록은 유지해요.").setNegativeButton("취소",null).setPositiveButton("삭제",(x,y)->{try{db.saveMovement(old,true);dialog.dismiss();render();}catch(Exception ex){error("삭제하지 못했어요",message(ex));}}).show();});});dialog.show();
    }

}
