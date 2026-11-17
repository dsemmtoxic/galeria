package app.galeria;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.os.*;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;

public final class ThumbLoader {
    private static ThumbLoader instance;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>(96));
    private final LruCache<String,Bitmap> cache;
    private final java.io.File disk;
    private final java.util.concurrent.atomic.AtomicInteger writes=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.Map<String,Job> jobs=new java.util.HashMap<>();
    private static final class Ticket {final String key;final WeakReference<ImageView> view;volatile boolean cancelled;Job job;Ticket(String k,ImageView v){key=k;view=new WeakReference<>(v);}}
    private static final class Job {final String key;final MediaItem item;final int size;final java.util.List<Ticket> tickets=new java.util.concurrent.CopyOnWriteArrayList<>();final CancellationSignal signal=new CancellationSignal();Job(String k,MediaItem m,int s){key=k;item=m;size=s;}boolean wanted(){for(Ticket t:tickets)if(!t.cancelled&&t.view.get()!=null)return true;return false;}}
    private ThumbLoader(Context c) {
        context=c.getApplicationContext();disk=new java.io.File(context.getCacheDir(),"thumbnails-v2");disk.mkdirs();
        int memory=(int)Math.min(28L*1024*1024,Runtime.getRuntime().maxMemory()/10);
        cache=new LruCache<String,Bitmap>(memory){@Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
        worker.allowCoreThreadTimeOut(true);worker.execute(this::trimDisk);
    }
    public static synchronized ThumbLoader get(Context c){if(instance==null)instance=new ThumbLoader(c);return instance;}
    public Bitmap preview(MediaItem m){for(int size:new int[]{384,256,192,128}){Bitmap b=cache.get(key(m,size));if(b!=null)return b;}return null;}
    private String key(MediaItem m,int s){return m.key()+":"+m.modified+":"+m.size+":"+m.generation+":"+s;}
    public void clear(ImageView view){Object old=view.getTag();if(old instanceof Ticket){Ticket t=(Ticket)old;t.cancelled=true;if(t.job!=null&&!t.job.wanted())t.job.signal.cancel();}view.setTag(null);view.setImageDrawable(null);}
    public void bind(ImageView view,MediaItem m,int requested) {
        int size=requested<=128?128:requested<=192?192:requested<=256?256:384;String key=key(m,size);
        if(view.getTag() instanceof Ticket&&key.equals(((Ticket)view.getTag()).key))return;
        clear(view);Ticket ticket=new Ticket(key,view);view.setTag(ticket);Bitmap cached=cache.get(key);
        if(cached!=null){view.setImageBitmap(cached);return;}
        view.setImageDrawable(new ThumbnailSkeleton());
        Job existing=jobs.get(key);if(existing!=null&&!existing.signal.isCanceled()){ticket.job=existing;existing.tickets.add(ticket);return;}
        Job job=new Job(key,m,size);ticket.job=job;job.tickets.add(ticket);jobs.put(key,job);
        try{worker.execute(()->load(job));}catch(RejectedExecutionException e){
            jobs.remove(key);ticket.job=null;
            // If the visible grid temporarily floods the queue, retry only while
            // this cell still wants the same media. Avoid a permanently blank skeleton.
            main.postDelayed(()->{
                ImageView target=ticket.view.get();
                if(target!=null&&!ticket.cancelled&&target.getTag()==ticket){
                    target.setTag(null);bind(target,m,requested);
                }
            },450);
        }
    }
    private java.io.File file(String key){try{byte[] d=java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(byte v:d)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return new java.io.File(disk,b+".jpg");}catch(Exception e){return new java.io.File(disk,Integer.toHexString(key.hashCode())+".jpg");}}
    private void load(Job job){
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);Bitmap bitmap=null;
        try{
            if(job.wanted()&&!job.signal.isCanceled()){
                java.io.File f=file(job.key);if(f.isFile())bitmap=BitmapFactory.decodeFile(f.getAbsolutePath());
                if(bitmap==null&&job.wanted()&&!job.signal.isCanceled()){
                    if(Build.VERSION.SDK_INT>=29&&!job.item.trashed)bitmap=context.getContentResolver().loadThumbnail(job.item.uri,new Size(job.size,job.size),job.signal);
                    else if(job.item.video){MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(context,job.item.uri);bitmap=Build.VERSION.SDK_INT>=27?r.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,job.size,job.size):r.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);}finally{r.release();}}
                    else bitmap=decodePhoto(context,job.item,job.size);
                    if(bitmap!=null&&Math.max(bitmap.getWidth(),bitmap.getHeight())>job.size){float scale=job.size/(float)Math.max(bitmap.getWidth(),bitmap.getHeight());Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*scale)),Math.max(1,Math.round(bitmap.getHeight()*scale)),true);if(small!=bitmap)bitmap.recycle();bitmap=small;}
                    if(bitmap!=null&&!job.signal.isCanceled()){java.io.File tmp=new java.io.File(f+".tmp");try(java.io.FileOutputStream out=new java.io.FileOutputStream(tmp)){bitmap.compress(Bitmap.CompressFormat.JPEG,82,out);}tmp.renameTo(f);if(writes.incrementAndGet()%64==0)try{worker.execute(this::trimDisk);}catch(RejectedExecutionException ignored){}}
                }else if(bitmap!=null)f.setLastModified(System.currentTimeMillis());
                if(bitmap!=null&&!job.signal.isCanceled())cache.put(job.key,bitmap);
            }
        }catch(Exception|OutOfMemoryError ignored){}
        final Bitmap result=bitmap;
        main.post(()->{if(jobs.get(job.key)==job)jobs.remove(job.key);for(Ticket t:job.tickets){t.job=null;ImageView v=t.view.get();if(t.cancelled||v==null||v.getTag()!=t)continue;if(result!=null)v.setImageBitmap(result);else v.setTag(null);}});
    }
    private void trimDisk(){java.io.File[] files=disk.listFiles();if(files==null)return;java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));long bytes=0;for(java.io.File f:files){bytes+=f.length();if(bytes>96L*1024*1024||f.getName().endsWith(".tmp"))f.delete();}}
    public static Bitmap decodePhoto(Context c,MediaItem item,int max) throws Exception {
        if(Build.VERSION.SDK_INT>=28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(),item.uri),(decoder,info,source)->{
                Size s=info.getSize();float ratio=Math.min(1f,max/(float)Math.max(s.getWidth(),s.getHeight()));
                decoder.setTargetSize(Math.max(1,Math.round(s.getWidth()*ratio)),Math.max(1,Math.round(s.getHeight()*ratio)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        try(InputStream s=c.getContentResolver().openInputStream(item.uri)){BitmapFactory.decodeStream(s,null,o);}
        o.inJustDecodeBounds=false;o.inSampleSize=1;
        while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>max)o.inSampleSize*=2;
        Bitmap b;try(InputStream s=c.getContentResolver().openInputStream(item.uri)){b=BitmapFactory.decodeStream(s,null,o);}
        if(b==null)return null;
        try(InputStream s=c.getContentResolver().openInputStream(item.uri)){
            int orientation=new ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix=new Matrix();
            switch(orientation){
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:matrix.setScale(-1,1);break;
                case ExifInterface.ORIENTATION_ROTATE_180:matrix.setRotate(180);break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:matrix.setScale(1,-1);break;
                case ExifInterface.ORIENTATION_TRANSPOSE:matrix.setRotate(90);matrix.postScale(-1,1);break;
                case ExifInterface.ORIENTATION_ROTATE_90:matrix.setRotate(90);break;
                case ExifInterface.ORIENTATION_TRANSVERSE:matrix.setRotate(-90);matrix.postScale(-1,1);break;
                case ExifInterface.ORIENTATION_ROTATE_270:matrix.setRotate(270);break;
            }
            if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),matrix,true);if(rotated!=b)b.recycle();return rotated;}
        }catch(Exception ignored){}
        return b;
    }
}
