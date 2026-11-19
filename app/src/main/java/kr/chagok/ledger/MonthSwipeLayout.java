package kr.chagok.ledger;

import android.content.Context;
import android.view.*;
import android.widget.LinearLayout;

/** Lets vertical scrolling and day taps pass through; owns horizontal month gestures. */
final class MonthSwipeLayout extends LinearLayout {
    interface Listener {void move(int delta);}
    private final Listener listener;
    private final float threshold;
    private final int slop;
    private float downX,downY;
    private int axis; // 0 undecided, 1 horizontal, 2 vertical/multitouch
    private boolean settling;
    MonthSwipeLayout(Context context,Listener listener){
        super(context);this.listener=listener;setOrientation(VERTICAL);
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
        threshold=64*getResources().getDisplayMetrics().density;
    }
    private void classify(MotionEvent event){
        if(axis!=0)return;float dx=Math.abs(event.getRawX()-downX),dy=Math.abs(event.getRawY()-downY);
        if(dx>slop&&dx>dy*1.3f)axis=1;
        else if(dy>slop){axis=2;getParent().requestDisallowInterceptTouchEvent(false);}
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(settling)return true;
                downX=event.getRawX();downY=event.getRawY();axis=0;
                getParent().requestDisallowInterceptTouchEvent(true);return false;
            case MotionEvent.ACTION_MOVE:
                classify(event);return axis==1;
            case MotionEvent.ACTION_POINTER_DOWN:axis=2;getParent().requestDisallowInterceptTouchEvent(false);return false;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);return false;
        }return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(settling)return true;
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE)classify(event);
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&axis==1){setTranslationX(Math.max(-getWidth()*.25f,Math.min(getWidth()*.25f,(event.getRawX()-downX)*.28f)));return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_UP&&axis==1){
            float distance=event.getRawX()-downX;getParent().requestDisallowInterceptTouchEvent(false);
            boolean change=Math.abs(distance)>=threshold;settling=true;
            animate().translationX(change?Math.copySign(getWidth()*.16f,distance):0).alpha(change?.65f:1).setDuration(120).withEndAction(()->{
                setTranslationX(0);setAlpha(1);settling=false;if(change)listener.move(distance<0?1:-1);
            }).start();return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL||event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN){axis=2;setTranslationX(0);getParent().requestDisallowInterceptTouchEvent(false);return true;}
        return true;
    }
    @Override protected void onDetachedFromWindow(){animate().cancel();super.onDetachedFromWindow();}
}
