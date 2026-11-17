package app.galeria;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import java.util.concurrent.*;

public final class ZoomImageView extends View implements Drawable.Callback {
    public interface Gestures {
        void tap();
        void navigate(int direction);
        boolean canNavigate(int direction);
        void swipe(float offset);
        void swipeEnd(float offset,boolean commit);
        void verticalStart(boolean right);
        void vertical(boolean right,float delta);
        void verticalEnd();
        void error(String message);
    }
    private final Prefs prefs;private final Gestures listener;
    private final GestureDetector taps;private final ScaleGestureDetector pinch;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private Drawable image;private String message="Carregando imagem…";private int generation;
    private int rotation;
    private float fit=1,zoom=1,panX,panY,downX,downY,lastX,lastY,swipe;
    private int drag;private boolean multiple,right,foreground=true;private ValueAnimator animator;
    public ZoomImageView(Context c,Gestures g){
        super(c);prefs=new Prefs(c);listener=g;setBackgroundColor(Color.BLACK);setFocusable(true);
        setContentDescription("Foto. Duplo toque para ampliar, pinça para zoom, deslize para navegar.");
        taps=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(android.view.MotionEvent e){return true;}
            @Override public boolean onSingleTapConfirmed(MotionEvent e){listener.tap();performClick();return true;}
            @Override public boolean onDoubleTap(MotionEvent e){
                if(prefs.bool("photo_double",true)&&image!=null)animateZoom(e.getX(),e.getY());return true;
            }
        });
        pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector d){
                if(drag==1)listener.swipeEnd(0,false);
                multiple=true;drag=0;swipe=0;if(animator!=null)animator.cancel();return prefs.bool("pinch",true)&&image!=null;
            }
            @Override public boolean onScale(ScaleGestureDetector d){
                float next=Math.max(1,Math.min(6,zoom*d.getScaleFactor())),ratio=next/zoom;
                panX=d.getFocusX()-getWidth()/2f-(d.getFocusX()-getWidth()/2f-panX)*ratio;
                panY=d.getFocusY()-getHeight()/2f-(d.getFocusY()-getHeight()/2f-panY)*ratio;
                zoom=next;constrain();invalidate();return true;
            }
        });
        pinch.setQuickScaleEnabled(false);pinch.setStylusScaleEnabled(false);
    }
    public void load(MediaItem item){
        int token=++generation;if(animator!=null)animator.cancel();stopDrawable();
        Bitmap preview=ThumbLoader.get(getContext()).preview(item);
        image=preview==null?null:new BitmapDrawable(getResources(),preview);
        zoom=1;panX=panY=swipe=0;message="Carregando imagem…";resetFit();invalidate();
        worker.execute(()->{
            Drawable drawable=null;String error=null;
            try{
                if(Build.VERSION.SDK_INT>=28){
                    drawable=ImageDecoder.decodeDrawable(ImageDecoder.createSource(getContext().getContentResolver(),item.uri),(decoder,info,source)->{
                        int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                        float ratio=(float)Math.min(1,Math.min(6000.0/Math.max(w,h),Math.sqrt(8000000.0/((double)w*h))));
                        decoder.setTargetSize(Math.max(1,Math.round(w*ratio)),Math.max(1,Math.round(h*ratio)));
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
                }else{
                    Bitmap b=ThumbLoader.decodePhoto(getContext(),item,2800);if(b!=null)drawable=new BitmapDrawable(getResources(),b);
                }
                if(drawable==null)error="Não foi possível abrir esta imagem.";
            }catch(Exception | OutOfMemoryError e){
                try{Bitmap b=ThumbLoader.decodePhoto(getContext(),item,1600);if(b!=null)drawable=new BitmapDrawable(getResources(),b);}catch(Exception | OutOfMemoryError ignored){}
                if(drawable==null)error="Não foi possível abrir esta imagem.";
            }
            final Drawable result=drawable;final String failure=error;
            main.post(()->{
                if(token!=generation){if(Build.VERSION.SDK_INT>=28&&result instanceof AnimatedImageDrawable)((AnimatedImageDrawable)result).stop();return;}
                image=result;message=failure;resetFit();
                if(image!=null){image.setCallback(this);if(image instanceof BitmapDrawable)((BitmapDrawable)image).setFilterBitmap(true);if(foreground&&Build.VERSION.SDK_INT>=28&&image instanceof AnimatedImageDrawable)((AnimatedImageDrawable)image).start();}
                invalidate();if(failure!=null)listener.error(failure);
            });
        });
    }
    private void stopDrawable(){if(image!=null){if(Build.VERSION.SDK_INT>=28&&image instanceof AnimatedImageDrawable)((AnimatedImageDrawable)image).stop();image.setCallback(null);}}
    public void lifecycle(boolean visible){foreground=visible;if(Build.VERSION.SDK_INT>=28&&image instanceof AnimatedImageDrawable){if(visible)((AnimatedImageDrawable)image).start();else ((AnimatedImageDrawable)image).stop();}}
    public void close(){generation++;stopDrawable();image=null;worker.shutdownNow();if(animator!=null)animator.cancel();}
    public boolean zoomed(){return zoom>1.03f;}
    public void reset(){zoom=1;panX=panY=swipe=0;invalidate();}
    public void rotate(int degrees){rotation=((degrees%360)+360)%360;reset();resetFit();invalidate();}
    private int imageWidth(){return rotation%180==0?image.getIntrinsicWidth():image.getIntrinsicHeight();}
    private int imageHeight(){return rotation%180==0?image.getIntrinsicHeight():image.getIntrinsicWidth();}
    private void resetFit(){if(image!=null&&getWidth()>0&&getHeight()>0)fit=Math.min(getWidth()/(float)Math.max(1,imageWidth()),getHeight()/(float)Math.max(1,imageHeight()));constrain();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);resetFit();}
    private void constrain(){
        if(image==null)return;
        float mx=Math.max(0,(imageWidth()*fit*zoom-getWidth())/2f),my=Math.max(0,(imageHeight()*fit*zoom-getHeight())/2f);
        panX=Math.max(-mx,Math.min(mx,panX));panY=Math.max(-my,Math.min(my,panY));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(image==null){
            Paint p=new Paint(3);p.setColor(Ui.MUTED);p.setTextSize(Ui.dp(getContext(),14));p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(message==null?"":message,getWidth()/2f,getHeight()/2f,p);return;
        }
        int w=image.getIntrinsicWidth(),h=image.getIntrinsicHeight();
        canvas.save();canvas.translate(getWidth()/2f+panX,getHeight()/2f+panY);canvas.scale(fit*zoom,fit*zoom);canvas.rotate(rotation);canvas.translate(-w/2f,-h/2f);image.setBounds(0,0,w,h);image.draw(canvas);canvas.restore();
    }
    private void animateZoom(float x,float y){
        if(animator!=null)animator.cancel();
        final float from=zoom,to=zoom>1.1f?1:3,px=panX,py=panY;
        float tx=x-getWidth()/2f-(x-getWidth()/2f-px)*(to/from),ty=y-getHeight()/2f-(y-getHeight()/2f-py)*(to/from);
        final float targetX=to==1?0:tx,targetY=to==1?0:ty;
        animator=ValueAnimator.ofFloat(0,1);animator.setDuration(260);animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a->{float t=(float)a.getAnimatedValue();zoom=from+(to-from)*t;panX=px+(targetX-px)*t;panY=py+(targetY-py)*t;constrain();invalidate();});animator.start();
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){downX=lastX=e.getX();downY=lastY=e.getY();drag=0;multiple=false;swipe=0;if(animator!=null)animator.cancel();}
        if(e.getPointerCount()>1)multiple=true;
        pinch.onTouchEvent(e);taps.onTouchEvent(e);
        if(action==MotionEvent.ACTION_MOVE && e.getPointerCount()==1 && !pinch.isInProgress() && !multiple){
            float dx=e.getX()-downX,dy=e.getY()-downY;
            if(zoomed()){
                panX+=(e.getX()-lastX)*1.6f;panY+=(e.getY()-lastY)*1.6f;constrain();invalidate();
            }else{
                if(drag==0&&Math.max(Math.abs(dx),Math.abs(dy))>Ui.dp(getContext(),18)){
                    right=downX>getWidth()/2f;
                    if(Math.abs(dy)>Math.abs(dx)*1.25f&&(right?prefs.bool("brightness_gesture",true):prefs.bool("volume_gesture",true))){drag=2;listener.verticalStart(right);}
                    else if(Math.abs(dx)>Math.abs(dy)*1.25f&&prefs.bool("swipe_nav",true))drag=1;
                }
                if(drag==1){swipe=dx*.9f;listener.swipe(swipe);}
                if(drag==2)listener.vertical(right,-dy/Math.max(1,getHeight()));
            }
            lastX=e.getX();lastY=e.getY();
        }
        if(action==MotionEvent.ACTION_POINTER_UP){lastX=e.getX();lastY=e.getY();}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            if(drag==2)listener.verticalEnd();
            if(drag==1){
                boolean commit=action==MotionEvent.ACTION_UP&&Math.abs(e.getX()-downX)>getWidth()*.22f;
                listener.swipeEnd(swipe,commit);swipe=0;
            }
            drag=0;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void invalidateDrawable(Drawable d){invalidate();}
    @Override public void scheduleDrawable(Drawable d,Runnable what,long when){postDelayed(what,Math.max(0,when-SystemClock.uptimeMillis()));}
    @Override public void unscheduleDrawable(Drawable d,Runnable what){removeCallbacks(what);}
}
