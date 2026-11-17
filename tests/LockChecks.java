package app.galeria;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import android.media.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
/** Focused regression for the Android credential/session boundary on a disposable emulator. */
public final class LockChecks extends Instrumentation {
    private final List<String> passed=new ArrayList<>();private MainActivity library;private SettingsActivity settings;private ViewerActivity viewer;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    private Object field(Object object,String name)throws Exception {Class<?> type=object.getClass();while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException e){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
    private void call(Object o,String name)throws Exception{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}
    private interface Condition {boolean ok()throws Exception;}
    private void await(Condition condition,String message)throws Exception {
        long deadline=SystemClock.elapsedRealtime()+45000;
        while(SystemClock.elapsedRealtime()<deadline){if(condition.ok())return;Thread.sleep(250);}
        throw new AssertionError(message);
    }
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void pass(String name){passed.add(name);Bundle b=new Bundle();b.putString("check",name);b.putString("stream",name+"\n");sendStatus(0,b);}
    private View findDescription(View v,String text){if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=findDescription(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
    private void shell(String cmd)throws Exception{try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(cmd))){byte[] b=new byte[4096];while(in.read(b)>0){}}}
    private android.view.accessibility.AccessibilityNodeInfo credentialNode(android.view.accessibility.AccessibilityNodeInfo node){if(node==null)return null;String text=node.getText()==null?"":node.getText().toString();String id=node.getViewIdResourceName()==null?"":node.getViewIdResourceName();if(text.equalsIgnoreCase("Use PIN")||text.equalsIgnoreCase("Usar PIN")||id.endsWith("device_credential_button"))return node;for(int i=0;i<node.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo found=credentialNode(node.getChild(i));if(found!=null)return found;}return null;}
    private void enterCredential()throws Exception{
        Thread.sleep(800);android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();android.view.accessibility.AccessibilityNodeInfo button=credentialNode(root);
        if(button!=null){button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);Thread.sleep(1000);}
        shell("input text 1234");shell("input keyevent 66");
    }

    @Override public void onStart(){Bundle result=new Bundle();Prefs prefs=new Prefs(getTargetContext());
        try{
            Context context=getTargetContext();prefs.set("app_lock",false);prefs.set("autoplay",true);
            if(!AppLock.deviceSecure(context))shell("locksettings set-pin 1234");
            library=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settings=(SettingsActivity)startActivitySync(new Intent(context,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            View option=findDescription(settings.getWindow().getDecorView(),"Bloquear ao abrir");check(option instanceof Switch,"Lock option missing.");runOnMainSync(()->((Switch)option).setChecked(true));await(()->AppLock.authenticating,"No native authentication prompt.");enterCredential();
            await(()->AppLock.enabled(context)&&AppLock.canAccess(context),"PIN did not enable lock.");pass("Native Android PIN enables startup lock");
            runOnMainSync(settings::finish);settings=null;shell("input keyevent 3");
            Thread.sleep(1200);check(AppLock.canAccess(context)&&!AppLock.authenticating,"Home relocked the current session.");
            runOnMainSync(()->library.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));Thread.sleep(1000);check(AppLock.canAccess(context)&&!AppLock.authenticating,"Foreground return requested another authentication.");pass("Home and foreground return preserve authentication");
            check((library.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)==0,"Screenshot flag still active.");android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();check(shot!=null&&shot.getWidth()>0,"Screenshot failed.");shot.recycle();pass("Screenshots allowed while startup protection is enabled");
            AppLock.beginTask(context,library.getTaskId(),true);check(AppLock.canAccess(context),"Restored background task did not retain its authenticated session.");pass("Restored task retains its session only within the same Android task and boot");
            runOnMainSync(library::finishAndRemoveTask);await(()->!AppLock.canAccess(context),"Closing task did not invalidate session.");pass("Closing the app task invalidates authentication");
            MediaItem movie=null;for(MediaItem m:MediaRepo.query(context,false))if(m.video&&m.duration>15000){movie=m;break;}check(movie!=null,"Long test movie missing.");
            viewer=(ViewerActivity)startActivitySync(new Intent(Intent.ACTION_VIEW).setClass(context,ViewerActivity.class).setData(movie.uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->field(viewer,"shield")!=null&&AppLock.authenticating,"Viewer bypasses lock.");
            check((viewer.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)==0,"Screenshot flag was re-enabled.");PlayerSurface player=(PlayerSurface)field(viewer,"player");if(player!=null)check(!player.playing(),"Playing while locked.");enterCredential();await(()->AppLock.canAccess(context)&&field(viewer,"shield")==null,"PIN did not reveal viewer.");if(player!=null)await(player::playing,"Video does not resume after auth.");pass("External viewer blocks media/audio until Android PIN succeeds");
            result.putString("stream","PASS: 6 focused lock checks\n");
        }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));}
        finally{prefs.set("app_lock",false);prefs.set("autoplay",false);try{if(settings!=null)runOnMainSync(settings::finish);if(viewer!=null)runOnMainSync(viewer::finish);}catch(Exception ignored){}}
        finish(Activity.RESULT_OK,result);
    }
}
