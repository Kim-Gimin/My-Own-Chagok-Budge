package kr.chagok.ledger;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/**
 * Drag and drop for the detailed category list. Rows stay in place while dragging;
 * only translations change. The caller saves the final slot through {@link Drop}.
 */
final class CategoryDrag {
    interface Drop{void moved(int from,int to);}
    /** Custom accessibility actions (TalkBack, tests). Outside framework action and resource id ranges. */
    static final int ACTION_MOVE_UP=0x7e0c0001, ACTION_MOVE_DOWN=0x7e0c0002;
    private static final long SETTLE_MS=150, SHIFT_MS=120;

    private final ScrollView scroll;
    private final LinearLayout list;
    private final int handleWidth,slop,edge,step;
    private final float lift;
    private final Drawable lifted;
    private final Drop drop;
    private View dragged;
    private View settlingRow;
    private int generation;
    private Drawable restingBackground;
    private float[] shifts=new float[0];
    private int from=-1,target=-1,downScroll;
    private float downRawY,lastRawY;
    private boolean settling;
    private final Runnable autoScroll=new Runnable(){public void run(){
        if(dragged==null)return;
        if(!dragged.isAttachedToWindow()){cancel();return;}
        int speed=edgeSpeed();
        if(speed!=0){int before=scroll.getScrollY();scroll.scrollBy(0,speed);if(scroll.getScrollY()!=before)update();}
        scroll.postOnAnimation(this);
    }};

    CategoryDrag(ScrollView scroll,LinearLayout list,int handleWidth,Drawable lifted,float lift,Drop drop){
        this.scroll=scroll;this.list=list;this.handleWidth=handleWidth;this.lifted=lifted;this.lift=lift;this.drop=drop;
        float density=scroll.getResources().getDisplayMetrics().density;
        slop=ViewConfiguration.get(scroll.getContext()).getScaledTouchSlop();edge=(int)(56*density);step=(int)(12*density);
    }

    boolean busy(){return dragged!=null||settling;}

