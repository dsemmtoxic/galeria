package app.galeria;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Opaque input shield survives asynchronous content replacement while locked. */
public class SecureActivity extends Activity {
    private View shield;private DeviceAuth authentication;private boolean requesting;
    @Override protected void onCreate(Bundle b){super.onCreate(b);secureWindow();}
    private void secureWindow(){getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    @Override public void setContentView(View v){super.setContentView(v);shield=null;if(!AppLock.canAccess(this))protect();}
    private void protect(){if(shield!=null)return;FrameLayout content=findViewById(android.R.id.content);if(content==null)return;
        for(int i=0;i<content.getChildCount();i++)content.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LinearLayout cover=Ui.column(this);cover.setBackgroundColor(Ui.BG);cover.setGravity(Gravity.CENTER);cover.setClickable(true);cover.setFocusableInTouchMode(true);
        cover.addView(Ui.icon(this,"lock",Ui.ACCENT,48));TextView title=Ui.bold(this,"Galeria protegida",24,Ui.TEXT);title.setPadding(0,Ui.dp(this,20),0,Ui.dp(this,10));cover.addView(title);cover.addView(Ui.text(this,"Desbloqueie com a digital ou a credencial do celular.",12,Ui.MUTED));
        cover.addView(Ui.button(this,"Desbloquear",this::prompt),new LinearLayout.LayoutParams(-2,Ui.dp(this,50)));shield=cover;content.addView(cover,new FrameLayout.LayoutParams(-1,-1));cover.requestFocus();
    }
    private void reveal(){if(shield==null)return;ViewGroup parent=(ViewGroup)shield.getParent();if(parent!=null){parent.removeView(shield);for(int i=0;i<parent.getChildCount();i++)parent.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);}shield=null;}
    @Override protected void onResume(){super.onResume();secureWindow();if(!AppLock.canAccess(this)){protect();onLocked();prompt();}else reveal();}
    private void prompt(){if(requesting||AppLock.canAccess(this)||isFinishing())return;
        requesting=true;authentication=new DeviceAuth(this);authentication.start(()->{requesting=false;AppLock.unlock();reveal();onUnlocked();},()->{requesting=false;AppLock.lock();finishAffinity();});
    }
    protected void onLocked(){}protected void onUnlocked(){}
    protected void enableLock(Runnable done){if(requesting)return;requesting=true;authentication=new DeviceAuth(this);authentication.start(()->{requesting=false;new Prefs(this).set("app_lock",true);AppLock.unlock();secureWindow();done.run();},()->{requesting=false;done.run();});}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(authentication!=null)authentication.result(request,result);}
    @Override protected void onDestroy(){if(authentication!=null)authentication.close();super.onDestroy();}
}
