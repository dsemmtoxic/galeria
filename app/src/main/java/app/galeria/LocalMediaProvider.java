package app.galeria;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Read-only, narrowly scoped URI access for recycle-bin previews. No arbitrary paths are accepted. */
public final class LocalMediaProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){MediaItem m=TrashStore.get(getContext()).metadata(uri);return m==null?null:m.mime;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Somente leitura.");return ParcelFileDescriptor.open(TrashStore.get(getContext()).file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        MediaItem m=TrashStore.get(getContext()).metadata(uri);if(m==null)return null;
        String[] cols=projection==null?new String[]{"_display_name","_size","mime_type","width","height","datetaken","date_added","duration","relative_path","bucket_display_name"}:projection;
        MatrixCursor cursor=new MatrixCursor(cols);MatrixCursor.RowBuilder row=cursor.newRow();
        for(String col:cols)switch(col){
            case "_display_name":row.add(m.name);break;case "_size":row.add(m.size);break;case "mime_type":row.add(m.mime);break;case "width":row.add(m.width);break;case "height":row.add(m.height);break;
            case "datetaken":row.add(m.date);break;case "date_added":row.add(m.added/1000);break;case "duration":row.add(m.duration);break;case "relative_path":row.add(m.path);break;case "bucket_display_name":row.add(m.album);break;
            case "_data":try{row.add(TrashStore.get(getContext()).file(uri).getAbsolutePath());}catch(IOException e){row.add(null);}break;default:row.add(null);
        }return cursor;
    }
    @Override public Uri insert(Uri uri,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
}
