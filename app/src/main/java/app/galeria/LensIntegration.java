package app.galeria;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import java.util.*;

/** Sends the selected original to an exported Lens receiver; no private activity names or uploads. */
public final class LensIntegration {
    static Intent imageIntent(Context c,MediaItem m){
        Intent intent=new Intent(Intent.ACTION_SEND).setType(m.mime.startsWith("image/")?m.mime:"image/*")
            .putExtra(Intent.EXTRA_STREAM,m.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(c.getContentResolver(),m.name,m.uri));return intent;
    }
    static Intent resolve(Context c,MediaItem m){
        Intent send=imageIntent(c,m);PackageManager pm=c.getPackageManager();
        for(String pkg:new String[]{"com.google.ar.lens","com.google.android.googlequicksearchbox"}){
            Intent candidate=new Intent(send).setPackage(pkg);
            for(ResolveInfo r:pm.queryIntentActivities(candidate,PackageManager.MATCH_DEFAULT_ONLY)){
                if(r.activityInfo==null||!r.activityInfo.exported)continue;
                String label=String.valueOf(r.loadLabel(pm)).toLowerCase(Locale.ROOT),component=r.activityInfo.name.toLowerCase(Locale.ROOT);
                if(pkg.equals("com.google.ar.lens")||label.contains("lens")||component.contains("lens"))return candidate.setComponent(new ComponentName(pkg,r.activityInfo.name));
            }
        }return null;
    }
    public static void open(Activity a,MediaItem m){
        if(m.video)return;Intent intent=resolve(a,m);
        if(intent!=null)try{a.startActivity(intent);return;}catch(ActivityNotFoundException|SecurityException ignored){}
        new AlertDialog.Builder(a).setTitle("Google Lens")
            .setMessage("Instale ou atualize o Google Lens e o app Google para pesquisar usando esta imagem.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Abrir Google Play",(d,w)->{
                try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=com.google.ar.lens")));}
                catch(ActivityNotFoundException e){try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=com.google.ar.lens")));}catch(ActivityNotFoundException ignored){Ui.toast(a,"Nenhum aplicativo disponível para abrir a loja.");}}
            }).show();
    }
}