    /** Handle area drags at once; the rest of the row drags after a long press so normal scrolling still works. */
    void attach(View row,int index,int count,String upLabel,String downLabel){
        final Runnable[] pending={null};final float[] down={0,0};
        row.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View view){}
            public void onViewDetachedFromWindow(View view){if(pending[0]!=null){view.removeCallbacks(pending[0]);pending[0]=null;}if(view==dragged||view==settlingRow)cancel();}
        });
        row.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    if(busy())return false;
                    down[0]=e.getRawY();down[1]=e.getRawX();
                    if(e.getX()<=handleWidth){start(v,index,e.getRawY());return true;}
                    final float rawY=e.getRawY();
                    pending[0]=()->{pending[0]=null;if(v.isAttachedToWindow()&&!busy())start(v,index,rawY);};
                    v.postDelayed(pending[0],ViewConfiguration.getLongPressTimeout());return true;
                case MotionEvent.ACTION_MOVE:
                    if(dragged==v){lastRawY=e.getRawY();update();return true;}
                    if(pending[0]!=null&&Math.hypot(e.getRawY()-down[0],e.getRawX()-down[1])>slop){v.removeCallbacks(pending[0]);pending[0]=null;}
                    return pending[0]!=null;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if(pending[0]!=null){v.removeCallbacks(pending[0]);pending[0]=null;}
                    if(dragged==v){if(e.getActionMasked()==MotionEvent.ACTION_UP){lastRawY=e.getRawY();update();}finish(e.getActionMasked()==MotionEvent.ACTION_UP);return true;}
                    return false;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if(pending[0]!=null){v.removeCallbacks(pending[0]);pending[0]=null;}
                    if(dragged==v){finish(false);return true;}return false;
                default:return dragged==v;
            }
        });
        row.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(host,info);
                if(index>0)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_MOVE_UP,upLabel));
                if(index<count-1)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_MOVE_DOWN,downLabel));
            }
            @Override public boolean performAccessibilityAction(View host,int action,Bundle args){
                if(action==ACTION_MOVE_UP&&index>0&&!busy()){drop.moved(index,index-1);return true;}
                if(action==ACTION_MOVE_DOWN&&index<count-1&&!busy()){drop.moved(index,index+1);return true;}
                return super.performAccessibilityAction(host,action,args);
            }
        });
    }

    private void start(View row,int index,float rawY){
        if(!row.isAttachedToWindow()||index<0||index>=list.getChildCount()||list.getChildAt(index)!=row)return;
        generation++;
        dragged=row;from=target=index;downRawY=lastRawY=rawY;downScroll=scroll.getScrollY();shifts=new float[list.getChildCount()];
        row.getParent().requestDisallowInterceptTouchEvent(true);
        restingBackground=row.getBackground();row.setBackground(lifted);row.setTranslationZ(lift);row.setAlpha(.96f);
        row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        scroll.postOnAnimation(autoScroll);
    }

    private int edgeSpeed(){
        int[] at=new int[2];scroll.getLocationOnScreen(at);float y=lastRawY-at[1];
        if(y<edge&&scroll.canScrollVertically(-1))return -Math.max(1,(int)(step*Math.min(1,(edge-y)/edge)));
        if(y>scroll.getHeight()-edge&&scroll.canScrollVertically(1))return Math.max(1,(int)(step*Math.min(1,(y-scroll.getHeight()+edge)/edge)));
        return 0;
    }

    private void update(){
        View row=dragged;if(row==null)return;
        float t=(lastRawY-downRawY)+(scroll.getScrollY()-downScroll);
        t=Math.max(-row.getTop(),Math.min(list.getHeight()-row.getBottom(),t));row.setTranslationY(t);
        float center=row.getTop()+t+row.getHeight()/2f;int next=from;
        for(int j=0;j<list.getChildCount();j++){if(j==from)continue;View other=list.getChildAt(j);float c=(other.getTop()+other.getBottom())/2f;if(j<from&&c>=center)next--;else if(j>from&&c<=center)next++;}
        // The clamped centre may equal an end row's centre (or never cross it for a taller row).
        if(row.getTop()+t<=.5f)next=0;
        else if(row.getBottom()+t>=list.getHeight()-.5f)next=list.getChildCount()-1;
        if(next==target)return;
        target=next;row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        int h=row.getHeight();
        for(int j=0;j<list.getChildCount();j++){
            if(j==from)continue;
            float shift=(from<j&&j<=target)?-h:(target<=j&&j<from)?h:0;
            if(shift!=shifts[j]){shifts[j]=shift;list.getChildAt(j).animate().translationY(shift).setDuration(SHIFT_MS).start();}
        }
    }

    private void finish(boolean commit){
        scroll.removeCallbacks(autoScroll);
        View row=dragged;dragged=null;settling=true;settlingRow=row;final int token=generation;
        final int start=from,end=commit?target:from;
        if(!commit)for(int j=0;j<list.getChildCount();j++)if(j!=start)list.getChildAt(j).animate().translationY(0).setDuration(SHIFT_MS).start();
        float slot=end>start?list.getChildAt(end).getBottom()-row.getBottom():end<start?list.getChildAt(end).getTop()-row.getTop():0;
        row.animate().translationY(slot).setDuration(SETTLE_MS).withEndAction(()->{
            if(token!=generation||!row.isAttachedToWindow())return;
            settling=false;settlingRow=null;scroll.requestDisallowInterceptTouchEvent(false);row.setTranslationZ(0);row.setAlpha(1f);row.setBackground(restingBackground);
            if(end!=start)drop.moved(start,end);
            else{row.setTranslationY(0);for(int j=0;j<list.getChildCount();j++)list.getChildAt(j).setTranslationY(0);}
        }).start();
    }

    /** Detaching/dismissing a dialog must never save an unfinished gesture or keep frame callbacks alive. */
    void cancel(){
        generation++;scroll.removeCallbacks(autoScroll);View active=dragged!=null?dragged:settlingRow;dragged=null;settlingRow=null;settling=false;
        if(active!=null){active.animate().cancel();active.setTranslationY(0);active.setTranslationZ(0);active.setAlpha(1f);active.setBackground(restingBackground);}
        for(int i=0;i<list.getChildCount();i++){View row=list.getChildAt(i);row.animate().cancel();row.setTranslationY(0);}
        scroll.requestDisallowInterceptTouchEvent(false);
    }

    /** Three short lines: the usual “drag me” handle, drawn so it never depends on font glyphs. */
    static final class Grip extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final float unit;
        Grip(int color,float unit){paint.setColor(color);paint.setStrokeWidth(unit*2);paint.setStrokeCap(Paint.Cap.ROUND);this.unit=unit;}
        @Override public void draw(Canvas canvas){Rect b=getBounds();float cx=b.exactCenterX(),cy=b.exactCenterY(),half=unit*9,gap=unit*5;for(int i=-1;i<=1;i++)canvas.drawLine(cx-half,cy+i*gap,cx+half,cy+i*gap,paint);}
        @Override public int getIntrinsicWidth(){return (int)(unit*22);}
        @Override public int getIntrinsicHeight(){return (int)(unit*16);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
