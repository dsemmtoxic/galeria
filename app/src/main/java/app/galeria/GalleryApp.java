package app.galeria;

import android.app.*;
import android.os.*;
import java.util.*;

public final class GalleryApp extends Application {
    private int sessionTask=-1;
    private final Set<Activity> roots=Collections.newSetFromMap(new IdentityHashMap<Activity,Boolean>());
    @Override public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
        public void onActivityCreated(Activity a,Bundle b){
            // A new task also covers reopening after removal from Recents while the process survives.
            if(sessionTask!=a.getTaskId()){sessionTask=a.getTaskId();AppLock.beginTask(GalleryApp.this,sessionTask,b!=null);}if(a.isTaskRoot())roots.add(a);
        }
        public void onActivityDestroyed(Activity a){
            if(roots.remove(a)&&a.isFinishing()&&a.getTaskId()==sessionTask){AppLock.lock();sessionTask=-1;}
        }
        public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}
        public void onActivityStarted(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}
    });}
}
