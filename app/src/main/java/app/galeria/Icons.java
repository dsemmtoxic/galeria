package app.galeria;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small, consistent 24-unit line icons; no emoji or bitmap UI symbols. */
public final class Icons extends Drawable {
    private final String name;private final int color;private final Paint p=new Paint(3);
    public Icons(String n,int c){name=n;color=c;}
    private void line(Canvas c,float... a){Path q=new Path();q.moveTo(a[0],a[1]);for(int i=2;i<a.length;i+=2)q.lineTo(a[i],a[i+1]);c.drawPath(q,p);}
    private void rect(Canvas c,float l,float t,float r,float b,float rad){c.drawRoundRect(l,t,r,b,rad,rad,p);}
    @Override public void draw(Canvas c){
        Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);
        p.setColor(color);p.setStrokeWidth(1.75f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
        switch(name){
            case "lens":line(c,8,3,5,3,3,5,3,8);line(c,16,3,19,3,21,5,21,8);line(c,3,16,3,19,5,21,8,21);c.drawCircle(12,12,4,p);p.setStyle(Paint.Style.FILL);c.drawCircle(19,19,2,p);break;
            case "gallery":case "photo":rect(c,3,3,21,21,4);c.drawCircle(16,8,1.5f,p);line(c,3,16,8,11,14,18,17,14,21,18);break;
            case "albums":rect(c,5,7,21,21,3);line(c,17,3,6,3,3,6,3,17);line(c,9,16,12,12,17,18);c.drawCircle(17,11,1,p);break;
            case "folder":{Path q=new Path();q.moveTo(3,8);q.lineTo(3,5);q.quadTo(3,3,5,3);q.lineTo(10,3);q.lineTo(12,6);q.lineTo(19,6);q.quadTo(21,6,21,8);q.lineTo(21,19);q.quadTo(21,21,19,21);q.lineTo(5,21);q.quadTo(3,21,3,19);q.close();c.drawPath(q,p);break;}
            case "heart":case "heartFill":{Path q=new Path();q.moveTo(12,21);q.cubicTo(10,19,2,13,2,7.5f);q.cubicTo(2,2,9,1,12,6);q.cubicTo(15,1,22,2,22,7.5f);q.cubicTo(22,13,14,19,12,21);q.close();if(name.equals("heartFill"))p.setStyle(Paint.Style.FILL);c.drawPath(q,p);break;}
            case "search":c.drawCircle(10.5f,10.5f,6.5f,p);line(c,16,16,21,21);break;
            case "settings":c.drawCircle(12,12,3.1f,p);{Path q=new Path();for(int i=0;i<24;i++){double a=(i*15-90)*Math.PI/180;float r=(i%3==0||i%3==1)?10:8;float x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)q.moveTo(x,y);else q.lineTo(x,y);}q.close();c.drawPath(q,p);}break;
            case "camera":rect(c,2,6,22,20,3);line(c,7,6,9,3,15,3,17,6);c.drawCircle(12,13,4,p);break;
            case "video":rect(c,3,4,21,20,3);line(c,7,4,7,20);line(c,17,4,17,20);line(c,3,9,7,9);line(c,3,15,7,15);line(c,17,9,21,9);line(c,17,15,21,15);line(c,10,9,14,12,10,15,10,9);break;
            case "play":p.setStyle(Paint.Style.FILL);line(c,8,4,21,12,8,20,8,4);break;
            case "pause":p.setStyle(Paint.Style.FILL);rect(c,6,4,10,20,1);rect(c,14,4,18,20,1);break;
            case "back":line(c,14,5,7,12,14,19);break;
            case "chevron":line(c,9,5,16,12,9,19);break;
            case "down":line(c,5,9,12,16,19,9);break;
            case "close":line(c,6,6,18,18);line(c,18,6,6,18);break;
            case "check":line(c,4,12,10,18,21,6);break;
            case "checkCircle":c.drawCircle(12,12,9,p);line(c,7,12,10.5f,15.5f,17,9);break;
            case "plus":line(c,12,5,12,19);line(c,5,12,19,12);break;
            case "more":p.setStyle(Paint.Style.FILL);c.drawCircle(12,5,1.5f,p);c.drawCircle(12,12,1.5f,p);c.drawCircle(12,19,1.5f,p);break;
            case "grid":rect(c,3,3,10,10,2);rect(c,14,3,21,10,2);rect(c,3,14,10,21,2);rect(c,14,14,21,21,2);break;
            case "sort":line(c,7,3,7,21);line(c,3,7,7,3,11,7);line(c,17,3,17,21);line(c,13,17,17,21,21,17);break;
            case "refresh":c.drawArc(4,4,20,20,35,290,false,p);line(c,21,5,21,11,15,11);break;
            case "share":c.drawCircle(18,4,3,p);c.drawCircle(5,12,3,p);c.drawCircle(18,20,3,p);line(c,8,10.5f,15,5.5f);line(c,8,13.5f,15,18.5f);break;
            case "trash":line(c,3,6,21,6);line(c,9,3,15,3);line(c,5,6,6,21,18,21,19,6);line(c,10,10,10,17);line(c,14,10,14,17);break;
            case "restore":c.drawArc(4,4,21,21,-75,280,false,p);line(c,3,3,3,9,9,9);line(c,12,8,12,13,16,15);break;
            case "info":c.drawCircle(12,12,9,p);line(c,12,11,12,17);p.setStyle(Paint.Style.FILL);c.drawCircle(12,7,1,p);break;
            case "sun":c.drawCircle(12,12,4,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(c,12+7*(float)Math.cos(a),12+7*(float)Math.sin(a),12+10*(float)Math.cos(a),12+10*(float)Math.sin(a));}break;
            case "volume":line(c,3,9,7,9,12,5,12,19,7,15,3,15,3,9);c.drawArc(9,6,21,18,-55,110,false,p);c.drawArc(6,3,24,21,-50,100,false,p);break;
            case "mute":line(c,3,9,7,9,12,5,12,19,7,15,3,15,3,9);line(c,17,9,22,14);line(c,22,9,17,14);break;
            case "lock":rect(c,5,10,19,22,3);c.drawArc(7,2,17,16,180,180,false,p);line(c,12,15,12,18);break;
            case "unlock":rect(c,5,10,19,22,3);c.drawArc(7,2,17,16,180,155,false,p);line(c,12,15,12,18);break;
            case "fullscreen":line(c,3,9,3,3,9,3);line(c,15,3,21,3,21,9);line(c,21,15,21,21,15,21);line(c,9,21,3,21,3,15);break;
            case "rotate":c.drawArc(5,5,19,19,40,285,false,p);line(c,20,3,20,9,14,9);break;
            case "copy":rect(c,8,8,22,22,3);line(c,16,4,16,2,2,2,2,16,4,16);break;
            case "move":line(c,3,12,21,12);line(c,16,7,21,12,16,17);line(c,3,7,3,17);break;
            case "crop":line(c,7,2,7,17,22,17);line(c,2,7,17,7,17,22);break;
            case "mosaic":rect(c,3,3,10,10,1);rect(c,14,3,21,10,1);rect(c,3,14,10,21,1);p.setStyle(Paint.Style.FILL);rect(c,14,14,21,21,1);break;
            case "blur":c.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);for(int y=6;y<=18;y+=4)for(int x=6;x<=18;x+=4)if((x-12)*(x-12)+(y-12)*(y-12)<50)c.drawCircle(x,y,(x-3)/8f,p);break;
            case "draw":line(c,3,20,8,19,21,6,18,3,5,16,3,20);line(c,14,7,17,10);break;
            case "undo":line(c,8,3,3,8,8,13);line(c,3,8,15,8);c.drawArc(9,8,21,20,-90,240,false,p);break;
            case "save":line(c,5,3,18,3,21,6,21,21,3,21,3,3,5,3);rect(c,7,3,16,10,1);rect(c,7,15,17,21,1);break;
            case "resize":line(c,3,9,3,3,9,3);line(c,15,21,21,21,21,15);line(c,3,3,10,10);line(c,21,21,14,14);break;
            case "edit":line(c,4,16,16,4,20,8,8,20,3,21,4,16);line(c,13,7,17,11);break;
            case "eye":{Path q=new Path();q.moveTo(1,12);q.quadTo(12,-3,23,12);q.quadTo(12,27,1,12);c.drawPath(q,p);c.drawCircle(12,12,3,p);break;}
            case "hide":{Path q=new Path();q.moveTo(1,12);q.quadTo(12,-3,23,12);q.quadTo(12,27,1,12);c.drawPath(q,p);line(c,3,3,21,21);break;}
            case "slideshow":rect(c,2,3,22,18,3);line(c,8,23,12,18,16,23);line(c,10,7,16,11,10,15,10,7);break;
            case "gesture":line(c,9,20,5,14,5,12,7,12,10,15,10,4);c.drawArc(10,2,14,6,180,180,false,p);line(c,14,4,14,11,17,10,20,12,20,18,17,21,11,21);break;
            case "palette":{Path q=new Path();q.moveTo(21,13);q.cubicTo(24,3,9,-1,3,6);q.cubicTo(-2,13,3,23,10,22);q.cubicTo(14,22,11,17,15,17);q.cubicTo(19,18,21,16,21,13);c.drawPath(q,p);c.drawCircle(7,9,1,p);c.drawCircle(12,6,1,p);c.drawCircle(18,9,1,p);break;}
            case "speed":c.drawArc(3,3,21,21,155,230,false,p);line(c,12,12,18,7);c.drawCircle(12,12,1.5f,p);break;
            case "loop":line(c,19,3,23,7,19,11);line(c,23,7,6,7,3,10);line(c,5,21,1,17,5,13);line(c,1,17,18,17,21,14);break;
            case "calendar":rect(c,3,5,21,22,3);line(c,3,10,21,10);line(c,7,2,7,7);line(c,17,2,17,7);line(c,8,14,8,17);line(c,13,14,16,14);break;
            case "select":rect(c,3,3,21,21,4);line(c,7,12,10,15,17,8);break;
            default:c.drawCircle(12,12,8,p);
        }
        c.restore();
    }
    @Override public int getIntrinsicWidth(){return 24;}
    @Override public int getIntrinsicHeight(){return 24;}
    @Override public void setAlpha(int a){p.setAlpha(a);}
    @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
