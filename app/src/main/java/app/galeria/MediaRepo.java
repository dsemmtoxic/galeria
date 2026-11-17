package app.galeria;

import android.content.*;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MediaRepo {
    public interface Listener { void onMedia(List<MediaItem> items,String error); }
    private final Context context;private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService disk=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false),again=new AtomicBoolean(false);
    private boolean active,registered,dirty=true,hydrated;private boolean trash;
    private long lastCheck,generation=Long.MIN_VALUE;
    private int permissionState=-1;
    private volatile int queryCount;
    private long signature=Long.MIN_VALUE;
    private final Runnable debounce=()->{if(active)refresh(true);};
    private final Runnable poll=new Runnable(){public void run(){if(active){refresh(false);main.postDelayed(this,60000);}}};
    private final ContentObserver observer=new ContentObserver(main){
        @Override public void onChange(boolean selfChange,Uri uri){dirty=true;main.removeCallbacks(debounce);if(active)main.postDelayed(debounce,1500);}
    };
    public MediaRepo(Context c,Listener l){context=c.getApplicationContext();listener=l;}
    public void start() {
        if(active)return;active=true;
        int permissions=permissions();if(permissions!=permissionState){permissionState=permissions;dirty=true;}
        if(!registered)try {
            context.getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,true,observer);
            context.getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,true,observer);
            registered=true;
        }catch(Exception ignored){}
        if(dirty||!hydrated)refresh(false);else if(SystemClock.elapsedRealtime()-lastCheck>30000)refresh(false);
        main.postDelayed(poll,60000);
    }
    public void stop() {
        active=false;main.removeCallbacks(poll);main.removeCallbacks(debounce);
    }
    public void close(){stop();if(registered)try{context.getContentResolver().unregisterContentObserver(observer);}catch(Exception ignored){}registered=false;worker.shutdownNow();disk.shutdown();}
    private int permissions(){int hash=FileAccess.direct(context)?1:0;for(String p:new String[]{"android.permission.READ_EXTERNAL_STORAGE","android.permission.READ_MEDIA_IMAGES","android.permission.READ_MEDIA_VIDEO","android.permission.READ_MEDIA_VISUAL_USER_SELECTED"})hash=hash*31+context.checkSelfPermission(p);return hash;}
    public void trash(boolean t){if(trash==t)return;trash=t;signature=Long.MIN_VALUE;dirty=true;if(active)refresh(true);}
    private long mediaGeneration(){
        if(Build.VERSION.SDK_INT<30)return Long.MIN_VALUE;
        try{long hash=1;for(String volume:new TreeSet<>(MediaStore.getExternalVolumeNames(context)))hash=31*hash+MediaStore.getGeneration(context,volume);return hash;}catch(Exception e){return Long.MIN_VALUE;}
    }
    public void refresh(boolean force){
        if(worker.isShutdown())return;
        if(force)dirty=true;
        if(!busy.compareAndSet(false,true)){again.set(true);return;}
        final boolean queryTrash=trash;
        final boolean requested=dirty;
        final boolean first=!hydrated;
        final int permissionFingerprint=permissions();
        hydrated=true;dirty=false;
        worker.execute(()->{
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            long gen=mediaGeneration();
            LibrarySnapshot.Data snapshot=null;
            if(first&&!queryTrash){
                // Use an on-disk index immediately after a cold start, but never across permission changes.
                snapshot=LibrarySnapshot.read(context,permissionFingerprint);
                if(snapshot!=null){
                    LibrarySnapshot.Data cached=snapshot;
                    main.post(()->{
                        if(active&&!trash&&permissions()==permissionFingerprint&&signature==Long.MIN_VALUE){
                            signature=cached.signature;generation=cached.generation;
                            GalleryCache.remember(cached.items);listener.onMedia(cached.items,null);
                        }
                    });
                }
            }
            boolean unchanged=gen!=Long.MIN_VALUE && ((snapshot!=null&&snapshot.generation==gen)||(!requested&&gen==generation));
            // A real user-initiated refresh always re-queries; normal launches with a valid
            // MediaStore generation need not scan the entire library again.
            if(!force&&unchanged){main.post(()->{lastCheck=SystemClock.elapsedRealtime();busy.set(false);if(!active){hydrated=false;dirty=true;}if(again.getAndSet(false)&&active)refresh(false);});return;}
            List<MediaItem> result=new ArrayList<>();String error=null;
            try{queryCount++;result=query(context,queryTrash);}catch(Exception e){error="Não foi possível ler a biblioteca. Verifique o acesso às fotos e aos vídeos.";}
            long hash=1125899906842597L;
            for(MediaItem m:result)hash=31*hash+m.key().hashCode()+m.modified+m.size+m.name.hashCode()+m.path.hashCode()+m.date+m.generation;
            final long sig=hash;final List<MediaItem> items=result;final String message=error;
            main.post(()->{
                lastCheck=SystemClock.elapsedRealtime();generation=gen;busy.set(false);
                // Persist even when the activity moves to the background before the scan finishes.
                if(!queryTrash&&message==null&&queryTrash==trash&&permissions()==permissionFingerprint&&!disk.isShutdown()){
                    try{disk.execute(()->LibrarySnapshot.write(context,items,permissionFingerprint,gen,sig));}catch(RejectedExecutionException ignored){}
                }
                if(!active){hydrated=false;dirty=true;return;}
                if(queryTrash==trash && (signature!=sig || message!=null)){signature=sig;if(!queryTrash&&message==null)GalleryCache.remember(items);listener.onMedia(items,message);}
                if(again.getAndSet(false) && active)refresh(false);
            });
        });
    }
    public static List<MediaItem> query(Context c,boolean trash) {
        List<MediaItem> items=new ArrayList<>();
        RuntimeException failure=null;int queried=0;
        for(boolean video:new boolean[]{false,true}) {
            try {queryType(c,video,trash,items);queried++;}catch(SecurityException e){failure=e;}catch(RuntimeException e){failure=e;}
        }
        if(queried==0 && failure!=null)throw failure;
        if(trash)items.addAll(TrashStore.get(c).items());
        items.sort((a,b)->Long.compare(b.added,a.added));return items;
    }
    private static void queryType(Context c,boolean video,boolean trash,List<MediaItem> items) {
        Uri collection=video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        List<String> cols=new ArrayList<>(Arrays.asList("_id","_display_name","mime_type","_size","date_added","date_modified","datetaken","width","height","bucket_display_name"));
        if(video)cols.add("duration");
        if(Build.VERSION.SDK_INT>=30)cols.add("generation_modified");
        if(Build.VERSION.SDK_INT>=29){cols.add("relative_path");cols.add("volume_name");}else cols.add("_data");
        Cursor cursor;
        if(Build.VERSION.SDK_INT>=30) {
            Bundle args=new Bundle();
            args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED,trash?MediaStore.MATCH_ONLY:MediaStore.MATCH_EXCLUDE);
            args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,"_size > 0 AND is_pending = 0");
            args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,"date_added DESC");
            cursor=c.getContentResolver().query(collection,cols.toArray(new String[0]),args,null);
        }else {
            if(trash)return;
            cursor=c.getContentResolver().query(collection,cols.toArray(new String[0]),"_size > 0"+(Build.VERSION.SDK_INT>=29?" AND is_pending = 0":""),null,"date_added DESC");
        }
        if(cursor==null)return;
        try {
            while(cursor.moveToNext()){
                MediaItem m=new MediaItem();m.video=video;m.trashed=trash;
                m.id=number(cursor,"_id");m.name=string(cursor,"_display_name");
                m.mime=string(cursor,"mime_type");if(m.mime.isEmpty())m.mime=video?"video/mp4":"image/jpeg";
                m.generation=number(cursor,"generation_modified");m.size=number(cursor,"_size");m.added=number(cursor,"date_added")*1000L;m.modified=number(cursor,"date_modified");
                m.date=number(cursor,"datetaken");if(m.date<=0)m.date=m.added;
                m.width=(int)number(cursor,"width");m.height=(int)number(cursor,"height");m.album=string(cursor,"bucket_display_name");
                if(video)m.duration=number(cursor,"duration");
                if(Build.VERSION.SDK_INT>=29){m.path=string(cursor,"relative_path");m.volume=string(cursor,"volume_name");if(m.volume.isEmpty())m.volume="external_primary";}
                else{String absolute=string(cursor,"_data");File f=new File(absolute);m.path=f.getParent()==null?"":f.getParent()+"/";m.volume="external";}
                if(m.album.isEmpty()) {String p=m.path.replaceAll("/+$","");int s=p.lastIndexOf('/');m.album=s>=0?p.substring(s+1):p;if(m.album.isEmpty())m.album="Outros";}
                Uri specific=collection;
                if(Build.VERSION.SDK_INT>=29)specific=video?MediaStore.Video.Media.getContentUri(m.volume):MediaStore.Images.Media.getContentUri(m.volume);
                m.uri=ContentUris.withAppendedId(specific,m.id);m.sortName=Ui.normalize(m.name);
                m.searchText=Ui.normalize(m.name+" "+m.album+" "+m.mime+" "+Ui.date(m.date,"dd/MM/yyyy"));items.add(m);
            }
        }finally{cursor.close();}
    }
    static long number(Cursor c,String col){int i=c.getColumnIndex(col);return i>=0&&!c.isNull(i)?c.getLong(i):0;}
    static String string(Cursor c,String col){int i=c.getColumnIndex(col);return i>=0&&!c.isNull(i)?c.getString(i):"";}
    public static List<MediaItem.Album> albums(List<MediaItem> items,Prefs prefs) {
        Map<String,MediaItem.Album> map=new LinkedHashMap<>();
        for(MediaItem m:items){
            MediaItem.Album a=map.get(m.albumKey());
            if(a==null){a=new MediaItem.Album(m.albumKey(),m.album,m.path,m.volume);map.put(a.key,a);}
            a.items.add(m);a.size+=m.size;
        }
        for(Prefs.Folder f:prefs.folders()) {
            MediaItem.Album a=map.get(f.key());
            if(a==null){a=new MediaItem.Album(f.key(),f.name,f.path,f.volume);map.put(a.key,a);}
            a.folder=f;
        }
        List<MediaItem.Album> result=new ArrayList<>(map.values());
        result.sort((a,b)->Ui.normalize(a.name).compareTo(Ui.normalize(b.name)));return result;
    }
}
