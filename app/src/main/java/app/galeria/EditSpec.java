package app.galeria;

import android.graphics.*;
import java.util.*;

/** Coordinates are normalized in the clockwise-rotated source, before cropping. */
public final class EditSpec {
    public int rotation,targetWidth,targetHeight;
    public RectF crop=new RectF(0,0,1,1);
    public final List<Region> regions=new ArrayList<>();
    public final List<Stroke> strokes=new ArrayList<>();
    public final List<FilterStroke> filters=new ArrayList<>();
    public static final class Region {public RectF rect;public boolean mosaic;public Region(RectF r,boolean m){rect=new RectF(r);mosaic=m;}}
    public static class Stroke {public int color;public float width;public final List<PointF> points=new ArrayList<>();public Stroke(int c,float w){color=c;width=w;}}
    public static final class FilterStroke extends Stroke {public final boolean mosaic;public FilterStroke(boolean m,float w){super(m?Color.GREEN:Color.RED,w);mosaic=m;}}
    public EditSpec copy(){EditSpec s=new EditSpec();s.rotation=rotation;s.targetWidth=targetWidth;s.targetHeight=targetHeight;s.crop=new RectF(crop);for(Region r:regions)s.regions.add(new Region(r.rect,r.mosaic));for(Stroke a:strokes){Stroke b=new Stroke(a.color,a.width);for(PointF p:a.points)b.points.add(new PointF(p.x,p.y));s.strokes.add(b);}for(FilterStroke a:filters){FilterStroke b=new FilterStroke(a.mosaic,a.width);for(PointF p:a.points)b.points.add(new PointF(p.x,p.y));s.filters.add(b);}return s;}
    private RectF clockwise(RectF r){return new RectF(1-r.bottom,r.left,1-r.top,r.right);}
    public void rotate(){rotation=(rotation+90)%360;crop=clockwise(crop);for(Region r:regions)r.rect=clockwise(r.rect);for(Stroke s:strokes)for(PointF p:s.points){float x=p.x;p.x=1-p.y;p.y=x;}for(FilterStroke s:filters)for(PointF p:s.points){float x=p.x;p.x=1-p.y;p.y=x;}int t=targetWidth;targetWidth=targetHeight;targetHeight=t;}
    public boolean changed(){return rotation!=0||targetWidth>0||!regions.isEmpty()||!strokes.isEmpty()||!filters.isEmpty()||crop.left>0||crop.top>0||crop.right<1||crop.bottom<1;}
    public int[] outputSize(int baseW,int baseH,int limit){int w=rotation%180==0?baseW:baseH,h=rotation%180==0?baseH:baseW;w=Math.max(1,Math.round(w*crop.width()));h=Math.max(1,Math.round(h*crop.height()));if(targetWidth>0&&targetHeight>0){w=targetWidth;h=targetHeight;}float scale=Math.min(1,limit/(float)Math.max(w,h));return new int[]{Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale))};}
    public static Bitmap rotateBitmap(Bitmap base,int degrees){if(degrees%360==0)return base;Matrix m=new Matrix();m.setRotate(degrees);return Bitmap.createBitmap(base,0,0,base.getWidth(),base.getHeight(),m,true);}
    static Bitmap regionBitmap(Bitmap image,Region r){int x=Math.max(0,Math.min(image.getWidth()-1,Math.round(r.rect.left*image.getWidth()))),y=Math.max(0,Math.min(image.getHeight()-1,Math.round(r.rect.top*image.getHeight())));int w=Math.max(1,Math.min(image.getWidth()-x,Math.round(r.rect.width()*image.getWidth()))),h=Math.max(1,Math.min(image.getHeight()-y,Math.round(r.rect.height()*image.getHeight())));Bitmap part=Bitmap.createBitmap(image,x,y,w,h);float ratio=Math.min(1,(r.mosaic?32f:256f)/Math.max(w,h));Bitmap small=Bitmap.createScaledBitmap(part,Math.max(1,Math.round(w*ratio)),Math.max(1,Math.round(h*ratio)),!r.mosaic);if(part!=image&&part!=small)part.recycle();if(r.mosaic)return small;Bitmap out=small.copy(Bitmap.Config.ARGB_8888,true);if(small!=image)small.recycle();blur(out,8);return out;}
    private static void blur(Bitmap b,int radius){int w=b.getWidth(),h=b.getHeight();int[] src=new int[w*h],tmp=new int[w*h];b.getPixels(src,0,w,0,0,w,h);
        for(int pass=0;pass<3;pass++){for(int y=0;y<h;y++)for(int x=0;x<w;x++){int red=0,green=0,blue=0,count=0;for(int k=Math.max(0,x-radius);k<=Math.min(w-1,x+radius);k++){int v=src[y*w+k];red+=(v>>16)&255;green+=(v>>8)&255;blue+=v&255;count++;}tmp[y*w+x]=0xff000000|(red/count<<16)|(green/count<<8)|blue/count;}for(int y=0;y<h;y++)for(int x=0;x<w;x++){int red=0,green=0,blue=0,count=0;for(int k=Math.max(0,y-radius);k<=Math.min(h-1,y+radius);k++){int v=tmp[k*w+x];red+=(v>>16)&255;green+=(v>>8)&255;blue+=v&255;count++;}src[y*w+x]=0xff000000|(red/count<<16)|(green/count<<8)|blue/count;}}
        b.setPixels(src,0,w,0,0,w,h);
    }
    public static Path strokeShape(Stroke stroke,int w,int h){
        Path result=new Path();if(stroke.points.isEmpty())return result;float thickness=Math.max(1,stroke.width*Math.min(w,h));PointF first=stroke.points.get(0);
        if(stroke.points.size()==1){result.addCircle(first.x*w,first.y*h,thickness/2,Path.Direction.CW);return result;}
        Path line=new Path();line.moveTo(first.x*w,first.y*h);for(int i=1;i<stroke.points.size();i++){PointF q=stroke.points.get(i);line.lineTo(q.x*w,q.y*h);}
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(thickness);paint.getFillPath(line,result);return result;
    }
    public static void drawStrokes(Canvas c,List<? extends Stroke> strokes,int w,int h){Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);for(Stroke s:strokes){paint.setColor(s.color);c.drawPath(strokeShape(s,w,h),paint);}}
    static Bitmap filteredBitmap(Bitmap image,boolean mosaic){
        float f=Math.min(1,(mosaic?50f/Math.min(image.getWidth(),image.getHeight()):256f/Math.max(image.getWidth(),image.getHeight())));
        Bitmap small=Bitmap.createScaledBitmap(image,Math.max(1,Math.round(image.getWidth()*f)),Math.max(1,Math.round(image.getHeight()*f)),!mosaic);
        if(mosaic)return small;
        Bitmap filtered=small.copy(Bitmap.Config.ARGB_8888,true);if(small!=image)small.recycle();blur(filtered,8);return filtered;
    }
    static void drawFilter(Canvas c,FilterStroke stroke,Bitmap filtered,int w,int h){
        if(filtered==null||stroke.points.isEmpty())return;Paint paint=new Paint(3);paint.setFilterBitmap(!stroke.mosaic);
        c.save();c.clipPath(strokeShape(stroke,w,h));c.drawBitmap(filtered,null,new RectF(0,0,w,h),paint);c.restore();
    }
    public static Bitmap render(Bitmap base,EditSpec spec,int limit){
        Bitmap rotated=rotateBitmap(base,spec.rotation),mosaic=null,blurred=null;int w=rotated.getWidth(),h=rotated.getHeight();int[] size=spec.outputSize(base.getWidth(),base.getHeight(),limit);
        Bitmap out=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.BLACK);c.scale(size[0]/(spec.crop.width()*w),size[1]/(spec.crop.height()*h));c.translate(-spec.crop.left*w,-spec.crop.top*h);Paint paint=new Paint(3);c.drawBitmap(rotated,0,0,paint);
        try{
            for(Region r:spec.regions){Bitmap b=regionBitmap(rotated,r);paint.setFilterBitmap(!r.mosaic);c.drawBitmap(b,null,new RectF(r.rect.left*w,r.rect.top*h,r.rect.right*w,r.rect.bottom*h),paint);if(b!=rotated)b.recycle();}
            for(FilterStroke stroke:spec.filters){if(stroke.mosaic){if(mosaic==null)mosaic=filteredBitmap(rotated,true);drawFilter(c,stroke,mosaic,w,h);}else{if(blurred==null)blurred=filteredBitmap(rotated,false);drawFilter(c,stroke,blurred,w,h);}}
            drawStrokes(c,spec.strokes,w,h);return out;
        }finally{if(mosaic!=null&&mosaic!=rotated)mosaic.recycle();if(blurred!=null&&blurred!=rotated)blurred.recycle();if(rotated!=base)rotated.recycle();}
    }
}
