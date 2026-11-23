package kr.chagok.ledger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Native canvas chart for cumulative expense pace and the daily budget pace. */
final class SpendingSpeedView extends View {
    private final SpendingSpeedData.Result data;private final int currentColor,previousColor,ink,muted,line;private final Typeface font;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG),textPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density,fontScale;private final NumberFormat number=NumberFormat.getIntegerInstance(Locale.KOREA);
    SpendingSpeedView(Context context,String scope,SpendingSpeedData.Result data,int currentColor,int previousColor,int ink,int muted,int line,Typeface font){
        super(context);this.data=data;this.currentColor=currentColor;this.previousColor=previousColor;this.ink=ink;this.muted=muted;this.line=line;this.font=font;
        density=getResources().getDisplayMetrics().density;fontScale=getResources().getDisplayMetrics().scaledDensity;
        setContentDescription(scope+" 그래프, 현재 "+won(data.currentTotal)+", 전월 같은 기간 "+won(data.previousComparableTotal)+(data.budgetTarget>0?", 예산 "+won(data.budgetTarget):""));
    }
    private float dp(float value){return value*density;}private String won(long value){return number.format(value)+"원";}
    private String axis(long value){return value>=10000?number.format(value/10000)+"만":number.format(value);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){setMeasuredDimension(MeasureSpec.getSize(widthSpec),resolveSize((int)dp(250),heightSpec));}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float width=getWidth(),height=getHeight(),left=dp(45),right=width-dp(8),top=dp(12)+legendRowHeight()*(data.budgetTarget>0?3:2),bottom=height-dp(32);if(right<=left||bottom<=top)return;
        drawLegend(canvas,left,dp(15));long max=0;for(long value:data.current)max=Math.max(max,value);for(long value:data.previous)max=Math.max(max,value);
        if(data.budgetTarget>0)max=Math.max(max,data.budgetTarget*data.daysShown/data.month.lengthOfMonth());max=niceMax(max);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(line);textPaint.setTypeface(font);textPaint.setTextSize(10*fontScale);textPaint.setColor(muted);textPaint.setTextAlign(Paint.Align.RIGHT);Paint.FontMetrics metrics=textPaint.getFontMetrics();
        for(int i=0;i<=4;i++){float y=bottom-(bottom-top)*i/4f;canvas.drawLine(left,y,right,y,paint);canvas.drawText(axis(max*i/4),left-dp(7),y-(metrics.ascent+metrics.descent)/2,textPaint);}
        if(data.currentMonth&&data.daysShown>1){float todayX=xFor(data.daysShown-1,left,right);paint.setColor(muted);paint.setStrokeWidth(dp(1.2f));canvas.drawLine(todayX,top,todayX,bottom,paint);textPaint.setTextAlign(Paint.Align.CENTER);textPaint.setColor(ink);canvas.drawText("오늘",todayX,height-dp(8),textPaint);}
        else{textPaint.setTextAlign(Paint.Align.LEFT);canvas.drawText("1일",left,height-dp(8),textPaint);textPaint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(data.daysShown+"일",right,height-dp(8),textPaint);}
        if(data.budgetTarget>0)drawBudget(canvas,left,right,top,bottom,max);
        drawSeries(canvas,data.previous,previousColor,left,right,top,bottom,max,dp(2.5f),false);drawSeries(canvas,data.current,currentColor,left,right,top,bottom,max,dp(3),true);
    }
    private void drawLegend(Canvas canvas,float left,float y){
        textPaint.setTypeface(font);textPaint.setTextSize(13*fontScale);textPaint.setColor(ink);textPaint.setTextAlign(Paint.Align.LEFT);
        drawLegendItem(canvas,left,y,currentColor,(data.currentMonth?"이번 달":data.month.getMonthValue()+"월")+" 지출");
        drawLegendItem(canvas,left,y+legendRowHeight(),previousColor,(data.currentMonth?"지난달":data.previousMonth.getMonthValue()+"월")+" 지출");
        if(data.budgetTarget>0){float budgetY=y+2*legendRowHeight();paint.setColor(ink);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setPathEffect(new DashPathEffect(new float[]{dp(4),dp(3)},0));canvas.drawLine(left,budgetY,left+dp(20),budgetY,paint);paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);canvas.drawText("예산 속도",left+dp(28),budgetY-(textPaint.ascent()+textPaint.descent())/2,textPaint);}
    }
    private float legendRowHeight(){return Math.max(dp(22),18*fontScale);}
    private void drawLegendItem(Canvas canvas,float left,float y,int color,String title){paint.setStyle(Paint.Style.FILL);paint.setColor(color);canvas.drawRect(left,y-dp(4),left+dp(20),y+dp(4),paint);canvas.drawText(title,left+dp(28),y-(textPaint.ascent()+textPaint.descent())/2,textPaint);}
    private void drawBudget(Canvas canvas,float left,float right,float top,float bottom,long max){Path path=new Path();for(int i=0;i<data.daysShown;i++){float x=xFor(i,left,right),value=(float)(data.budgetTarget*(i+1)/(double)data.month.lengthOfMonth()),y=bottom-(bottom-top)*value/max;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.8f));paint.setColor(ink);paint.setPathEffect(new DashPathEffect(new float[]{dp(4),dp(3)},0));canvas.drawPath(path,paint);paint.setPathEffect(null);}
    private void drawSeries(Canvas canvas,List<Long> values,int color,float left,float right,float top,float bottom,long max,float stroke,boolean point){if(values.isEmpty())return;Path path=new Path();for(int i=0;i<values.size();i++){float x=xFor(i,left,right),y=bottom-(bottom-top)*values.get(i)/(float)max;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(color);canvas.drawPath(path,paint);if(point){paint.setStyle(Paint.Style.FILL);int last=values.size()-1;canvas.drawCircle(xFor(last,left,right),bottom-(bottom-top)*values.get(last)/(float)max,dp(4.5f),paint);}}
    private float xFor(int index,float left,float right){return data.daysShown<=1?(left+right)/2:left+(right-left)*index/(data.daysShown-1f);}
    private long niceMax(long value){if(value<=0)return 1;long unit=1;while(value/unit>=10)unit*=10;long rounded=((value+unit-1)/unit)*unit;return rounded==value?rounded+unit:rounded;}
}
