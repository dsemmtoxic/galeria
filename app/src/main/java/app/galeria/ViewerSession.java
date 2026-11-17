package app.galeria;

import android.content.*;
import android.os.Bundle;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Exact navigation membership is saved off-thread; a small scope also survives interrupted writes. */
public final class ViewerSession {
    private static final ExecutorService disk=Executors.newSingleThreadExecutor();
    private static final AtomicReference<Runnable> pending=new AtomicReference<>();
    private static boolean writing;
    private static synchronized void enqueue(Runnable work){pending.set(work);if(writing)return;writing=true;disk.execute(()->{for(;;){Runnable next=pending.getAndSet(null);if(next!=null)next.run();synchronized(ViewerSession.class){if(pending.get()==null){writing=false;return;}}}});}
    static String start(Context c,List<MediaItem> items){String id=UUID.randomUUID().toString();save(c,id,items);return id;}
    static void save(Context c,String id,List<MediaItem> items){
        if(!valid(id))return;List<MediaItem> snapshot=new ArrayList<>(items);MediaItem.Session.id=id;MediaItem.Session.items=snapshot;
        Context app=c.getApplicationContext();enqueue(()->{
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            File dir=new File(app.getFilesDir(),"viewer-sessions");dir.mkdirs();AtomicFile atomic=new AtomicFile(new File(dir,id+".json"));FileOutputStream out=null;
            try{JSONArray a=new JSONArray();for(MediaItem m:snapshot)a.put(MediaJson.encode(m));out=atomic.startWrite();out.write(a.toString().getBytes("UTF-8"));atomic.finishWrite(out);out=null;
                File[] files=dir.listFiles((d,n)->n.endsWith(".json"));if(files!=null){Arrays.sort(files,(x,y)->Long.compare(y.lastModified(),x.lastModified()));for(int i=4;i<files.length;i++)files[i].delete();}
            }catch(Exception ignored){if(out!=null)atomic.failWrite(out);}
        });
    }
    private static boolean valid(String id){return id!=null&&id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");}
    static List<MediaItem> recover(Context c,String id,Bundle scope){
        if(valid(id))try{
            byte[] bytes=new AtomicFile(new File(c.getFilesDir(),"viewer-sessions/"+id+".json")).readFully();JSONArray a=new JSONArray(new String(bytes,"UTF-8"));List<MediaItem> result=new ArrayList<>();
            for(int i=0;i<a.length();i++)result.add(MediaJson.decode(a.getJSONObject(i)));if(!result.isEmpty())return result;
        }catch(Exception ignored){}
        if(scope==null)return Collections.emptyList();
        try{
            List<MediaItem> all=MediaRepo.query(c,scope.getBoolean("trash"));Prefs prefs=new Prefs(c);Set<String> hidden=prefs.hidden(),favorites=prefs.favorites();List<MediaItem> result=new ArrayList<>();
            String album=scope.getString("album"),q=Ui.normalize(scope.getString("query","")).trim(),pkg=scope.getString("screenshot_package");int filter=scope.getInt("filter"),tab=scope.getInt("tab");
            for(MediaItem m:all){
                if(!scope.getBoolean("trash")&&hidden.contains(m.albumKey()))continue;
                if(album!=null&&!album.equals(m.albumKey()))continue;if(tab==2&&!favorites.contains(m.key()))continue;
                if(filter==1&&m.video||filter==2&&!m.video)continue;
                if(pkg!=null&&!pkg.equals(SearchIndex.screenshotPackage(m)))continue;
                if(!q.isEmpty()&&!(scope.getBoolean("file_names")?Ui.normalize(m.name):m.searchText).contains(q))continue;
                result.add(m);
            }
            sort(result,scope.getInt("sort"));return result;
        }catch(RuntimeException e){return Collections.emptyList();}
    }
    static void sort(List<MediaItem> items,int ordering){items.sort(ordering==1?(a,b)->Long.compare(a.added,b.added):ordering==2?(a,b)->Long.compare(b.date,a.date):ordering==3?(a,b)->a.sortName.compareTo(b.sortName):ordering==4?(a,b)->Long.compare(b.size,a.size):(a,b)->Long.compare(b.added,a.added));}
}
