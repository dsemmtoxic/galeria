package app.galeria;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.net.Uri;
import android.os.Environment;
import org.json.*;
import java.io.*;
import java.util.*;

/** Durable journal is committed before moving a file. Hidden payloads stay outside normal albums. */
public final class TrashStore extends SQLiteOpenHelper {
    private static TrashStore instance;
    private final Context context;
    private TrashStore(Context c){super(c,"recycle-bin.db",null,1);context=c.getApplicationContext();setWriteAheadLoggingEnabled(true);}
    static synchronized TrashStore get(Context c){if(instance==null)instance=new TrashStore(c.getApplicationContext());return instance;}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE trash (token TEXT PRIMARY KEY, original TEXT NOT NULL, held TEXT NOT NULL, metadata TEXT NOT NULL, deleted INTEGER NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){}
    private static final class Entry{String token,original,held,json;long deleted;}
    private Entry entry(String token){try(Cursor c=getReadableDatabase().query("trash",null,"token=?",new String[]{token},null,null,null)){
        if(c==null||!c.moveToFirst())return null;return read(c);
    }}
    private Entry read(Cursor c){Entry e=new Entry();e.token=c.getString(c.getColumnIndexOrThrow("token"));e.original=c.getString(c.getColumnIndexOrThrow("original"));e.held=c.getString(c.getColumnIndexOrThrow("held"));e.json=c.getString(c.getColumnIndexOrThrow("metadata"));e.deleted=c.getLong(c.getColumnIndexOrThrow("deleted"));return e;}
    static Uri uri(Context c,String token){return new Uri.Builder().scheme("content").authority(c.getPackageName()+".local").appendPath("trash").appendPath(token).build();}
    private String token(Uri u){if(!(context.getPackageName()+".local").equals(u.getAuthority())||u.getPathSegments().size()!=2||!"trash".equals(u.getPathSegments().get(0)))return null;return u.getLastPathSegment();}
    boolean owns(Uri u){return token(u)!=null;}
    File file(Uri u)throws FileNotFoundException{Entry e=entry(token(u));if(e==null||!new File(e.held).isFile())throw new FileNotFoundException("Arquivo indisponível.");return new File(e.held);}
    MediaItem metadata(Uri u){Entry e=entry(token(u));if(e==null)return null;try{return item(e);}catch(Exception ignored){return null;}}
    private MediaItem item(Entry e)throws JSONException{MediaItem m=MediaJson.decode(new JSONObject(e.json));m.uri=uri(context,e.token);m.trashed=true;return m;}
    synchronized List<MediaItem> items(){
        List<MediaItem> result=new ArrayList<>();try(Cursor c=getReadableDatabase().query("trash",null,null,null,null,null,"deleted DESC")){
            while(c.moveToNext()){Entry e=read(c);if(!new File(e.held).isFile())continue;try{result.add(item(e));}catch(JSONException ignored){}}
        }return result;
    }
    synchronized void put(MediaItem m)throws Exception{
        DirectMedia.requireAccess(context);File original=FileAccess.source(context,m);
        File root=new File(Environment.getExternalStorageDirectory(),"Pictures/Gallery/.trash");
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Não foi possível acessar a lixeira.");new File(root,".nomedia").createNewFile();
        String id=UUID.randomUUID().toString();File folder=new File(root,id),held=new File(folder,original.getName());if(!folder.mkdir())throw new IOException("Não foi possível preparar a lixeira.");
        ContentValues values=new ContentValues();values.put("token",id);values.put("original",original.getAbsolutePath());values.put("held",held.getAbsolutePath());values.put("metadata",MediaJson.encode(m).toString());values.put("deleted",System.currentTimeMillis());
        try{getWritableDatabase().insertOrThrow("trash",null,values);DirectMedia.move(original,held);}
        catch(Exception e){if(!held.isFile()){getWritableDatabase().delete("trash","token=?",new String[]{id});folder.delete();}throw e;}
        new Prefs(context).migrateFavorite(m.key(),uri(context,id).toString());FolderTransfers.scan(context,original.getAbsolutePath(),m.mime);
    }
    synchronized void remove(MediaItem m)throws Exception{
        DirectMedia.requireAccess(context);Entry e=entry(token(m.uri));if(e==null){DirectMedia.delete(context,m);return;}
        File held=new File(e.held);if(held.exists()&&!held.delete())throw new IOException("Não foi possível excluir o arquivo.");
        getWritableDatabase().delete("trash","token=?",new String[]{e.token});held.getParentFile().delete();new Prefs(context).removeFavorite(m.key());
    }
    synchronized Uri restore(MediaItem m)throws Exception{
        DirectMedia.requireAccess(context);Entry e=entry(token(m.uri));File source,target;
        if(e!=null){source=new File(e.held);File original=new File(e.original);target=DirectMedia.unique(original.getParentFile(),original.getName());}
        else{source=FileAccess.source(context,m);String name=source.getName().replaceFirst("^\\.trashed-[0-9]+-","");if(name.equals(source.getName()))name=m.name.replaceFirst("^\\.trashed-[0-9]+-","");if(!MediaActions.validName(name)||name.startsWith("."))throw new IOException("Nome original indisponível.");target=DirectMedia.unique(source.getParentFile(),name);}
        DirectMedia.move(source,target);if(e==null)FolderTransfers.scan(context,source.getAbsolutePath(),m.mime);
        Uri restored=DirectMedia.indexed(context,target,m);if(restored!=null)new Prefs(context).migrateFavorite(m.key(),restored.toString());
        if(e!=null){getWritableDatabase().delete("trash","token=?",new String[]{e.token});source.getParentFile().delete();}return restored;
    }
}
