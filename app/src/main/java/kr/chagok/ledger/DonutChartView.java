package kr.chagok.ledger;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import java.text.NumberFormat;
import java.util.*;

/** Native Canvas ring chart with separate left/right label layout. */
final class DonutChartView extends View {
    private static final int INK=0xFF25272B, MUTED=0xFF8B8D92;
    private static final int[] PALETTE={0xFFED1761,0xFFF4423A,0xFFFF642C,0xFFFF9800,0xFFFFC400,0xFFE9DB39,0xFFB0CE43,0xFF76B846,0xFF44AA70,0xFF4EB5B3};
    private final StatisticsData.Summary data;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final float density,fontScale;
    static int color(int index){return PALETTE[Math.min(index,PALETTE.length-1)];}
    DonutChartView(Context context,StatisticsData.Summary data){
        super(context);this.data=data;density=getResources().getDisplayMetrics().density;
        fontScale=getResources().getDisplayMetrics().scaledDensity;
        setContentDescription(data.type+" 분류별 원형 차트, 합계 "+NumberFormat.getIntegerInstance(Locale.KOREA).format(data.total)+"원, "+data.categories.size()+"개 분류");
    }
    private float dp(float value){return value*density;}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);int height=resolveSize(Math.min((int)(width*.82f+dp(16)),(int)dp(390)),heightSpec);setMeasuredDimension(width,height);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float w=getWidth(),h=getHeight(),cx=w/2,cy=h/2;
        float radius=Math.min(w*.39f,h*.46f),inner=radius*.57f;
        paint.setStyle(Paint.Style.FILL);
        if(data.total==0){
            paint.setColor(0xFFF0F1F3);canvas.drawCircle(cx,cy,radius,paint);paint.setColor(Color.WHITE);canvas.drawCircle(cx,cy,inner,paint);
            textPaint.setColor(MUTED);textPaint.setTextSize(14*fontScale);textPaint.setTextAlign(Paint.Align.CENTER);textPaint.setTypeface(Typeface.DEFAULT);
            Paint.FontMetrics fm=textPaint.getFontMetrics();canvas.drawText("기록 없음",cx,cy-(fm.ascent+fm.descent)/2,textPaint);return;
        }
        RectF bounds=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);float start=0;
        List<Callout> left=new ArrayList<>(),right=new ArrayList<>();List<Float> boundaries=new ArrayList<>();
        for(int i=0;i<data.chartCategories.size();i++){
            StatisticsData.Category category=data.chartCategories.get(i);
            float sweep=(float)(category.amount*360.0/data.total);float middle=start+sweep/2;
            paint.setColor(color(i));canvas.drawArc(bounds,start,sweep,true,paint);boundaries.add(start);
            if(sweep>=18){
                double a=Math.toRadians(middle);float middleRadius=(radius+inner)/2;
                float available=Math.min(dp(66),(float)(2*middleRadius*Math.sin(Math.toRadians(Math.min(sweep,90)/2))*.84));
                textPaint.setTextSize(12*fontScale);textPaint.setTextAlign(Paint.Align.CENTER);textPaint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
                int c=color(i);double light=(.2126*Color.red(c)+.7152*Color.green(c)+.0722*Color.blue(c))/255;
                textPaint.setColor(light>.7?0xFF424623:Color.WHITE);
                String label=TextUtils.ellipsize(category.name,textPaint,available,TextUtils.TruncateAt.END).toString();
                Paint.FontMetrics fm=textPaint.getFontMetrics();canvas.drawText(label,cx+(float)Math.cos(a)*middleRadius,cy+(float)Math.sin(a)*middleRadius-(fm.ascent+fm.descent)/2,textPaint);
            }
            if(data.percent(category.amount)>=1){
                double angle=Math.toRadians(middle);Callout call=new Callout();
                call.right=Math.cos(angle)>=0;call.x=cx+(float)Math.cos(angle)*(radius-dp(3));call.y=cy+(float)Math.sin(angle)*(radius-dp(3));
                call.elbowX=cx+(float)Math.cos(angle)*(radius+dp(6));call.desiredY=cy+(float)Math.sin(angle)*(radius+dp(6));
                call.label=String.format(Locale.KOREA,"%.1f%%",data.percent(category.amount));
                (call.right?right:left).add(call);
            }
            start+=sweep;
        }
        paint.setColor(Color.WHITE);canvas.drawCircle(cx,cy,inner,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(5));paint.setColor(0x66FFFFFF);canvas.drawCircle(cx,cy,inner+dp(2.5f),paint);
        if(data.chartCategories.size()>1){paint.setStrokeWidth(dp(1.6f));paint.setColor(Color.WHITE);for(float angle:boundaries){double a=Math.toRadians(angle);canvas.drawLine(cx+(float)Math.cos(a)*inner,cy+(float)Math.sin(a)*inner,cx+(float)Math.cos(a)*radius,cy+(float)Math.sin(a)*radius,paint);}}
        drawCallouts(canvas,left,w,h);drawCallouts(canvas,right,w,h);
    }
    private void drawCallouts(Canvas canvas,List<Callout> labels,float width,float height){
        if(labels.isEmpty())return;labels.sort(Comparator.comparingDouble(c->c.desiredY));
        float edge=dp(12),gap=Math.max(dp(16),12*fontScale),previous=edge-gap;
        for(Callout call:labels){call.drawY=Math.max(Math.max(edge,call.desiredY),previous+gap);previous=call.drawY;}
        float next=height-edge+gap;for(int i=labels.size()-1;i>=0;i--){Callout call=labels.get(i);call.drawY=Math.min(call.drawY,next-gap);next=call.drawY;}
        textPaint.setTextSize(10*fontScale);textPaint.setTypeface(Typeface.DEFAULT);textPaint.setColor(INK);
        paint.setStyle(Paint.Style.STROKE);paint.setColor(INK);paint.setStrokeWidth(dp(.7f));
        Paint.FontMetrics fm=textPaint.getFontMetrics();
        for(Callout call:labels){
            float textWidth=textPaint.measureText(call.label),textX=call.right?width-dp(3):dp(3);
            float endX=call.right?textX-textWidth-dp(4):textX+textWidth+dp(4);
            Path path=new Path();path.moveTo(call.x,call.y);path.lineTo(call.elbowX,call.drawY);path.lineTo(endX,call.drawY);canvas.drawPath(path,paint);
            textPaint.setTextAlign(call.right?Paint.Align.RIGHT:Paint.Align.LEFT);canvas.drawText(call.label,textX,call.drawY-(fm.ascent+fm.descent)/2,textPaint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
    private static final class Callout {boolean right;float x,y,elbowX,desiredY,drawY;String label;}
}
