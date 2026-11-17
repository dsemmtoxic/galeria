package app.galeria;

import android.content.Context;
import android.content.ContentUris;
import android.provider.MediaStore;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** All disk I/O and scanning run on MediaActions' worker, never on the UI thread. */
public final class FolderTransfers {
    public static final class Result {public int completed,failed;public String error;}
    private FolderTransfers(){}
    public static Result send(Context c,List<MediaItem> items,String folderName,boolean move)throws IOException{
        if(!FileAccess.direct(c))throw new IOException("Autorize o acesso aos arquivos nos Ajustes do Android.");
        File directory=FileAccess.folder(folderName);FileAccess.remember(c,directory);
        String path=Build.VERSION.SDK_INT>=29?FileAccess.ROOT+folderName+"/":directory.getAbsolutePath()+"/";
        return sendToDirectory(c,items,directory,path,"external_primary",move);
    }
    public static Result send(Context c,List<MediaItem> items,MediaItem.Album album,boolean move)throws IOException{
        if(!FileAccess.direct(c))throw new IOException("Autorize o acesso aos arquivos nos Ajustes do Android.");
        File directory=FileAccess.albumFolder(album);
        return sendToDirectory(c,items,directory,album.path,album.volume,move);
    }
    private static Result sendToDirectory(Context c,List<MediaItem> items,File directory,String relativePath,String volume,boolean move){
        Result result=new Result();Prefs prefs=new Prefs(c);
        for(MediaItem m:items){
            File destination=null,staging=null;boolean published=false,moved=false;
            try{
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Operação interrompida.");
                if(!MediaActions.validName(m.name))throw new IOException("Nome de arquivo inválido.");
                File original=null;try{original=FileAccess.source(c,m);}catch(IOException e){if(move)throw e;}
                if(move&&directory.equals(original.getParentFile())){result.completed++;continue;}
                destination=unique(directory,m.name);
                if(move&&original.renameTo(destination)){published=true;moved=true;}
                else{
                    staging=new File(directory,".galeria-"+UUID.randomUUID()+".part");
                    try(InputStream in=c.getContentResolver().openInputStream(m.uri);FileOutputStream out=new FileOutputStream(staging)){
                        stream(in,out,m.size);out.getFD().sync();
                    }
                    if(!staging.renameTo(destination))throw new IOException("Não foi possível concluir a cópia.");published=true;
                    if(original!=null&&original.lastModified()>0)destination.setLastModified(original.lastModified());
                    if(move){if(!original.delete())throw new IOException("A cópia foi salva, mas o original não pôde ser removido.");moved=true;}
                }
                String previous=m.key();Uri uri=scan(c,destination.getAbsolutePath(),m.mime);
                if(moved){
                    scan(c,original.getAbsolutePath(),m.mime);
                    if(uri!=null){Uri collection=Build.VERSION.SDK_INT>=29?(m.video?MediaStore.Video.Media.getContentUri("external_primary"):MediaStore.Images.Media.getContentUri("external_primary")):(m.video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI);m.uri=ContentUris.withAppendedId(collection,ContentUris.parseId(uri));prefs.migrateFavorite(previous,m.key());}
                    m.name=destination.getName();m.album=directory.getName();m.path=relativePath;m.volume=Build.VERSION.SDK_INT>=29?volume:"external";
                }
                result.completed++;
            }catch(Exception e){result.failed++;result.error=e.getMessage();if(published&&destination!=null)scan(c,destination.getAbsolutePath(),m.mime);}
            finally{if(staging!=null&&staging.exists())staging.delete();if(!published&&destination!=null&&destination.exists())destination.delete();}
        }
        return result;
    }
    static Uri scan(Context c,String path,String mime){
        CountDownLatch done=new CountDownLatch(1);Uri[] answer={null};
        MediaScannerConnection.scanFile(c,new String[]{path},new String[]{mime},(p,uri)->{answer[0]=uri;done.countDown();});
        try{done.await(15,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return answer[0];
    }
    private static File unique(File parent,String name){
        File file=new File(parent,name);int i=1,at=name.lastIndexOf('.');String base=at>0?name.substring(0,at):name,ext=at>0?name.substring(at):"";
        while(file.exists())file=new File(parent,base+" ("+(i++)+")"+ext);return file;
    }
    private static void stream(InputStream in,OutputStream out,long expected)throws IOException{
        if(in==null)throw new IOException("Arquivo indisponível.");byte[] buffer=new byte[128*1024];long bytes=0;int n;
        while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Operação interrompida.");out.write(buffer,0,n);bytes+=n;}
        out.flush();if(bytes==0||(expected>0&&bytes!=expected))throw new IOException("Cópia incompleta. O original foi mantido.");
    }
}
