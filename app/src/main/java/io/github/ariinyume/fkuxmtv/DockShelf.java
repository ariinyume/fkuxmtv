package io.github.ariinyume.fkuxmtv;

import android.content.Context;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** A stationary rounded material shelf containing a remotely scrollable icon track. */
final class DockShelf extends FrameLayout {
    final LinearLayout track;
    private final FrameLayout viewport;
    private View selected;
    private int offset;

    DockShelf(Context context,int count){
        super(context);
        setPadding(dp(12),dp(12),dp(12),dp(12));
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View v,Outline outline){
                outline.setRoundRect(0,0,v.getWidth(),v.getHeight(),dp(22));
            }
        });
        viewport=new FrameLayout(context);
        viewport.setClipChildren(true);viewport.setClipToPadding(true);
        addView(viewport,new LayoutParams(-1,-1));
        track=new LinearLayout(context);track.setOrientation(LinearLayout.HORIZONTAL);
        int width=count*dp(132)+Math.max(0,count-1)*dp(10);
        viewport.addView(track,new LayoutParams(width,-1));
    }
    void reveal(View tile,boolean animate){selected=tile;position(animate);}
    private void position(boolean animate){
        if(selected==null||viewport.getWidth()==0||selected.getWidth()==0)return;
        int available=viewport.getWidth(),content=track.getWidth();
        offset=DockScroll.reveal(offset,selected.getLeft(),selected.getRight(),available,content);
        float x=content<=available?(available-content)/2f:-offset;
        track.animate().cancel();
        if(animate)track.animate().translationX(x).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
        else track.setTranslationX(x);
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        super.onLayout(changed,left,top,right,bottom);position(false);
    }
    @Override protected void onDetachedFromWindow(){track.animate().cancel();super.onDetachedFromWindow();}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
