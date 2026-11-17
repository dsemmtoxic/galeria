package app.galeria;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import java.io.*;

/** One Android grant enables direct organization inside the fixed public destination. */
public final class FileAccess {
    public static final String ROOT="Pictures/Gallery/owner/";
    private FileAccess(){}
    public static boolean direct(Context c){
        return Build.VERSION.SDK_INT>=30?Environment.isExternalStorageManager():
            c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }
    public static void request(Activity a,int request){
        if(Build.VERSION.SDK_INT<30){a.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},request);return;}
        try{a.startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+a.getPackageName())),request);}
        catch(ActivityNotFoundException e){a.startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),request);}
    }
    public static File folder(String name)throws IOException{
        if(!MediaActions.validName(name)||name.length()>80||name.startsWith("."))throw new IOException("Digite um nome de pasta válido, com até 80 caracteres.");
        File root=new File(Environment.getExternalStorageDirectory(),ROOT).getCanonicalFile();
        File dir=new File(root,name).getCanonicalFile();
        if(!root.equals(dir.getParentFile()))throw new IOException("Nome de pasta inválido.");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Não foi possível criar a pasta. Verifique o acesso e o espaço disponível.");
        return dir;
    }
    /** Resolve a public MediaStore album without allowing escapes or private Android paths. */
    public static File albumFolder(MediaItem.Album album)throws IOException{
        if(album==null||album.path==null||album.path.isEmpty())throw new IOException("Álbum sem pasta de destino.");
        if(!"external_primary".equals(album.volume)&&!"external".equals(album.volume))
            throw new IOException("Este armazenamento não permite movimentação direta. Escolha um álbum do armazenamento interno.");
        String relative=album.path.replace('\\','/');
        if(relative.startsWith("/") && Build.VERSION.SDK_INT>=29)throw new IOException("Caminho de álbum inválido.");
        File root=Environment.getExternalStorageDirectory().getCanonicalFile();
        File folder=(Build.VERSION.SDK_INT>=29?new File(root,relative):new File(relative)).getCanonicalFile();
        if(!folder.getPath().startsWith(root.getPath()+File.separator))throw new IOException("Álbum fora do armazenamento permitido.");
        String local=folder.getPath().substring(root.getPath().length()+1).replace('\\','/');
        if(local.equals("Android")||local.startsWith("Android/"))throw new IOException("Escolha um álbum público de fotos ou vídeos.");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Não foi possível acessar a pasta do álbum.");
        return folder;
    }
    public static boolean supportedAlbum(MediaItem.Album album){
        if(album==null||album.path==null||album.path.isEmpty())return false;
        if(!"external_primary".equals(album.volume)&&!"external".equals(album.volume))return false;
        String path=album.path.replace('\\','/');
        return !path.equals("Android")&&!path.startsWith("Android/")&&!path.contains("../")&&!path.startsWith("../");
    }
    public static File source(Context c,MediaItem item)throws IOException{
        String path=null;
        if("file".equals(item.uri.getScheme()))path=item.uri.getPath();
        else try(Cursor cursor=c.getContentResolver().query(item.uri,new String[]{"_data"},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())path=cursor.getString(0);
        }catch(RuntimeException ignored){}
        if(path==null||path.isEmpty())throw new IOException("O arquivo original não está disponível no armazenamento local.");
        File file=new File(path).getCanonicalFile();if(!file.isFile())throw new IOException("O arquivo original não foi encontrado.");return file;
    }
    public static void remember(Context c,File folder){
        String volume=Build.VERSION.SDK_INT>=29?"external_primary":"external";
        String path=Build.VERSION.SDK_INT>=29?ROOT+folder.getName()+"/":folder.getAbsolutePath()+"/";
        new Prefs(c).addFolder(new Prefs.Folder(folder.getName(),Uri.fromFile(folder).toString(),path,volume));
    }
}
