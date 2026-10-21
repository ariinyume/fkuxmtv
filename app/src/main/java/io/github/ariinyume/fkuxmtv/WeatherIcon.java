package io.github.ariinyume.fkuxmtv;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small monochrome vector silhouettes; no emoji or downloaded images. */
final class WeatherIcon extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String type;
    WeatherIcon(int code,boolean day,int color){type=WeatherPolicy.icon(code,day);paint.setColor(color);}
    @Override public void draw(Canvas canvas){
        int saved=canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/32f,getBounds().height()/32f);
        if(type.equals("sun")||type.equals("partly")){
            float x=type.equals("sun")?16:11,y=type.equals("sun")?16:11;
            canvas.drawCircle(x,y,5,paint);
            for(int i=0;i<8;i++){double angle=i*Math.PI/4;canvas.drawCircle(x+(float)Math.cos(angle)*9,y+(float)Math.sin(angle)*9,1.3f,paint);}
        }
        if(type.equals("moon")){Path moon=new Path();moon.moveTo(23,23);moon.cubicTo(9,27,4,13,15,6);moon.cubicTo(11,17,18,22,23,23);canvas.drawPath(moon,paint);}
        if(!type.equals("sun")&&!type.equals("moon")){
            canvas.drawCircle(10,18,5,paint);canvas.drawCircle(17,15,7,paint);canvas.drawCircle(24,19,4,paint);canvas.drawRoundRect(9,17,25,23,3,3,paint);
            if(type.equals("rain"))for(int x=10;x<26;x+=7)canvas.drawRoundRect(x,25,x+2,30,1,1,paint);
            if(type.equals("snow"))for(int x=10;x<26;x+=7)canvas.drawCircle(x,27,1.7f,paint);
            if(type.equals("fog")){canvas.drawRoundRect(7,25,27,27,1,1,paint);canvas.drawRoundRect(10,29,23,31,1,1,paint);}
            if(type.equals("storm")){Path bolt=new Path();bolt.moveTo(17,23);bolt.lineTo(13,28);bolt.lineTo(17,28);bolt.lineTo(15,32);bolt.lineTo(23,25);bolt.lineTo(19,25);bolt.lineTo(21,23);bolt.close();canvas.drawPath(bolt,paint);}
        }
        canvas.restoreToCount(saved);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
