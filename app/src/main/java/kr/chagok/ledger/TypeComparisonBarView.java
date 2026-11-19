package kr.chagok.ledger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Four overview rows share one maximum; zero values have no colored segment. */
final class TypeComparisonBarView extends View {
    private final double fraction;
    private final int color,track;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    TypeComparisonBarView(Context context,double fraction,int color,int track) {
        super(context);this.fraction=Math.max(0,Math.min(1,fraction));this.color=color;this.track=track;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    double fraction(){return fraction;}
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int height=Math.round(10*getResources().getDisplayMetrics().density);
        setMeasuredDimension(MeasureSpec.getSize(widthSpec),resolveSize(height,heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);float width=getWidth(),height=getHeight(),radius=height/2;
        paint.setColor(track);canvas.drawRoundRect(new RectF(0,0,width,height),radius,radius,paint);
        if(fraction>0){paint.setColor(color);canvas.drawRoundRect(new RectF(0,0,(float)(width*fraction),height),radius,radius,paint);}
    }
}
