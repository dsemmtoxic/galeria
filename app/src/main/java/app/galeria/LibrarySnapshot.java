package app.galeria;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Persistent media metadata index; thumbnails remain in ThumbLoader's independent disk cache.
 * A snapshot is only displayed when the current access permissions match those it was built with.
 * All reading and writing must run off the main thread.
 */
final class LibrarySnapshot {
    private static final int MAGIC=0x47414C49,VERSION=1,MAX_ITEMS=150000;
    private static final long MAX_BYTES=24L*1024*1024;
    private LibrarySnapshot(){}
    static final class Data {
        final List<MediaItem> items;final long generation,signature,created;
        Data(List<MediaItem> i,long g,long s,long t){items=i;generation=g;signature=s;created=t;}
    }
    private static AtomicFile storage(Context c){return new AtomicFile(new File(c.getFilesDir(),"media-index-v1.bin"));}
    static Data read(Context c,int permissions){
        File path=new File(c.getFilesDir(),"media-index-v1.bin");
        if(!path.isFile()||path.length()>MAX_BYTES)return null;
        try(InputStream source=storage(c).openRead(); DataInputStream in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(source)))){
            if(in.readInt()!=MAGIC||in.readInt()!=VERSION||in.readInt()!=permissions)return null;
            long created=in.readLong(),generation=in.readLong(),signature=in.readLong();int count=in.readInt();
            if(count<0||count>MAX_ITEMS)return null;
            ArrayList<MediaItem> result=new ArrayList<>(count);
            for(int i=0;i<count;i++){
                MediaItem m=new MediaItem();m.uri=Uri.parse(in.readUTF());m.name=in.readUTF();m.mime=in.readUTF();m.album=in.readUTF();
                m.path=in.readUTF();m.volume=in.readUTF();m.id=in.readLong();m.size=in.readLong();m.date=in.readLong();
                m.added=in.readLong();m.modified=in.readLong();m.duration=in.readLong();m.generation=in.readLong();
                m.width=in.readInt();m.height=in.readInt();m.video=in.readBoolean();MediaJson.index(m);result.add(m);
            }
            return new Data(result,generation,signature,created);
        }catch(Exception ignored){return null;}
    }
    static void write(Context c,List<MediaItem> items,int permissions,long generation,long signature){
        if(items.size()>MAX_ITEMS)return;
        AtomicFile atomic=storage(c);FileOutputStream out=null;
        try{
            out=atomic.startWrite();
            GZIPOutputStream compressed=new GZIPOutputStream(out);
            DataOutputStream stream=new DataOutputStream(new BufferedOutputStream(compressed));
            stream.writeInt(MAGIC);stream.writeInt(VERSION);stream.writeInt(permissions);
            stream.writeLong(System.currentTimeMillis());stream.writeLong(generation);stream.writeLong(signature);stream.writeInt(items.size());
            for(MediaItem m:items){
                stream.writeUTF(m.key());stream.writeUTF(m.name);stream.writeUTF(m.mime);stream.writeUTF(m.album);
                stream.writeUTF(m.path);stream.writeUTF(m.volume);stream.writeLong(m.id);stream.writeLong(m.size);
                stream.writeLong(m.date);stream.writeLong(m.added);stream.writeLong(m.modified);stream.writeLong(m.duration);
                stream.writeLong(m.generation);stream.writeInt(m.width);stream.writeInt(m.height);stream.writeBoolean(m.video);
            }
            stream.flush();compressed.finish();compressed.flush(); // Commit only after the GZIP footer is written.
            atomic.finishWrite(out);out=null;
            if(atomic.getBaseFile().length()>MAX_BYTES)atomic.delete();
        }catch(Exception e){if(out!=null)atomic.failWrite(out);}
    }
}
