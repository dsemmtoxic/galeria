package app.galeria;

import android.app.*;
import android.content.*;
import android.hardware.biometrics.*;
import android.os.*;
import java.util.concurrent.Executor;

/** Uses Android's credential UI: the app never sees a fingerprint, PIN or pattern. */
final class DeviceAuth {
    private static final int CREDENTIAL=670;
    private final Activity activity;private CancellationSignal signal;private Runnable success,cancel;private boolean credential,complete;
    DeviceAuth(Activity a){activity=a;}
    void start(Runnable yes,Runnable no){success=yes;cancel=no;complete=false;credential=false;AppLock.authenticating=true;
        if(!AppLock.deviceSecure(activity)){finish(false);return;}
        if(Build.VERSION.SDK_INT<28){credential();return;}
        try{
            Executor executor=r->activity.runOnUiThread(r);BiometricPrompt.Builder builder=new BiometricPrompt.Builder(activity).setTitle("Desbloquear Galeria").setSubtitle("Use a digital ou o bloqueio do celular");
            if(Build.VERSION.SDK_INT>=30)builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            else if(Build.VERSION.SDK_INT>=29)builder.setDeviceCredentialAllowed(true);
            else builder.setNegativeButton("Usar senha",executor,(d,w)->credential());
            signal=new CancellationSignal();builder.build().authenticate(signal,executor,new BiometricPrompt.AuthenticationCallback(){
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){finish(true);}
                @Override public void onAuthenticationError(int code,CharSequence message){
                    if(complete||credential)return;
                    if(code==BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED||code==BiometricPrompt.BIOMETRIC_ERROR_CANCELED){finish(false);return;}
                    credential();
                }
            });
        }catch(Exception e){credential();}
    }
    private void credential(){if(complete||credential)return;credential=true;
        try{KeyguardManager manager=(KeyguardManager)activity.getSystemService(Context.KEYGUARD_SERVICE);Intent intent=manager.createConfirmDeviceCredentialIntent("Desbloquear Galeria","Confirme o PIN, senha ou padrão do celular.");if(intent==null){finish(false);return;}activity.startActivityForResult(intent,CREDENTIAL);}
        catch(Exception e){finish(false);}
    }
    boolean result(int request,int result){if(request!=CREDENTIAL)return false;if(!complete)finish(result==Activity.RESULT_OK);return true;}
    private void finish(boolean ok){if(complete)return;complete=true;AppLock.authenticating=false;Runnable callback=ok?success:cancel;success=cancel=null;if(callback!=null&&!activity.isDestroyed()&&!activity.isFinishing())callback.run();}
    void close(){boolean pending=!complete;complete=true;success=cancel=null;if(signal!=null)signal.cancel();if(pending)AppLock.authenticating=false;}
}
