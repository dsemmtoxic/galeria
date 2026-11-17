package app.galeria;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.*;
import java.util.UUID;

/** File mutations use the one-time storage grant, never a per-item MediaStore request. */
final class DirectMedia {
    static void requireAccess(Context c)throws IOException{if(!FileAccess.direct(c))throw new IOException("Autorize o acesso aos arquivos nos Ajustes do Android.");}
    static File unique(File parent,String name){
        File f=new File(parent,name);int at=name.lastIndexOf('.'),i=1;String base=at>0?name.substring(0,at):name,ext=at>0?name.substring(at):"";
        while(f.exists())f=new File(parent,base+" ("+(i++)+")"+ext);return f;
    }
    static void move(File source,File destination)throws IOException{
        if(source.equals(destination))return;
        if(destination.exists())throw new IOException("Já existe um arquivo com este nome.");
        File parent=destination.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Não foi possível acessar a pasta de destino.");
        if(source.renameTo(destination))return;
        File stage=new File(parent,".galeria-"+UUID.randomUUID()+".part");boolean published=false;
        try{
            long bytes=0;byte[] buffer=new byte[128*1024];
            try(InputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(stage)){
                int n;while((n=in.read(buffer))>=0){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Operação interrompida.");out.write(buffer,0,n);bytes+=n;}out.flush();out.getFD().sync();
            }
            if(bytes!=source.length()||bytes==0)throw new IOException("Cópia incompleta. O original foi mantido.");
            stage.setLastModified(source.lastModified());
            if(!stage.renameTo(destination))throw new IOException("Não foi possível concluir a operação.");published=true;
            if(!source.delete()){if(destination.delete())published=false;throw new IOException("O original não pôde ser removido.");}
        }finally{stage.delete();}
    }
    static Uri indexed(Context c,File destination,MediaItem m){
        Uri scanned=FolderTransfers.scan(c,destination.getAbsolutePath(),m.mime);if(scanned==null)return null;
        try{Uri collection=Build.VERSION.SDK_INT>=29?(m.video?MediaStore.Video.Media.getContentUri(m.volume):MediaStore.Images.Media.getContentUri(m.volume)):(m.video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI);return ContentUris.withAppendedId(collection,ContentUris.parseId(scanned));}catch(RuntimeException e){return scanned;}
    }
    static void rename(Context c,MediaItem m,String name)throws IOException{
        requireAccess(c);if(!MediaActions.validName(name))throw new IOException("Nome inválido.");
        File source=FileAccess.source(c,m),target=new File(source.getParentFile(),name).getCanonicalFile();
        if(!target.getParentFile().equals(source.getParentFile()))throw new IOException("Nome inválido.");
        if(source.equals(target))return;String old=m.key();move(source,target);
        FolderTransfers.scan(c,source.getAbsolutePath(),m.mime);Uri uri=indexed(c,target,m);
        if(uri!=null){m.uri=uri;new Prefs(c).migrateFavorite(old,m.key());}m.name=name;MediaJson.index(m);
    }
    static void delete(Context c,MediaItem m)throws IOException{
        requireAccess(c);File source=FileAccess.source(c,m);if(!source.delete())throw new IOException("Não foi possível excluir o arquivo.");
        FolderTransfers.scan(c,source.getAbsolutePath(),m.mime);new Prefs(c).removeFavorite(m.key());
    }
}
