package kr.chagok.ledger;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.time.YearMonth;
import java.util.*;

/** Grouped monthly amounts use one scale for all four types, including zero months. */
final class TrendChartView extends View {
    private final List<PlanningData.MonthTotal> months;private final int[] colors;private final int ink,muted;private final Typeface font;private final java.util.function.Consumer<YearMonth> selected;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final float density;private float downX,downY;
    TrendChartView(Context context,List<PlanningData.MonthTotal> months,int[] colors,int ink,int muted,Typeface font,java.util.function.Consumer<YearMonth> selected){
        super(context);this.months=months;this.colors=colors.clone();this.ink=ink;this.muted=muted;this.font=font;this.selected=selected;density=getResources().getDisplayMetrics().density;setClickable(true);
        StringBuilder desc=new StringBuilder("월별 네 유형 추이");for(PlanningData.MonthTotal m:months){desc.append(", ").append(m.month);for(int i=0;i<4;i++)desc.append(" ").append(Ledger.TYPES[i]).append(" ").append(m.totals[i]).append("원");}setContentDescription(desc);
    }
    private float dp(float v){return v*density;}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        setMeasuredDimension(resolveSize(Math.round(dp(76*months.size())),widthSpec),resolveSize(Math.round(dp(235)),heightSpec));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);long max=0;for(PlanningData.MonthTotal m:months)for(long n:m.totals)max=Math.max(max,n);float base=getHeight()-dp(34),height=base-dp(34),slot=getWidth()/(float)months.size();
        paint.setTypeface(font);paint.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);paint.setTextAlign(Paint.Align.LEFT);paint.setColor(muted);canvas.drawText("최대 "+java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA).format(max)+"원",dp(5),dp(20),paint);
        paint.setStrokeWidth(dp(1));canvas.drawLine(0,base,getWidth(),base,paint);
        for(int m=0;m<months.size();m++){float center=slot*(m+.5f);for(int i=0;i<4;i++){float x=center+dp((i-1.5f)*12),h=max==0?0:(float)(Math.max(0,months.get(m).totals[i])/(double)max*height);paint.setColor(colors[i]);canvas.drawRoundRect(x-dp(4),base-h,x+dp(4),base,dp(2),dp(2),paint);}paint.setColor(ink);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(months.get(m).month.toString().substring(2),center,base+dp(22),paint);}
    }
    @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP){if(Math.abs(e.getX()-downX)<dp(12)&&Math.abs(e.getY()-downY)<dp(12)){performClick();int i=Math.max(0,Math.min(months.size()-1,(int)(e.getX()/getWidth()*months.size())));selected.accept(months.get(i).month);}return true;}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
