package app.galeria;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Continuous pixel position, including the partially visible first row. */
public final class ScrollIndicator extends View implements AbsListView.OnScrollListener {
    public interface Direction {void changed(boolean down);}
    private final ListView list;private final MediaGridAdapter adapter;private final Direction direction;
    private final Paint paint=new Paint(3);private final RectF rect=new RectF();
    private boolean dragging;private float fraction,lastOffset=Float.NaN,travel;private int state;
    public ScrollIndicator(Context c,ListView l,MediaGridAdapter a){this(c,l,a,null);}
    public ScrollIndicator(Context c,ListView l,MediaGridAdapter a,Direction d){super(c);list=l;adapter=a;direction=d;setContentDescription("Posição na biblioteca. Arraste para percorrer os arquivos.");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);list.setOnScrollListener(this);}
    @Override public void onScrollStateChanged(AbsListView v,int s){state=s;if(s==SCROLL_STATE_IDLE)travel=0;}
    private float extent(){return Math.max(0,adapter.totalPixels(list.getWidth())+list.getPaddingBottom()+list.getPaddingTop()-list.getHeight());}
    @Override public void onScroll(AbsListView v,int row,int shown,int count){
        if(list.getWidth()<=0||shown==0)return;View child=list.getChildAt(0);float offset=Math.max(0,adapter.offsetOf(row,list.getWidth())-(child==null?0:child.getTop()-list.getPaddingTop()));
        if(!dragging)fraction=extent()==0?0:Math.max(0,Math.min(1,offset/extent()));
        if((state!=SCROLL_STATE_IDLE||dragging)&&!Float.isNaN(lastOffset)){
            float delta=offset-lastOffset;if(delta*travel<0)travel=0;travel+=delta;
            if(Math.abs(travel)>=Ui.dp(getContext(),8)){if(direction!=null)direction.changed(travel>0);travel=0;}
        }
        lastOffset=offset;postInvalidateOnAnimation();
    }
    public void changed(){lastOffset=Float.NaN;travel=0;onScroll(list,list.getFirstVisiblePosition(),list.getChildCount(),adapter.getCount());invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(adapter.totalItems()==0||list.getVisibility()!=VISIBLE||extent()<=0)return;float dp=getResources().getDisplayMetrics().density,x=getWidth()-7*dp,top=18*dp,bottom=Math.max(top,getHeight()-18*dp),thumb=46*dp,y=top+fraction*Math.max(0,bottom-top-thumb);
        paint.setColor(0x304F4267);rect.set(x-1.25f*dp,top,x+1.25f*dp,bottom);c.drawRoundRect(rect,2*dp,2*dp,paint);
        paint.setColor(0xBF000000|(Ui.ACCENT&0xffffff));rect.set(x-2*dp,y,x+2*dp,y+thumb);c.drawRoundRect(rect,3*dp,3*dp,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e){if(adapter.totalItems()==0||extent()<=0)return false;int action=e.getActionMasked();float dp=getResources().getDisplayMetrics().density;
        if(action==MotionEvent.ACTION_DOWN){if(e.getX()<getWidth()-32*dp)return false;dragging=true;getParent().requestDisallowInterceptTouchEvent(true);}
        if(!dragging)return false;
        if(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_DOWN){fraction=Math.max(0,Math.min(1,(e.getY()-41*dp)/Math.max(1,getHeight()-82*dp)));float offset=fraction*extent();int row=adapter.rowAtOffset(offset,list.getWidth());list.setSelectionFromTop(row,Math.round(adapter.offsetOf(row,list.getWidth())-offset));postInvalidateOnAnimation();}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);travel=0;performClick();}return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
