package app.galeria;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Viewer-only monoline symbols: compact drawing, normal dp sizing and separate large touch targets. */
public final class ViewerIcons extends Drawable {
    private final String name;private final int color;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public ViewerIcons(String n,int c){name=n;color=c;}
    private void line(Canvas c,float... a){Path q=new Path();q.moveTo(a[0],a[1]);for(int i=2;i<a.length;i+=2)q.lineTo(a[i],a[i+1]);c.drawPath(q,p);}
    @Override public void draw(Canvas c){
        Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);
        p.setColor(color);p.setStrokeWidth(1.75f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
        switch(name){
            case "back":line(c,10,5,3,12,10,19);line(c,3,12,22,12);break;
            case "edit":{
                Path q=new Path();q.moveTo(12.5f,4);q.lineTo(5,4);q.quadTo(3.5f,4,3.5f,5.5f);q.lineTo(3.5f,18.5f);q.quadTo(3.5f,20,5,20);q.lineTo(18.5f,20);q.quadTo(20,20,20,18.5f);q.lineTo(20,11);c.drawPath(q,p);
                line(c,10,13,11,10,20.5f,2.5f);line(c,10,13,13,12,21,4);break;
            }
            case "heart":case "heartFill":{
                Path q=new Path();q.moveTo(12,20);q.lineTo(4.5f,12.5f);q.cubicTo(1,9,3.7f,3,7.7f,3.8f);q.quadTo(10,3.9f,12,6.4f);q.quadTo(14,3.9f,16.3f,3.8f);q.cubicTo(20.3f,3,23,9,19.5f,12.5f);q.close();
                if(name.equals("heartFill"))p.setStyle(Paint.Style.FILL);c.drawPath(q,p);break;
            }
            case "trash":{
                line(c,2.5f,6,21.5f,6);c.drawRoundRect(8,2.5f,16,6,1.2f,1.2f,p);
                Path q=new Path();q.moveTo(5,6);q.lineTo(5,19);q.quadTo(5,20.5f,6.5f,20.5f);q.lineTo(17.5f,20.5f);q.quadTo(19,20.5f,19,19);q.lineTo(19,6);c.drawPath(q,p);break;
            }
            case "more":c.drawCircle(12,12,9.25f,p);p.setStyle(Paint.Style.FILL);c.drawCircle(8.25f,12,.85f,p);c.drawCircle(12,12,.85f,p);c.drawCircle(15.75f,12,.85f,p);break;
            case "info":c.drawCircle(12,12,9.25f,p);line(c,11,10.5f,12,10.5f,12,16.5f);line(c,10.5f,16.5f,13.5f,16.5f);p.setStyle(Paint.Style.FILL);c.drawCircle(12,7.2f,.9f,p);break;
            case "rotate":c.drawArc(3,3,21,21,-70,145,false,p);c.drawArc(3,3,21,21,110,145,false,p);line(c,21,9,21,14,17,12);line(c,3,15,3,10,7,12);c.drawRect(9,9,15,15,p);break;
            default:{c.restore();Icons fallback=new Icons(name,color);fallback.setBounds(b);fallback.draw(c);return;}
        }c.restore();
    }
    @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return 24;}@Override public int getIntrinsicHeight(){return 24;}
}
