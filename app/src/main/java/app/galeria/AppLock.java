package app.galeria;

import android.app.KeyguardManager;
import android.content.Context;
import android.provider.Settings;

/** Trust belongs to one Android task and boot. A restored background task keeps its session. */
public final class AppLock {
    private static volatile boolean unlocked;
    static volatile boolean authenticating;
    private static Context application;private static int task=-1,boot=-1;
    private AppLock(){}
    public static boolean enabled(Context c){return new Prefs(c).bool("app_lock",false);}
    public static boolean canAccess(Context c){return !enabled(c)||unlocked;}
    public static boolean deviceSecure(Context c){KeyguardManager k=(KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE);return k!=null&&k.isDeviceSecure();}
    static void beginTask(Context c,int taskId,boolean restored){
        application=c.getApplicationContext();task=taskId;boot=Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);Prefs p=new Prefs(c);
        // Never restore trust for a fresh activity/task, another task ID or another device boot.
        unlocked=restored&&boot>=0&&p.integer("trusted_task",-2)==task&&p.integer("trusted_boot",-2)==boot;
        if(!unlocked){p.set("trusted_task",-1);p.set("trusted_boot",-1);}
    }
    static void unlock(){unlocked=true;if(application!=null){Prefs p=new Prefs(application);p.set("trusted_task",task);p.set("trusted_boot",boot);}}
    static void lock(){unlocked=false;if(application!=null){Prefs p=new Prefs(application);p.set("trusted_task",-1);p.set("trusted_boot",-1);}}
}
