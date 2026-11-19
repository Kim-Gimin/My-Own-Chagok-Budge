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
    private static final int BG=0xFFF7F8FA, INK=0xFF202A33, MUTED=0xFF78818A, GREEN=0xFF19765B, RED=0xFFD76862, PURPLE=0xFF8071BC, LINE=0xFFE8ECEF;
    private static final int[] COLORS={GREEN,RED,PURPLE};
    private static final int PINK=0xFFED1761;
    private static final String MIME="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int EXPORT=10, TEMPLATE=11, IMPORT=12, EXPORT_MONTH=13;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final NumberFormat number=NumberFormat.getIntegerInstance(Locale.KOREA);
    private LedgerDb db;
    private CategoryStore categories;
    private List<Ledger.Entry> all=new ArrayList<>(), monthly=new ArrayList<>();
    private YearMonth month=YearMonth.now();
    private LocalDate selected=LocalDate.now();
    private int tab=2, statisticsType=1, filter, displayLimit=100;
    private String search="";
    private LinearLayout root,page,recordList;
    private AlertDialog progress;
    private boolean busy;
    private YearMonth pendingExportMonth;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db=new LedgerDb(this);
        categories=new CategoryStore(this);
        if(state!=null) {
            month=YearMonth.parse(state.getString("month",month.toString()));
            selected=LocalDate.parse(state.getString("selected",selected.toString()));
            tab=state.containsKey("statisticsType")?state.getInt("tab",2):2; statisticsType=state.getInt("statisticsType",1); filter=state.getInt("filter"); search=state.getString("search","");
            String exportMonth=state.getString("exportMonth"); if(exportMonth!=null) pendingExportMonth=YearMonth.parse(exportMonth);
        }
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
    }
    @Override public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("month",month.toString()); out.putString("selected",selected.toString());
        out.putInt("tab",tab); out.putInt("statisticsType",statisticsType); out.putInt("filter",filter); out.putString("search",search);
        if(pendingExportMonth!=null) out.putString("exportMonth",pendingExportMonth.toString());
    }
    @Override public void onDestroy() {
        if(progress!=null) { progress.dismiss(); progress=null; }
        io.execute(()->db.close()); io.shutdown();
        super.onDestroy();
    }
    private int dp(float n) { return Math.round(getResources().getDisplayMetrics().density*n); }
    private GradientDrawable bg(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("tnum"); t.setIncludeFontPadding(false);
        if(bold) t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return t;
    }
    private TextView label(String value,int size,int color) { return text(value,size,color,false); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void space(LinearLayout p,int height) { View v=new View(this); p.addView(v,new LinearLayout.LayoutParams(1,dp(height))); }
    private void line(LinearLayout p) { View v=new View(this); v.setBackgroundColor(LINE); p.addView(v,new LinearLayout.LayoutParams(-1,dp(1))); }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c=column(); c.setPadding(dp(18),dp(18),dp(18),dp(18)); c.setBackground(bg(Color.WHITE,20));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(14); parent.addView(c,lp); return c;
    }
    private TextView button(String caption,int color,boolean solid,Runnable click) {
        TextView t=text(caption,14,solid?Color.WHITE:color,true); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(48));
        t.setPadding(dp(16),dp(13),dp(16),dp(13)); t.setBackground(bg(solid?color:0xFFF0F4F3,12));
        t.setOnClickListener(v->click.run()); t.setClickable(true); return t;
    }
    private void render() {
        if(isFinishing() || isDestroyed()) return;
        all=db.all(); monthly=Ledger.month(all,month);
        boolean stats=tab==2;int surface=stats?Color.WHITE:BG;
        root=column(); root.setBackgroundColor(surface);getWindow().setStatusBarColor(surface);
        root.setOnApplyWindowInsetsListener((v,insets)-> { v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets(); });
        setContentView(root); root.requestApplyInsets();
        LinearLayout header=row();
        if(stats){
            header.setPadding(dp(15),dp(4),dp(15),dp(4));header.addView(new View(this),new LinearLayout.LayoutParams(dp(41),1));
            TextView prev=text("‹",29,INK,false);prev.setGravity(Gravity.CENTER);prev.setContentDescription("이전 달");prev.setOnClickListener(v->moveMonth(-1));header.addView(prev,new LinearLayout.LayoutParams(dp(36),dp(36)));
            TextView monthTitle=text(month.format(DateTimeFormatter.ofPattern("yyyy년 MM월")),20,INK,true);monthTitle.setSingleLine(true);monthTitle.setAutoSizeTextTypeUniformWithConfiguration(15,20,1,TypedValueCompat.SP);monthTitle.setGravity(Gravity.CENTER);monthTitle.setOnClickListener(v->pickMonth());header.addView(monthTitle,new LinearLayout.LayoutParams(0,dp(36),1));
            TextView next=text("›",29,INK,false);next.setGravity(Gravity.CENTER);next.setContentDescription("다음 달");next.setOnClickListener(v->moveMonth(1));header.addView(next,new LinearLayout.LayoutParams(dp(36),dp(36)));
        }else{
            header.setPadding(dp(22),dp(18),dp(22),dp(12));
            IconView mark=new IconView("stack",GREEN); header.addView(mark,new LinearLayout.LayoutParams(dp(28),dp(28)));
            TextView title=text("차곡",25,INK,true); LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,1); tl.leftMargin=dp(9); header.addView(title,tl);
            TextView today=button("오늘",GREEN,false,()->{ month=YearMonth.now(); selected=LocalDate.now(); displayLimit=100; render(); }); today.setMinHeight(dp(38)); today.setPadding(dp(14),dp(10),dp(14),dp(10)); header.addView(today);
        }
        root.addView(header);
        FrameLayout body=new FrameLayout(this); root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); body.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        page=column(); page.setPadding(dp(stats?16:20),dp(stats?0:6),dp(stats?16:20),dp(32)); scroll.addView(page);
        if(tab==0 || tab==1) { monthBar(); summary(); }
        if(tab==0) calendar(); else if(tab==1) records(); else if(tab==2) statistics(); else files();
        TextView add=text("+",stats?24:26,stats?PINK:Color.WHITE,false); add.setGravity(Gravity.CENTER); add.setContentDescription("수입, 지출, 저축 기록 추가"); add.setBackground(bg(stats?0xFFFDECF2:GREEN,14));
        LinearLayout.LayoutParams addParams=new LinearLayout.LayoutParams(dp(stats?32:42),dp(stats?32:42)); addParams.leftMargin=dp(9); if(tab!=3) header.addView(add,addParams); add.setOnClickListener(v->edit(null));
        line(root);
        LinearLayout nav=row(); nav.setPadding(dp(8),dp(8),dp(8),dp(8)); nav.setBackgroundColor(Color.WHITE);
        String[] names={"통계","달력","내역","엑셀"}, icons={"pie","calendar","list","file"};int[] targets={2,0,1,3};
        for(int i=0;i<4;i++) {
            final int target=targets[i];boolean active=tab==target;int tint=active?(target==2?PINK:GREEN):MUTED;LinearLayout item=column(); item.setGravity(Gravity.CENTER); item.setPadding(0,dp(3),0,dp(3));
            item.addView(new IconView(icons[i],tint),new LinearLayout.LayoutParams(dp(23),dp(23))); space(item,5);
            TextView navLabel=text(names[i],11,tint,active); navLabel.setGravity(Gravity.CENTER); item.addView(navLabel); item.setContentDescription(names[i]+" 탭");item.setSelected(active);
            item.setOnClickListener(v->{hideKeyboard(); tab=target; displayLimit=100; render();}); nav.addView(item,new LinearLayout.LayoutParams(0,dp(51),1));
        }
        root.addView(nav);
    }
    private void hideKeyboard() { View focus=getCurrentFocus(); if(focus!=null) ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0); }
    private void monthBar() {
        LinearLayout r=row(); r.setPadding(0,dp(2),0,dp(10));
        TextView prev=text("‹",32,MUTED,false); prev.setGravity(Gravity.CENTER); prev.setContentDescription("이전 달"); prev.setOnClickListener(v->moveMonth(-1)); r.addView(prev,new LinearLayout.LayoutParams(dp(42),dp(46)));
        TextView value=text(month.getYear()+"년 "+month.getMonthValue()+"월",23,INK,true); value.setGravity(Gravity.CENTER); value.setOnClickListener(v->pickMonth()); r.addView(value,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView next=text("›",32,MUTED,false); next.setGravity(Gravity.CENTER); next.setContentDescription("다음 달"); next.setOnClickListener(v->moveMonth(1)); r.addView(next,new LinearLayout.LayoutParams(dp(42),dp(46))); page.addView(r);
    }
    private void moveMonth(int delta) { if((month.getYear()==1900 && month.getMonthValue()==1 && delta<0) || (month.getYear()==9999 && month.getMonthValue()==12 && delta>0)) return; month=month.plusMonths(delta); selected=month.atDay(1); displayLimit=100; render(); }
    private void pickMonth() {
        DatePickerDialog picker=new DatePickerDialog(this,(p,y,m,d)->{ month=YearMonth.of(y,m+1); selected=month.atDay(d); displayLimit=100; render(); },month.getYear(),month.getMonthValue()-1,Math.min(selected.getDayOfMonth(),month.lengthOfMonth()));
        picker.setTitle("보고 싶은 날짜 선택"); picker.show();
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
        for(int i=0;i<3;i++) {
            LinearLayout item=column(); item.setPadding(0,0,dp(7),0); item.addView(label(Ledger.TYPES[i],11,COLORS[i])); space(item,3);
            TextView amount=text(number.format(total[i]),13,INK,true); amount.setSingleLine(true); amount.setAutoSizeTextTypeUniformWithConfiguration(9,13,1,TypedValueCompat.SP); item.addView(amount,new LinearLayout.LayoutParams(-1,dp(18))); r.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        c.addView(r); space(c,6); c.addView(label("수입 − 지출 − 저축  ·  단위: 원",10,MUTED));
    }
    private void calendar() {
        LinearLayout c=card(page); c.setPadding(dp(8),dp(15),dp(8),dp(10));
        LinearLayout weekdays=row(); String[] labels={"일","월","화","수","목","금","토"};
        for(int i=0;i<7;i++) { TextView t=text(labels[i],12,i==0?RED:i==6?0xFF6485B7:MUTED,false); t.setGravity(Gravity.CENTER); weekdays.addView(t,new LinearLayout.LayoutParams(0,dp(30),1)); } c.addView(weekdays);
        Map<String,long[]> daily=new HashMap<>();
        for(Ledger.Entry e:monthly) { long[] d=daily.get(e.date); if(d==null) {d=new long[3]; daily.put(e.date,d);} d[Ledger.index(e.type)]+=e.amount; }
        int offset=month.atDay(1).getDayOfWeek().getValue()%7, n=month.lengthOfMonth();
        int weeks=(offset+n+6)/7;
        for(int w=0;w<weeks;w++) {
            LinearLayout week=row(); week.setGravity(Gravity.TOP);
            for(int d=0;d<7;d++) {
                int day=w*7+d-offset+1; LinearLayout cell=column(); cell.setGravity(Gravity.CENTER_HORIZONTAL); cell.setPadding(dp(1),dp(3),dp(1),dp(3));
                week.addView(cell,new LinearLayout.LayoutParams(0,dp(66),1));
                if(day<1 || day>n) continue;
                LocalDate date=month.atDay(day); boolean active=date.equals(selected), today=date.equals(LocalDate.now());
                if(active) cell.setBackground(bg(0xFFEAF3EF,12));
                TextView dayText=text(String.valueOf(day),13,today?Color.WHITE:(d==0?RED:d==6?0xFF6485B7:INK),today || active); dayText.setGravity(Gravity.CENTER);
                if(today) dayText.setBackground(bg(GREEN,20));
                cell.addView(dayText,new LinearLayout.LayoutParams(dp(23),dp(23))); space(cell,2);
                long[] t=daily.get(date.toString());
                if(t!=null) for(int i=0;i<3;i++) if(t[i]>0) {
                    TextView amount=text((i==0?"+":i==1?"−":"·")+shortMoney(t[i]),9,COLORS[i],false); amount.setSingleLine(true); amount.setGravity(Gravity.CENTER); cell.addView(amount,new LinearLayout.LayoutParams(-1,dp(11)));
                }
                cell.setContentDescription(date+" 새 기록"); cell.setOnClickListener(v->{selected=date; displayLimit=100; render(); edit(null);});
            }
            c.addView(week);
        }
        LinearLayout legend=row(); legend.setGravity(Gravity.CENTER); legend.setPadding(0,dp(5),0,dp(3));
        for(int i=0;i<3;i++) { TextView t=label("● "+Ledger.TYPES[i]+"   ",11,COLORS[i]); legend.addView(t); } c.addView(legend);
        space(page,7);
        LinearLayout heading=row(); TextView date=text(selected.format(DateTimeFormatter.ofPattern("M월 d일 EEEE",Locale.KOREA)),17,INK,true); heading.addView(date,new LinearLayout.LayoutParams(0,-2,1));
        List<Ledger.Entry> entries=new ArrayList<>(); for(Ledger.Entry e:monthly) if(e.date.equals(selected.toString())) entries.add(e);
        heading.addView(label(entries.size()+"건",12,MUTED)); page.addView(heading); space(page,12);
        if(entries.isEmpty()) empty(page,"아직 기록이 없어요","날짜나 오른쪽 위 + 버튼을 눌러 기록해 보세요.");
        else showEntries(page,entries,false);
    }
    private void records() {
        LinearLayout filters=row(); filters.setPadding(0,0,0,dp(14)); String[] names={"전체","지출","수입","저축"};
        for(int i=0;i<4;i++) {
            final int f=i; TextView t=text(names[i],13,filter==i?Color.WHITE:MUTED,filter==i); t.setGravity(Gravity.CENTER); t.setPadding(dp(8),dp(11),dp(8),dp(11)); t.setBackground(bg(filter==i?GREEN:Color.WHITE,12)); t.setOnClickListener(v->{filter=f; displayLimit=100; render();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1); if(i>0) lp.leftMargin=dp(7); filters.addView(t,lp);
        } page.addView(filters);
        EditText query=new EditText(this); query.setSingleLine(true); query.setTextSize(14); query.setTextColor(INK); query.setHintTextColor(MUTED); query.setHint("분류 또는 메모 검색"); query.setPadding(dp(15),0,dp(15),0); query.setBackground(bg(Color.WHITE,12)); query.setText(search); page.addView(query,new LinearLayout.LayoutParams(-1,dp(48))); space(page,18);
        recordList=column(); page.addView(recordList); refreshRecords();
        query.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int st,int count,int after){} public void onTextChanged(CharSequence s,int st,int before,int count){search=s.toString();displayLimit=100;refreshRecords();} public void afterTextChanged(Editable e){} });
    }
    private void refreshRecords() {
        recordList.removeAllViews(); List<Ledger.Entry> entries=new ArrayList<>(); String[] types={"","지출","수입","저축"}; String q=search.trim().toLowerCase(Locale.KOREA);
        for(Ledger.Entry e:monthly) if((filter==0 || e.type.equals(types[filter])) && (q.isEmpty() || (e.category+" "+e.memo).toLowerCase(Locale.KOREA).contains(q))) entries.add(e);
        TextView count=label(entries.size()+"건의 내역",12,MUTED); recordList.addView(count); space(recordList,12);
        if(entries.isEmpty()) empty(recordList,"내역이 없어요",search.isEmpty()?"+ 버튼으로 첫 기록을 남겨 보세요.":"다른 검색어로 찾아보세요.");
        else showEntries(recordList,entries,true);
    }
    private void showEntries(LinearLayout parent,List<Ledger.Entry> entries,boolean groupDates) {
        String last="";
        for(int i=0;i<Math.min(displayLimit,entries.size());i++) {
            Ledger.Entry e=entries.get(i);
            if(groupDates && !last.equals(e.date)) { if(i>0) space(parent,16); parent.addView(text(LocalDate.parse(e.date).format(DateTimeFormatter.ofPattern("M월 d일 EEEE",Locale.KOREA)),13,MUTED,true)); space(parent,10); last=e.date; }
            LinearLayout r=row(); r.setPadding(dp(14),dp(15),dp(14),dp(15)); r.setBackground(bg(Color.WHITE,14));
            int index=Ledger.index(e.type); TextView icon=text(index==0?"↙":index==1?"↗":"=",22,COLORS[index],true); icon.setGravity(Gravity.CENTER); icon.setBackground(bg(index==0?0xFFEAF4EF:index==1?0xFFFFF0EE:0xFFF1EFF8,12)); r.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
            LinearLayout desc=column(); TextView cat=text(e.category,14,INK,true); cat.setSingleLine(true); cat.setEllipsize(TextUtils.TruncateAt.END); desc.addView(cat); space(desc,5);
            TextView memo=label(e.memo.isEmpty()?e.type:e.memo,11,MUTED); memo.setSingleLine(true); memo.setEllipsize(TextUtils.TruncateAt.END); desc.addView(memo);
            LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(0,-2,1); dl.leftMargin=dp(12); dl.rightMargin=dp(8); r.addView(desc,dl);
            TextView amount=text((index==0?"+":index==1?"−":"")+number.format(e.amount),14,COLORS[index],true); amount.setGravity(Gravity.END|Gravity.CENTER_VERTICAL); amount.setSingleLine(true); amount.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,TypedValueCompat.SP); r.addView(amount,new LinearLayout.LayoutParams(dp(128),dp(36)));
            r.setContentDescription(e.date+" "+e.type+" "+e.category+" "+won(e.amount)+". 눌러서 수정"); r.setOnClickListener(v->edit(e)); parent.addView(r); space(parent,7);
        }
        if(entries.size()>displayLimit) { TextView more=button("100건 더 보기",GREEN,false,()->{displayLimit+=100; if(tab==1) refreshRecords(); else render();}); parent.addView(more); }
    }
    private void empty(LinearLayout parent,String title,String subtitle) {
        LinearLayout c=card(parent); c.setGravity(Gravity.CENTER); c.setPadding(dp(12),dp(28),dp(12),dp(28)); c.addView(text(title,15,INK,true)); space(c,10); TextView sub=label(subtitle,12,MUTED); sub.setGravity(Gravity.CENTER); c.addView(sub);
    }
    private void statistics() {
        LinearLayout typeTabs=row();
        for(int type:new int[]{1,0,2}){
            boolean active=statisticsType==type;LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(0,dp(8),0,dp(4));
            TextView name=text(Ledger.TYPES[type],16,active?INK:MUTED,active);name.setGravity(Gravity.CENTER);item.addView(name);space(item,7);
            View underline=new View(this);underline.setBackgroundColor(active?PINK:Color.TRANSPARENT);item.addView(underline,new LinearLayout.LayoutParams(dp(44),dp(3)));
            item.setContentDescription(Ledger.TYPES[type]+" 통계");item.setSelected(active);
            item.setOnClickListener(v->{statisticsType=type;displayLimit=100;render();});typeTabs.addView(item,new LinearLayout.LayoutParams(0,dp(52),1));
        }
        page.addView(typeTabs);
        StatisticsData.Summary data=StatisticsData.aggregate(monthly,Ledger.TYPES[statisticsType]);
        TextView note=label(data.hasTinyCategory?"* 1% 미만인 분류가 있어요.":"",11,INK);note.setGravity(Gravity.CENTER_VERTICAL);page.addView(note,new LinearLayout.LayoutParams(-1,dp(22)));
        page.addView(new DonutChartView(this,data),new LinearLayout.LayoutParams(-1,-2));
        if(data.groupedCount>0){TextView grouped=label("차트의 ‘그 외 "+data.groupedCount+"개’는 아래 목록에서 각각 확인할 수 있어요.",11,MUTED);grouped.setLineSpacing(dp(3),1);page.addView(grouped);space(page,8);}
        page.addView(label("기간: "+month.getMonthValue()+"월 1일 ~ "+month.getMonthValue()+"월 "+month.lengthOfMonth()+"일",11,MUTED));space(page,14);line(page);
        statisticsAmountRow(page,month.getMonthValue()+"월 "+data.type+" 합계",data.total,-1,true);
        for(int i=0;i<Math.min(data.categories.size(),displayLimit);i++){
            StatisticsData.Category category=data.categories.get(i);
            statisticsAmountRow(page,category.name,category.amount,DonutChartView.color(i),false);
        }
        if(data.categories.size()>displayLimit)page.addView(button("분류 100개 더 보기",PINK,false,()->{displayLimit+=100;render();}));
        if(data.total==0){space(page,18);TextView empty=label("아직 "+data.type+" 기록이 없어요.",14,MUTED);empty.setGravity(Gravity.CENTER);page.addView(empty);space(page,14);page.addView(button(data.type+" 기록하기",PINK,true,()->edit(null)));}
        long[] monthlyTotals=Ledger.totals(monthly);space(page,24);line(page);
        statisticsAmountRow(page,"이번 달 사용 가능",Ledger.available(monthlyTotals),-1,false);
        page.addView(label("수입 − 지출 − 저축",11,MUTED));space(page,8);
        page.addView(label(monthlyTotals[0]>0?"수입 대비 저축률 "+String.format(Locale.KOREA,"%.1f%%",monthlyTotals[2]*100.0/monthlyTotals[0]):"수입을 기록하면 저축률을 볼 수 있어요.",11,MUTED));space(page,24);line(page);space(page,18);
        page.addView(text("전체 기간 합계",15,INK,true));
        long[] lifetime=Ledger.totals(all);for(int i=0;i<3;i++)statisticsAmountRow(page,Ledger.TYPES[i],lifetime[i],COLORS[i],false);
        statisticsAmountRow(page,"사용 가능",Ledger.available(lifetime),-1,false);
        TextView foot=label("저축은 지출 통계에 포함하지 않아요.",11,MUTED);page.addView(foot);
    }
    private void statisticsAmountRow(LinearLayout parent,String name,long amount,int color,boolean total){
        LinearLayout r=row();r.setPadding(0,dp(12),0,dp(12));
        if(color!=-1){View marker=new View(this);marker.setBackgroundColor(color);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(3),dp(19));mp.rightMargin=dp(8);r.addView(marker,mp);}
        TextView label=text(name,total?15:14,INK,total);label.setMaxLines(3);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=dp(9);r.addView(label,lp);
        TextView price=text(won(amount),total?16:15,INK,true);price.setSingleLine(true);price.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);price.setAutoSizeTextTypeUniformWithConfiguration(10,total?16:15,1,TypedValueCompat.SP);r.addView(price,new LinearLayout.LayoutParams(dp(145),dp(24)));
        r.setContentDescription(name+" "+won(amount));parent.addView(r);if(total)line(parent);
    }
    private void files() {
        space(page,12); page.addView(text("엑셀로 간편하게",25,INK,true)); space(page,10); TextView sub=label("기록은 안전하게 보관하고,\n여러 내역은 한 번에 입력하세요.",14,MUTED); sub.setLineSpacing(dp(5),1); page.addView(sub); space(page,24);
        LinearLayout backup=card(page); backup.addView(new IconView("file",GREEN),new LinearLayout.LayoutParams(dp(29),dp(29))); space(backup,13); backup.addView(text("엑셀 백업",18,INK,true)); space(backup,8); backup.addView(label("전체 "+all.size()+"건을 .xlsx 파일로 저장해요.",13,MUTED)); space(backup,18); backup.addView(button("전체 내역 백업",GREEN,true,()->chooseExport(EXPORT))); space(backup,10); backup.addView(button(month.getYear()+"년 "+month.getMonthValue()+"월만 내보내기",GREEN,false,()->chooseExport(EXPORT_MONTH)));
        LinearLayout input=card(page); input.addView(text("엑셀 한 번에 가져오기",18,INK,true)); space(input,8); TextView desc=label("입력 양식에 내역을 적어 가져오세요.\n저장 전에 오류와 중복을 확인할 수 있어요.",13,MUTED); desc.setLineSpacing(dp(5),1); input.addView(desc); space(input,18); input.addView(button("엑셀 파일 가져오기",GREEN,true,this::chooseImport)); space(input,10); input.addView(button("빈 입력 양식 내려받기",GREEN,false,()->chooseExport(TEMPLATE)));
        LinearLayout info=card(page); info.addView(text("알아두세요",14,INK,true)); space(info,12);
        TextView note=label("• .xlsx 파일을 지원해요. 한 번에 최대 10,000건을 가져와요.\n\n• 백업 파일의 고유ID가 같으면 중복을 건너뛰어요. ID가 없으면 날짜·구분·금액·분류·메모를 비교해요.\n\n• 오류나 ID 충돌이 있으면 전체를 추가하지 않아요. 기존 내역은 지우지 않아요.\n\n• 기록은 이 휴대폰에만 저장돼요. 앱을 삭제하면 기록도 삭제되니, 백업 파일을 따로 보관하세요.",12,MUTED); note.setLineSpacing(dp(4),1); info.addView(note);
        TextView version=label("차곡가계부 0.2.0  ·  로그인 없이, 광고 없이",11,MUTED); version.setGravity(Gravity.CENTER); page.addView(version);
    }
    private EditText field(LinearLayout parent,String title,String value,int inputType) {
        parent.addView(text(title,12,MUTED,true)); space(parent,7); EditText edit=new EditText(this); edit.setTextSize(15); edit.setTextColor(INK); edit.setPadding(dp(12),dp(10),dp(12),dp(10)); edit.setBackground(bg(0xFFF3F5F6,10)); edit.setInputType(inputType); edit.setText(value); parent.addView(edit,new LinearLayout.LayoutParams(-1,dp(47))); space(parent,15); return edit;
    }
    private void edit(Ledger.Entry entry) {
        if(busy) return;
        boolean updating=entry!=null;
        LinearLayout form=column(); form.setPadding(dp(22),dp(10),dp(22),dp(15));
        LinearLayout switches=row(); final String[] chosen={updating?entry.type:(tab==2?Ledger.TYPES[statisticsType]:"지출")}; final TextView[] tabs=new TextView[3];
        Runnable updateTabs=()->{for(int i=0;i<3;i++){boolean on=Ledger.TYPES[i].equals(chosen[0]);tabs[i].setTextColor(on?Color.WHITE:COLORS[i]);tabs[i].setBackground(bg(on?COLORS[i]:0xFFF2F4F5,10));}};
        for(int i=0;i<3;i++) { final int index=i; TextView t=text(Ledger.TYPES[i],14,COLORS[i],true); t.setGravity(Gravity.CENTER); tabs[i]=t; LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(43),1); if(i>0)p.leftMargin=dp(7); switches.addView(t,p); t.setOnClickListener(v->{chosen[0]=Ledger.TYPES[index];updateTabs.run();}); }
        updateTabs.run(); form.addView(switches); space(form,20);
        EditText amount=field(form,"금액 (원)",updating?String.valueOf(entry.amount):"",android.text.InputType.TYPE_CLASS_NUMBER); amount.setHint("0"); amount.setTextSize(24); amount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        LocalDate defaultDate=updating?LocalDate.parse(entry.date):(tab==0?selected:(YearMonth.from(LocalDate.now()).equals(month)?LocalDate.now():month.atDay(1)));
        EditText date=field(form,"날짜",defaultDate.toString(),android.text.InputType.TYPE_CLASS_TEXT); date.setFocusable(false); date.setContentDescription("날짜 선택");
        date.setOnClickListener(v->{LocalDate d=LocalDate.parse(date.getText().toString()); DatePickerDialog picker=new DatePickerDialog(this,(p,y,m,day)->date.setText(LocalDate.of(y,m+1,day).toString()),d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());picker.show();});
        EditText category=field(form,"분류",updating?entry.category:"",android.text.InputType.TYPE_CLASS_TEXT); category.setHint("예: 식비, 월급, 적금"); category.setSingleLine(true); category.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        LinearLayout categoryActions=row();
        TextView choose=button("분류 선택",GREEN,false,()->chooseCategory(chosen[0],category));
        TextView add=button("+ 분류 추가",GREEN,false,()->addCategory(chosen[0],category));
        categoryActions.addView(choose,new LinearLayout.LayoutParams(0,dp(43),1));
        LinearLayout.LayoutParams addCategoryParams=new LinearLayout.LayoutParams(0,dp(43),1); addCategoryParams.leftMargin=dp(8); categoryActions.addView(add,addCategoryParams);
        form.addView(categoryActions); space(form,15);
        EditText memo=field(form,"메모 (선택)",updating?entry.memo:"",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); memo.setHint("어떤 기록인가요?"); memo.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        TextView hint=label("저축은 사용 가능 금액에서 차감돼요.",11,MUTED); form.addView(hint);
        ScrollView scroll=new ScrollView(this); scroll.addView(form);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(updating?"기록 수정":"새 기록").setView(scroll).setNegativeButton("취소",null).setPositiveButton("저장",null);
        if(updating) builder.setNeutralButton("삭제",null);
        AlertDialog dialog=builder.create(); dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try {
                    Ledger.Entry next=new Ledger.Entry(updating?entry.id:UUID.randomUUID().toString(),date.getText().toString(),chosen[0],Ledger.parseAmount(amount.getText().toString()),category.getText().toString(),memo.getText().toString(),0);
                    db.save(next,updating); selected=LocalDate.parse(next.date); month=YearMonth.from(selected); hideKeyboard(); dialog.dismiss(); render(); toast("저장했어요.");
                } catch(Exception ex){amount.setError(ex.getMessage());}
            });
            if(updating) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new AlertDialog.Builder(this).setTitle("이 기록을 삭제할까요?").setMessage(entry.category+"  "+won(entry.amount)).setNegativeButton("취소",null).setPositiveButton("삭제",(a,b)->{db.delete(entry.id);dialog.dismiss();render();toast("삭제했어요.");}).show());
        });
        dialog.show(); dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void chooseCategory(String type,EditText category) {
        hideKeyboard();
        List<String> choices=categories.choices(type,db.all());
        String current=category.getText().toString().trim();
        if(!current.isEmpty() && !choices.contains(current)) choices.add(0,current);
        new AlertDialog.Builder(this).setTitle(type+" 분류 선택")
            .setItems(choices.toArray(new String[0]),(d,index)->category.setText(choices.get(index)))
            .setNegativeButton("취소",null)
            .setPositiveButton("새 분류 추가",(d,w)->addCategory(type,category)).show();
    }
    private void addCategory(String type,EditText category) {
        LinearLayout form=column(); form.setPadding(dp(22),dp(12),dp(22),dp(6));
        EditText name=field(form,"분류 이름","",android.text.InputType.TYPE_CLASS_TEXT);
        name.setSingleLine(true); name.setHint("예: 반려동물, 보험, 운동");
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        TextView note=label(type+" 분류에 저장하고 다음에도 선택할 수 있어요.",12,MUTED); form.addView(note);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(type+" 분류 추가").setView(form)
            .setNegativeButton("취소",null).setPositiveButton("추가",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                boolean added=categories.remember(type,name.getText().toString());
                category.setText(name.getText().toString().trim());
                ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(name.getWindowToken(),0);
                dialog.dismiss(); if(!added) toast("이미 있는 분류를 선택했어요.");
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
        Uri uri=data.getData();
        if(request==IMPORT) loadImport(uri);
        else if(request==EXPORT || request==TEMPLATE || request==EXPORT_MONTH) {
            List<Ledger.Entry> exportData=request==TEMPLATE?new ArrayList<>():request==EXPORT_MONTH?Ledger.month(db.all(),pendingExportMonth==null?month:pendingExportMonth):db.all();
            setBusy("엑셀 파일을 저장하고 있어요");
            io.execute(()->{
                try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")) {
                    if(output==null) throw new IOException("파일을 열 수 없어요.");
                    XlsxIO.write(output,exportData,request==TEMPLATE);
                    runOnUiThread(()->{clearBusy();toast(request==TEMPLATE?"입력 양식을 저장했어요.":exportData.size()+"건을 백업했어요.");});
                } catch(Exception ex) {runOnUiThread(()->{clearBusy();error("저장하지 못했어요",message(ex)+"\n만들어진 빈 파일이 있다면 삭제한 뒤 다시 시도해 주세요.");});}
            });
        }
    }
    private void loadImport(Uri uri) {
        setBusy("엑셀 내역을 확인하고 있어요");
        io.execute(()->{
            try(InputStream input=getContentResolver().openInputStream(uri)) {
                if(input==null) throw new IOException("파일을 열 수 없어요.");
                XlsxIO.ReadResult read=XlsxIO.read(input); Ledger.ImportPlan plan=Ledger.planImport(read.entries,db.all());
                runOnUiThread(()->{clearBusy();preview(read,plan);});
            } catch(Exception ex) {runOnUiThread(()->{clearBusy();error("가져오지 못했어요",message(ex));});}
        });
    }
    private void preview(XlsxIO.ReadResult read,Ledger.ImportPlan plan) {
        if(isFinishing() || isDestroyed()) return;
        if(read.invalidRows>0 || !plan.errors.isEmpty()) {
            List<String> errors=new ArrayList<>(read.errors); errors.addAll(plan.errors);
            error("파일을 수정해 주세요","오류 "+(read.invalidRows+plan.errors.size())+"건을 찾았어요. 아직 아무 내역도 추가하지 않았어요.\n\n"+joinErrors(errors)+"\n\n엑셀에서 수정한 뒤 다시 가져와 주세요."); return;
        }
        if(plan.additions.isEmpty()) {error("추가할 내역이 없어요",plan.duplicates>0?"이미 저장된 "+plan.duplicates+"건은 중복으로 건너뛰었어요.":"내역 시트의 두 번째 행부터 기록을 입력해 주세요.");return;}
        long[] t=Ledger.totals(plan.additions);
        StringBuilder summary=new StringBuilder("새 내역 "+plan.additions.size()+"건\n중복 제외 "+plan.duplicates+"건\n\n");
        for(int i=0;i<3;i++) summary.append(Ledger.TYPES[i]).append("  ").append(won(t[i])).append('\n');
        summary.append("\n미리보기\n");
        for(int i=0;i<Math.min(5,plan.additions.size());i++){Ledger.Entry e=plan.additions.get(i);summary.append(e.date).append("  ").append(e.type).append("  ").append(won(e.amount)).append("\n").append(e.category).append(e.memo.isEmpty()?"":" · "+e.memo).append("\n\n");}
        if(plan.additions.size()>5) summary.append("외 ").append(plan.additions.size()-5).append("건\n\n");
        summary.append("기존 내역에 추가하며, 기존 내역은 지우지 않아요.");
        new AlertDialog.Builder(this).setTitle("이 내역을 가져올까요?").setMessage(summary.toString()).setNegativeButton("취소",null).setPositiveButton(plan.additions.size()+"건 추가",(d,w)->{
            setBusy("내역을 저장하고 있어요");
            io.execute(()->{
                try {
                    Ledger.ImportPlan committed=db.importAtomic(read.entries);
                    runOnUiThread(()->{clearBusy();if(!committed.errors.isEmpty()){error("가져오지 못했어요",joinErrors(committed.errors));return;} if(!committed.additions.isEmpty()){month=YearMonth.from(LocalDate.parse(committed.additions.get(0).date));selected=month.atDay(1);}render();toast(committed.additions.size()+"건을 추가했어요.");});
                } catch(Exception ex){runOnUiThread(()->{clearBusy();error("저장하지 못했어요",message(ex));});}
            });
        }).show();
    }
    private String joinErrors(List<String> errors) { StringBuilder out=new StringBuilder();for(int i=0;i<Math.min(20,errors.size());i++)out.append(errors.get(i)).append('\n'); if(errors.size()>20)out.append("외 ").append(errors.size()-20).append("건");return out.toString(); }
    private String message(Exception e) { return e.getMessage()==null?"파일을 확인한 뒤 다시 시도해 주세요.":e.getMessage(); }
    private void setBusy(String title) { busy=true;LinearLayout l=row();l.setPadding(dp(24),dp(24),dp(24),dp(24));l.addView(new ProgressBar(this),new LinearLayout.LayoutParams(dp(28),dp(28)));TextView t=label(title,14,INK);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.leftMargin=dp(16);l.addView(t,p);progress=new AlertDialog.Builder(this).setView(l).setCancelable(false).create();progress.show(); }
    private void clearBusy() { busy=false;if(progress!=null){progress.dismiss();progress=null;} }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_SHORT).show(); }
    private void error(String title,String value) { if(!isFinishing() && !isDestroyed())new AlertDialog.Builder(this).setTitle(title).setMessage(value).setPositiveButton("확인",null).show(); }
    private static final class TypedValueCompat { static final int SP=2; }
    private final class IconView extends View {
        final String kind;final int color;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        IconView(String kind,int color){super(MainActivity.this);this.kind=kind;this.color=color;}
        @Override protected void onDraw(Canvas raw){raw.save();raw.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStrokeWidth(1.8f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(kind.equals("calendar")){raw.drawRoundRect(3,5,21,22,3,3,p);raw.drawLine(3,10,21,10,p);raw.drawLine(8,2,8,7,p);raw.drawLine(16,2,16,7,p);p.setStyle(Paint.Style.FILL);raw.drawCircle(8,15,1,p);raw.drawCircle(13,15,1,p);raw.drawCircle(8,19,1,p);}
            else if(kind.equals("list")){for(int y=5;y<=19;y+=7){raw.drawLine(8,y,21,y,p);raw.drawCircle(3,y,.7f,p);}}
            else if(kind.equals("pie")){p.setStyle(Paint.Style.FILL);raw.drawArc(new RectF(2,2,22,22),5,265,true,p);raw.drawArc(new RectF(2,2,22,22),277,78,true,p);p.setColor(Color.WHITE);raw.drawCircle(12,12,4.5f,p);}
            else if(kind.equals("file")){raw.drawRoundRect(5,2,20,22,2,2,p);raw.drawLine(9,8,16,8,p);raw.drawLine(9,12,16,12,p);raw.drawLine(9,16,14,16,p);}
            else {p.setStyle(Paint.Style.FILL);raw.drawRoundRect(1,3,23,7,1.5f,1.5f,p);raw.drawRoundRect(1,10,19,14,1.5f,1.5f,p);raw.drawRoundRect(1,17,15,21,1.5f,1.5f,p);}
            raw.restore();}
    }
}
