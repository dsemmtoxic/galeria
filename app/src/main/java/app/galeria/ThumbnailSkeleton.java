package app.galeria;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/** Lightweight shimmering placeholder; reuses its shader instead of allocating one per frame. */
final class ThumbnailSkeleton extends Drawable implements Runnable {
    private final Paint base=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shimmer=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix=new Matrix();
    private LinearGradient gradient;
    private boolean scheduled;
    private int alpha=255;
    ThumbnailSkeleton(){base.setColor(0xFF292934);}
    @Override protected void onBoundsChange(Rect bounds){
        super.onBoundsChange(bounds);
        float width=Math.max(1,bounds.width());
        gradient=new LinearGradient(-width*.38f,0,width*.38f,0,
                new int[]{0x002D2D38,0x4A8C8C9A,0x002D2D38},null,Shader.TileMode.CLAMP);
        shimmer.setShader(gradient);
    }
    @Override public void draw(Canvas canvas){
        Rect bounds=getBounds();if(bounds.isEmpty())return;
        base.setAlpha(alpha);canvas.drawRect(bounds,base);
        float phase=(SystemClock.uptimeMillis()%1250)/1250f;
        float origin=bounds.left+(phase*2f-.5f)*bounds.width();
        if(gradient!=null){
            matrix.setTranslate(origin,bounds.top);gradient.setLocalMatrix(matrix);
            shimmer.setAlpha(alpha);canvas.drawRect(bounds,shimmer);
        }
        if(getCallback()!=null&&!scheduled){scheduled=true;scheduleSelf(this,SystemClock.uptimeMillis()+65);}
    }
    @Override public void run(){scheduled=false;if(getCallback()!=null)invalidateSelf();}
    @Override public void setAlpha(int a){alpha=a;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){base.setColorFilter(filter);shimmer.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
