package app.galeria;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ListView;

/** Detects finger direction even at a list boundary where content cannot move. */
public final class GalleryListView extends ListView {
    private final ScrollIndicator.Direction direction;private float x,y,dx,dy;
    public GalleryListView(Context context,ScrollIndicator.Direction callback){super(context);direction=callback;}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();dx=dy=0;}
        else if(action==MotionEvent.ACTION_MOVE&&e.getPointerCount()==1){
            float step=e.getY()-y;if(step*dy<0)dy=0;dx+=e.getX()-x;dy+=step;x=e.getX();y=e.getY();
            if(Math.abs(dy)>Ui.dp(getContext(),12)&&Math.abs(dy)>Math.abs(dx)*1.2f){direction.changed(dy<0);dx=dy=0;}
        }
        return super.dispatchTouchEvent(e);
    }
}
