package kr.chagok.ledger;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.Gravity;
import android.widget.*;
import java.time.YearMonth;
import java.util.*;

/** Draft selection only. The owning budget editor saves the amount and filter together. */
final class LivingBudgetSelector extends LinearLayout {
    private final Set<String> selected;
    private final List<CheckBox> checks=new ArrayList<>();
    private final TextView count,preview;
    private final EditText amount;
    private final YearMonth month;
    private final List<Ledger.Entry> entries;
    private final int ink,muted,accent;
    private final Typeface font;
    private boolean updating;private final boolean showBalance;

    LivingBudgetSelector(Context context,List<String> options,Set<String> chosen,EditText amount,YearMonth month,List<Ledger.Entry> entries,int ink,int muted,int accent,int surface,Typeface font){
        this(context,options,chosen,amount,month,entries,ink,muted,accent,surface,font,true);
    }
    LivingBudgetSelector(Context context,List<String> options,Set<String> chosen,EditText amount,YearMonth month,List<Ledger.Entry> entries,int ink,int muted,int accent,int surface,Typeface font,boolean showBalance){
        super(context);setOrientation(VERTICAL);this.showBalance=showBalance;this.selected=new LinkedHashSet<>(chosen);this.amount=amount;this.month=month;this.entries=new ArrayList<>(entries);this.ink=ink;this.muted=muted;this.accent=accent;this.font=font;
        setPadding(0,dp(16),0,0);
        addView(text("생활비(자유금액)에 반영할 상세분류",15,ink,true));
        TextView note=text("달력 생활비 보기와 전체 생활비 예산에 모든 월 공통으로 적용해요. 저장 후 새로 생긴 분류는 직접 체크해야 반영돼요. 개별 분류 예산과 전체 지출 통계는 바뀌지 않아요.",12,muted,false);note.setPadding(0,dp(7),0,dp(8));addView(note);
        count=text("",13,accent,true);count.setContentDescription("생활비 선택 분류 개수");addView(count);
        preview=text("",13,ink,false);preview.setContentDescription("생활비 계산 미리보기");preview.setPadding(0,dp(7),0,dp(9));addView(preview);
        LinearLayout actions=new LinearLayout(context);actions.setOrientation(HORIZONTAL);
        TextView all=action("전체 선택",()->select(options,true)),none=action("전체 해제",()->select(options,false));
        actions.addView(all,new LayoutParams(0,dp(44),1));actions.addView(none,new LayoutParams(0,dp(44),1));addView(actions);
        LinearLayout list=new LinearLayout(context);list.setOrientation(VERTICAL);
        for(String name:options){CheckBox check=new CheckBox(context);check.setText(name);check.setTextColor(ink);check.setTextSize(14);check.setTypeface(font);check.setMinHeight(dp(48));check.setPadding(0,dp(4),0,dp(4));check.setContentDescription("생활비 반영 "+name);check.setChecked(selected.contains(name));check.setOnCheckedChangeListener((button,on)->{if(updating)return;if(on)selected.add(name);else selected.remove(name);refresh();});checks.add(check);list.addView(check,new LayoutParams(-1,-2));}
        if(options.isEmpty())list.addView(text("상세 분류가 없어요. 새 기록에서 분류를 직접 추가하세요.",13,muted,false));
        ScrollView scroll=new ScrollView(context);scroll.setNestedScrollingEnabled(true);scroll.addView(list);GradientDrawable background=new GradientDrawable();background.setColor(surface);background.setCornerRadius(dp(10));scroll.setBackground(background);addView(scroll,new LayoutParams(-1,dp(210)));
        amount.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){refresh();}public void afterTextChanged(Editable value){}});refresh();
    }
    Set<String> selected(){return new LinkedHashSet<>(selected);}
    private void select(List<String> options,boolean all){updating=true;selected.clear();if(all)selected.addAll(options);for(CheckBox check:checks)check.setChecked(all);updating=false;refresh();}
    private void refresh(){
        PlanningData plan=new PlanningData();plan.selectLivingCategories(selected);long target=0;try{target=Ledger.parseAmount(amount.getText().toString());}catch(IllegalArgumentException ignored){}
        PlanningData.Limit limit=new PlanningData.Limit("*","지출","",target>0?target:1);
        long total=PlanningData.progress(limit,month,entries).actual;PlanningData.Progress used=plan.budgetProgress(limit,month,entries);
        count.setText("선택 "+selected.size()+" / "+checks.size()+"개 · 모든 월 공통");
        preview.setText("전체 지출  "+won(total)+"\n생활비 반영 지출  "+won(used.actual)+(showBalance?"\n생활비 잔액  "+(target>0?won(used.remaining):"예산 금액 입력 후 표시"):""));
    }
    private TextView action(String title,Runnable run){TextView view=text(title,13,accent,true);view.setGravity(Gravity.CENTER);view.setContentDescription("생활비 분류 "+title);view.setOnClickListener(v->run.run());return view;}
    private TextView text(String value,int size,int color,boolean bold){TextView view=new TextView(getContext());view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setTypeface(Typeface.create(font,bold?Typeface.BOLD:Typeface.NORMAL));view.setLineSpacing(dp(3),1);return view;}
    private int dp(float value){return Math.round(getResources().getDisplayMetrics().density*value);}
    private static String won(long value){return String.format(Locale.KOREA,"%,d원",value);}
}
