package app.galeria;

import android.content.*;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.util.concurrent.*;

/** Publish only complete edited copies. On failure, delete the partial destination. */
public final class MediaOutput {
    public interface Writer {void write(OutputStream output)throws Exception;}
    public static Uri save(Context c,Uri original,boolean video,String extension,String mime,Writer writer,VideoExporter.Cancel cancel)throws Exception{
        String stem="Mídia";long taken=0;
        try(Cursor cursor=c.getContentResolver().query(original,new String[]{"_display_name","datetaken"},null,null,null)){if(cursor!=null&&cursor.moveToFirst()){stem=MediaRepo.string(cursor,"_display_name");taken=MediaRepo.number(cursor,"datetaken");}}catch(Exception ignored){}
        int dot=stem.lastIndexOf('.');if(dot>0)stem=stem.substring(0,dot);if(stem.length()>90)stem=stem.substring(0,90);stem=stem.replaceAll("[/\\\\]","_");String name=stem+"_editada_"+System.currentTimeMillis()+extension;
        if(Build.VERSION.SDK_INT>=29){ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,mime);values.put(MediaStore.MediaColumns.RELATIVE_PATH,(video?"Movies/":"Pictures/")+"Galeria/Editadas/");values.put(MediaStore.MediaColumns.IS_PENDING,1);if(taken>0)values.put("datetaken",taken);
            Uri collection=video?MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY):MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);Uri result=c.getContentResolver().insert(collection,values);if(result==null)throw new IOException("Não foi possível criar a cópia editada.");boolean done=false;
            try{try(OutputStream output=c.getContentResolver().openOutputStream(result,"w")){if(output==null)throw new IOException("Não foi possível gravar a cópia.");writer.write(output);}cancel.check();ContentValues publish=new ContentValues();publish.put(MediaStore.MediaColumns.IS_PENDING,0);if(c.getContentResolver().update(result,publish,null,null)!=1)throw new IOException("Não foi possível publicar a cópia editada.");done=true;return result;}finally{if(!done)c.getContentResolver().delete(result,null,null);}
        }
        File parent=new File(Environment.getExternalStoragePublicDirectory(video?Environment.DIRECTORY_MOVIES:Environment.DIRECTORY_PICTURES),"Galeria/Editadas");if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Não foi possível criar a pasta de edição.");File output=new File(parent,name);boolean done=false;
        try{try(OutputStream stream=new FileOutputStream(output)){writer.write(stream);}cancel.check();CountDownLatch scanned=new CountDownLatch(1);Uri[] result=new Uri[1];MediaScannerConnection.scanFile(c,new String[]{output.getAbsolutePath()},new String[]{mime},(path,uri)->{result[0]=uri;scanned.countDown();});if(!scanned.await(20,TimeUnit.SECONDS)||result[0]==null)throw new IOException("O Android não conseguiu registrar a cópia editada.");done=true;return result[0];}finally{if(!done)output.delete();}
    }
    public static void copy(File file,OutputStream output,VideoExporter.Cancel cancel)throws Exception{try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[128*1024];int n;while((n=in.read(buffer))>=0){cancel.check();output.write(buffer,0,n);}}}
}
