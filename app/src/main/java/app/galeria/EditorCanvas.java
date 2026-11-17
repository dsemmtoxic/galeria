package app.galeria;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.util.*;
import java.util.concurrent.*;

public final class EditorCanvas extends View {
    public static final int NONE=-1,CROP=0,MOSAIC=1,BLUR=2,DRAW=3;
    private Bitmap source,preview,mosaicPreview,blurPreview;private EditSpec spec=new EditSpec();private int tool=NONE,color=0xFFB497FF;private float brush=.012f,aspect;
    private final Paint paint=new Paint(3);private final RectF bounds=new RectF();private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayDeque<EditSpec> undo=new ArrayDeque<>();private final Runnable changed;private int token,handle;private float downX,downY;private RectF beforeCrop;private EditSpec.Stroke stroke;private EditSpec.FilterStroke filterStroke;private float filterBrush=.08f;private EditSpec beforeGesture;private boolean busy,gestureActive;
    public EditorCanvas(Context c,Runnable update){super(c);changed=update;setBackgroundColor(Color.BLACK);setContentDescription("Área de edição. Escolha uma ferramenta. Borre, aplique mosaico ou desenhe arrastando o dedo.");setFocusable(true);}
    public void load(Bitmap bitmap,int rotation){source=bitmap;spec.rotation=rotation;refresh();}
    public void frame(Bitmap bitmap){if(bitmap==null||gestureActive)return;source=bitmap;refresh();}
    public EditSpec snapshot(){return spec.copy();}
    public boolean dirty(){return spec.changed();}
    public int sourceWidth(){return source==null?0:source.getWidth();}public int sourceHeight(){return source==null?0:source.getHeight();}
    public void tool(int t){tool=t;filterStroke=null;stroke=null;invalidate();}
    public int tool(){return tool;}
    public void color(int c){color=c;}public void brush(float w){brush=w;}public void filterBrush(float w){filterBrush=w;}public float filterBrush(){return filterBrush;}
    public boolean canUndo(){return !undo.isEmpty();}
    private void remember(){undo.push(spec.copy());while(undo.size()>24)undo.removeLast();}
    public void undo(){if(undo.isEmpty())return;spec=undo.pop();aspect=0;refresh();changed.run();}
    public void rotate(){if(source==null)return;remember();spec.rotate();if(aspect>0)aspect=1/aspect;refresh();changed.run();}
    public void dimensions(int w,int h){remember();spec.targetWidth=w;spec.targetHeight=h;changed.run();invalidate();}
    public void aspect(float value){if(source==null)return;remember();aspect=value;if(value>0)spec.crop=new RectF(0,0,1,1);fitAspect(spec.crop);changed.run();invalidate();}
    private float sourceAspect(){int w=sourceWidth(),h=sourceHeight();return spec.rotation%180==0?w/(float)Math.max(1,h):h/(float)Math.max(1,w);}
    private void fitAspect(RectF r){if(aspect<=0)return;float ratio=aspect/sourceAspect(),cx=r.centerX(),cy=r.centerY(),w=r.width(),h=r.height();if(w/h>ratio)w=h*ratio;else h=w/ratio;r.set(cx-w/2,cy-h/2,cx+w/2,cy+h/2);}
    private void refresh(){
        if(source==null)return;final int generation=++token;EditSpec render=new EditSpec();render.rotation=spec.rotation;busy=true;final Bitmap input=source;
        worker.execute(()->{if(generation!=token)return;Bitmap result=null,mosaic=null,blurred=null;
            try{result=EditSpec.render(input,render,1600);mosaic=EditSpec.filteredBitmap(result,true);blurred=EditSpec.filteredBitmap(result,false);}catch(Exception|OutOfMemoryError ignored){}
            final Bitmap b=result,m=mosaic,f=blurred;main.post(()->{
                if(generation!=token){recycle(b,input);recycle(m,b);recycle(f,b);return;}
                recycle(mosaicPreview,preview);recycle(blurPreview,preview);recycle(preview,source);preview=b;mosaicPreview=m;blurPreview=f;busy=false;invalidate();
            });});invalidate();
    }
    private static void recycle(Bitmap bitmap,Bitmap keep){if(bitmap!=null&&bitmap!=keep&&!bitmap.isRecycled())bitmap.recycle();}
    private void bounds(){if(source==null)return;float rw=spec.rotation%180==0?source.getWidth():source.getHeight(),rh=spec.rotation%180==0?source.getHeight():source.getWidth();float scale=Math.min((getWidth()-Ui.dp(getContext(),28))/rw,(getHeight()-Ui.dp(getContext(),36))/rh);float w=Math.max(1,rw*scale),h=Math.max(1,rh*scale);bounds.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);}
    private RectF screen(RectF r){return new RectF(bounds.left+r.left*bounds.width(),bounds.top+r.top*bounds.height(),bounds.left+r.right*bounds.width(),bounds.top+r.bottom*bounds.height());}
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(source==null)return;bounds();paint.setFilterBitmap(true);paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);if(preview!=null)c.drawBitmap(preview,null,bounds,paint);
        int rw=spec.rotation%180==0?sourceWidth():sourceHeight(),rh=spec.rotation%180==0?sourceHeight():sourceWidth();c.save();c.translate(bounds.left,bounds.top);c.scale(bounds.width()/Math.max(1,rw),bounds.height()/Math.max(1,rh));for(EditSpec.FilterStroke filter:spec.filters)EditSpec.drawFilter(c,filter,filter.mosaic?mosaicPreview:blurPreview,rw,rh);if(filterStroke!=null)EditSpec.drawFilter(c,filterStroke,filterStroke.mosaic?mosaicPreview:blurPreview,rw,rh);EditSpec.drawStrokes(c,spec.strokes,rw,rh);if(stroke!=null)EditSpec.drawStrokes(c,Collections.singletonList(stroke),rw,rh);c.restore();
        RectF crop=screen(spec.crop);paint.setColor(0xA9000000);c.drawRect(bounds.left,bounds.top,bounds.right,crop.top,paint);c.drawRect(bounds.left,crop.bottom,bounds.right,bounds.bottom,paint);c.drawRect(bounds.left,crop.top,crop.left,crop.bottom,paint);c.drawRect(crop.right,crop.top,bounds.right,crop.bottom,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),1));paint.setColor(Ui.ACCENT);if(tool==CROP)c.drawRect(crop,paint);
        if(tool==CROP){paint.setColor(0x88FFFFFF);for(int i=1;i<3;i++){c.drawLine(crop.left+crop.width()*i/3,crop.top,crop.left+crop.width()*i/3,crop.bottom,paint);c.drawLine(crop.left,crop.top+crop.height()*i/3,crop.right,crop.top+crop.height()*i/3,paint);}paint.setColor(Ui.ACCENT);paint.setStrokeWidth(Ui.dp(getContext(),3));float l=Ui.dp(getContext(),18);c.drawLine(crop.left,crop.top,crop.left+l,crop.top,paint);c.drawLine(crop.left,crop.top,crop.left,crop.top+l,paint);c.drawLine(crop.right,crop.top,crop.right-l,crop.top,paint);c.drawLine(crop.right,crop.top,crop.right,crop.top+l,paint);c.drawLine(crop.left,crop.bottom,crop.left+l,crop.bottom,paint);c.drawLine(crop.left,crop.bottom,crop.left,crop.bottom-l,paint);c.drawLine(crop.right,crop.bottom,crop.right-l,crop.bottom,paint);c.drawLine(crop.right,crop.bottom,crop.right,crop.bottom-l,paint);}
        paint.setStyle(Paint.Style.FILL);
    }
    @Override public boolean onTouchEvent(MotionEvent e){int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN)gestureActive=false;if(source==null||busy||tool==NONE)return true;if(a!=MotionEvent.ACTION_DOWN&&!gestureActive)return true;bounds();float x=Math.max(0,Math.min(1,(e.getX()-bounds.left)/bounds.width())),y=Math.max(0,Math.min(1,(e.getY()-bounds.top)/bounds.height()));
        if(a==MotionEvent.ACTION_DOWN){if(!bounds.contains(e.getX(),e.getY()))return false;gestureActive=true;beforeGesture=spec.copy();downX=x;downY=y;beforeCrop=new RectF(spec.crop);getParent().requestDisallowInterceptTouchEvent(true);
            if(tool==CROP){float threshold=Ui.dp(getContext(),24)/bounds.width();handle=0;if(Math.abs(x-spec.crop.left)<threshold)handle|=1;if(Math.abs(x-spec.crop.right)<threshold)handle|=4;if(Math.abs(y-spec.crop.top)<threshold)handle|=2;if(Math.abs(y-spec.crop.bottom)<threshold)handle|=8;if(handle==0&&!spec.crop.contains(x,y))handle=16;}
            else if(tool==DRAW){stroke=new EditSpec.Stroke(color,brush);stroke.points.add(new PointF(x,y));}
            else{filterStroke=new EditSpec.FilterStroke(tool==MOSAIC,filterBrush);filterStroke.points.add(new PointF(x,y));}invalidate();return true;}
        if(a==MotionEvent.ACTION_MOVE){if(tool==CROP){RectF r=new RectF(beforeCrop);if(handle==16){r.set(Math.min(x,downX),Math.min(y,downY),Math.max(x,downX),Math.max(y,downY));}else if(handle==0){r.offset(Math.max(-r.left,Math.min(1-r.right,x-downX)),Math.max(-r.top,Math.min(1-r.bottom,y-downY)));}else{if((handle&1)!=0)r.left=Math.min(x,r.right-.04f);if((handle&4)!=0)r.right=Math.max(x,r.left+.04f);if((handle&2)!=0)r.top=Math.min(y,r.bottom-.04f);if((handle&8)!=0)r.bottom=Math.max(y,r.top+.04f);}if(r.width()>.02f&&r.height()>.02f){if(handle!=0)fitAspect(r);spec.crop=r;}}
            else if(tool==DRAW&&stroke!=null)stroke.points.add(new PointF(x,y));else if(filterStroke!=null)filterStroke.points.add(new PointF(x,y));invalidate();return true;}
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){gestureActive=false;getParent().requestDisallowInterceptTouchEvent(false);
            if(a==MotionEvent.ACTION_CANCEL){spec=beforeGesture;stroke=null;filterStroke=null;invalidate();return true;}
            undo.push(beforeGesture);while(undo.size()>24)undo.removeLast();
            if(stroke!=null){spec.strokes.add(stroke);stroke=null;}if(filterStroke!=null){spec.filters.add(filterStroke);filterStroke=null;}
            changed.run();invalidate();performClick();return true;}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    public void close(){token++;main.removeCallbacksAndMessages(null);worker.shutdownNow();}
}
