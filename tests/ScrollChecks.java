package app.galeria;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Native finger gestures in a focused window, independently of editor/export timing. */
public final class ScrollChecks extends Instrumentation {
    private MainActivity activity;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    private Object field(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private interface Condition {boolean ok()throws Exception;}
    private void await(Condition condition,String message)throws Exception {
        long until=SystemClock.elapsedRealtime()+30000;
        while(SystemClock.elapsedRealtime()<until){if(condition.ok())return;Thread.sleep(100);}
        throw new AssertionError(message);
    }
    private void shell(String command)throws Exception {try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))){byte[] b=new byte[512];while(in.read(b)>0){}}}
    private void pass(String text){Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(0,b);}
    @Override public void onStart(){Bundle result=new Bundle();
        try{
            Context context=getTargetContext();new Prefs(context).set("app_lock",false);
            activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->activity.hasWindowFocus()&&(boolean)field(activity,"loaded"),"Gallery is not focused and loaded.");
            List<MediaItem> real=MediaRepo.query(context,false);if(real.isEmpty())throw new AssertionError("Missing media fixture.");
            runOnMainSync(()->{try{((MediaRepo)field(activity,"repo")).stop();}catch(Exception e){throw new RuntimeException(e);}});
            ListView list=(ListView)field(activity,"list");MediaGridAdapter adapter=(MediaGridAdapter)field(activity,"adapter");View navigation=(View)field(activity,"navigation");ScrollIndicator indicator=(ScrollIndicator)field(activity,"scrollIndicator");
            List<MediaItem> many=new ArrayList<>();for(int i=0;i<240;i++)many.add(real.get(0));
            runOnMainSync(()->{adapter.media(many,false);list.setVisibility(View.VISIBLE);try{((View)field(activity,"empty")).setVisibility(View.GONE);}catch(Exception e){throw new RuntimeException(e);}list.setSelectionFromTop(0,0);});
            await(()->list.getChildCount()>2,"Rows are not laid out.");Thread.sleep(350);
            int[] position=new int[2];runOnMainSync(()->list.getLocationOnScreen(position));
            int x=position[0]+list.getWidth()/2, low=position[1]+list.getHeight()*3/4, high=position[1]+list.getHeight()/3;
            shell("input swipe "+x+" "+low+" "+x+" "+high+" 650");
            await(()->navigation.getHeight()==0,"Downward finger scroll did not hide navigation.");
            await(()->(int)field(indicator,"state")==AbsListView.OnScrollListener.SCROLL_STATE_IDLE,"List is still flinging.");
            shell("input swipe "+x+" "+high+" "+x+" "+low+" 650");
            await(()->navigation.getHeight()>=Ui.dp(context,70),"Upward finger scroll did not show navigation: height="+navigation.getHeight()+", first="+list.getFirstVisiblePosition()+", hidden="+field(activity,"navHidden"));
            pass("Native finger scroll: navigation hides downward and returns upward");
            await(()->(int)field(indicator,"state")==AbsListView.OnScrollListener.SCROLL_STATE_IDLE,"List is still flinging after return.");
            runOnMainSync(()->list.setSelectionFromTop(10,-30));Thread.sleep(350);float a=(float)field(indicator,"fraction");
            runOnMainSync(()->list.setSelectionFromTop(10,-33));Thread.sleep(350);float b=(float)field(indicator,"fraction");
            if(!(b>a&&b-a<.001f))throw new AssertionError("Scrollbar skips partial pixels: "+a+" -> "+b);
            pass("Scrollbar: a 3px movement updates the fraction continuously within one row");
            result.putString("stream","PASS: 2 native scroll checks\n");
        }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));}
        finish(Activity.RESULT_OK,result);
    }
}
