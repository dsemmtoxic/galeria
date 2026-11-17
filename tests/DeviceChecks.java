package app.galeria;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaExtractor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Method;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.*;

/** Run only on a disposable emulator. Creates one small photo for integration checks. */
public final class DeviceChecks extends Instrumentation {
    private MainActivity library;private ViewerActivity viewer;private Uri owned;private final List<String> passed=new ArrayList<>();
    private Prefs prefs;private boolean autoplay;private int volume;private final List<Uri> copies=new ArrayList<>();private EditorActivity editor;private File qaFolder;private SettingsActivity settings;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private Object field(Object object,String name)throws Exception {Class<?> type=object.getClass();while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException e){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
    private void call(Object o,String name)throws Exception{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}
    private interface Condition {boolean ok()throws Exception;}
    private void await(Condition condition,String message)throws Exception {
        long deadline=SystemClock.elapsedRealtime()+45000;
        while(SystemClock.elapsedRealtime()<deadline){if(condition.ok())return;Thread.sleep(250);}
        throw new AssertionError(message);
    }
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void pass(String name){passed.add(name);Bundle b=new Bundle();b.putString("check",name);b.putString("stream",name+"\n");sendStatus(0,b);}
    @SuppressWarnings("unchecked")
    private List<MediaItem> list(MainActivity a)throws Exception {return (List<MediaItem>)field(a,"all");}
    private void touch(View v,int action,long down,long time,float x,float y){
        MotionEvent e=MotionEvent.obtain(down,time,action,x,y,0);v.dispatchTouchEvent(e);e.recycle();
    }
    private void doubleTap(View v,float x,float y){
        long t=SystemClock.uptimeMillis();
        touch(v,MotionEvent.ACTION_DOWN,t,t,x,y);touch(v,MotionEvent.ACTION_UP,t,t+35,x,y);
        touch(v,MotionEvent.ACTION_DOWN,t+120,t+120,x,y);touch(v,MotionEvent.ACTION_UP,t+120,t+155,x,y);
    }
    private void swipe(View v,float x,float y1,float y2){
        long t=SystemClock.uptimeMillis();touch(v,MotionEvent.ACTION_DOWN,t,t,x,y1);
        for(int i=1;i<=5;i++)touch(v,MotionEvent.ACTION_MOVE,t,t+i*30,x,y1+(y2-y1)*i/5);
        touch(v,MotionEvent.ACTION_UP,t,t+180,x,y2);
    }
    private void pair(View v,int action,long down,long time,float x1,float x2,float y){
        MotionEvent.PointerProperties[] p={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};
        MotionEvent.PointerCoords[] c={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};
        for(int i=0;i<2;i++){p[i].id=i;p[i].toolType=MotionEvent.TOOL_TYPE_FINGER;c[i].x=i==0?x1:x2;c[i].y=y;c[i].pressure=1;c[i].size=1;}
        MotionEvent e=MotionEvent.obtain(down,time,action,2,p,c,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);v.dispatchTouchEvent(e);e.recycle();
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context context=getTargetContext();prefs=new Prefs(context);autoplay=prefs.bool("autoplay",true);prefs.set("autoplay",false);
            AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            library=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->(boolean)field(library,"loaded"),"Library did not finish loading.");
            check(context.getResources().getDisplayMetrics().density>=2.9f,"Run the display checks at density 480 (3x).");
            runOnMainSync(()->{ImageView icon=Ui.icon(context,"rotate",Color.WHITE,28);int pixels=Ui.dp(context,28);icon.measure(View.MeasureSpec.makeMeasureSpec(pixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(pixels,View.MeasureSpec.EXACTLY));icon.layout(0,0,pixels,pixels);Bitmap bitmap=Bitmap.createBitmap(pixels,pixels,Bitmap.Config.ARGB_8888);icon.draw(new Canvas(bitmap));int min=pixels,max=-1;for(int y=0;y<pixels;y++)for(int x=0;x<pixels;x++)if(Color.alpha(bitmap.getPixel(x,y))>100){min=Math.min(min,x);max=Math.max(max,x);}check(max-min>=Ui.dp(context,18),"Icon drawing is still intrinsic 24 pixels instead of scaling with density: "+(max-min));bitmap.recycle();ImageView button=Ui.iconButton(context,"settings","Ajustes",()->{});check(button.getLayoutParams().width==Ui.dp(context,48),"Icon touch target is smaller than 48dp.");});
            pass("Icons at 3x density: painted width exceeds 54px; touch target is 144px");
            check(!hasText(library.getWindow().getDecorView(),"MINHA BIBLIOTECA")&&descriptions(library.getWindow().getDecorView(),"Abrir câmera")==0,"Removed library heading/camera still present.");
            pass("Main interface: no library eyebrow or camera action");
            final String stamp=Long.toString(SystemClock.elapsedRealtime());
            ContentValues values=new ContentValues();values.put("_display_name","Ação_Check_"+stamp+".png");values.put("mime_type","image/png");values.put("relative_path","Pictures/GaleriaQA/");values.put("is_pending",1);
            owned=context.getContentResolver().insert(MediaStore.Images.Media.getContentUri("external_primary"),values);
            check(owned!=null,"Photo insertion failed.");
            Bitmap b=Bitmap.createBitmap(800,1000,Bitmap.Config.ARGB_8888);for(int y=0;y<1000;y+=10)for(int x=0;x<800;x+=10){Paint paint=new Paint();paint.setColor((x/10+y/10)%2==0?0xFF796197:0xFF2C2141);new Canvas(b).drawRect(x,y,x+10,y+10,paint);}
            try(OutputStream stream=context.getContentResolver().openOutputStream(owned)){check(b.compress(Bitmap.CompressFormat.PNG,100,stream),"PNG encoding failed.");}
            b.recycle();values.clear();values.put("is_pending",0);context.getContentResolver().update(owned,values,null,null);
            await(()->{for(MediaItem m:list(library))if(m.uri.equals(owned))return true;return false;},"New photo did not appear while library was open.");
            pass("MediaStore observer: new photo appears while library is open");
            Prefs first=new Prefs(context);first.toggleFavorite(owned.toString());
            check(new Prefs(context).favorite(owned.toString()),"Favorite not retained.");
            first.toggleFavorite(owned.toString());check(!new Prefs(context).favorite(owned.toString()),"Favorite removal failed.");
            pass("Favorites: add, reopen preferences, remove");
            SearchIndex searchIndex=new SearchIndex(context,MediaRepo.query(context,false));
            check(searchIndex.suggest("acao_check_"+stamp).stream().anyMatch(row->row.kind==SearchIndex.FILE&&row.cover.uri.equals(owned)),"Accent-insensitive filename suggestions failed.");
            pass("Search: filename suggestions match without accents");
            final MediaRepo cachedRepo=(MediaRepo)field(library,"repo");
            Thread.sleep(1800);final int queriesBefore=(int)field(cachedRepo,"queryCount");
            final Object adapterBefore=field(library,"adapter");
            viewer=(ViewerActivity)startActivitySync(new Intent(context,ViewerActivity.class).setData(owned).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->field(viewer,"photo")!=null,"Photo viewer did not open.");
            ZoomImageView photo=(ZoomImageView)field(viewer,"photo");
            await(()->field(photo,"image")!=null&&photo.getWidth()>0&&photo.getHeight()>0,"Photo did not decode and lay out.");
            runOnMainSync(()->doubleTap(photo,photo.getWidth()/2f,photo.getHeight()/2f));
            await(photo::zoomed,"Photo double tap did not zoom.");
            pass("Photo viewer: double tap enlarges decoded image");
            await(()->(float)field(photo,"zoom")>=2.8f,"Zoom animation did not finish.");final float panBefore=(float)field(photo,"panX");
            runOnMainSync(()->{long t=SystemClock.uptimeMillis();float x=photo.getWidth()/2f,y=photo.getHeight()/2f;touch(photo,MotionEvent.ACTION_DOWN,t,t,x,y);touch(photo,MotionEvent.ACTION_MOVE,t,t+100,x+40,y);touch(photo,MotionEvent.ACTION_UP,t,t+200,x+40,y);});
            check(Math.abs((float)field(photo,"panX")-panBefore)>=59,"Zoom pan still moves at slow 1:1 sensitivity.");pass("Zoom pan: 40px finger movement pans at least 59px");

            Thread.sleep(350);
            runOnMainSync(()->{
                photo.reset();long t=SystemClock.uptimeMillis();float x=photo.getWidth()/2f,y=photo.getHeight()/2f;
                touch(photo,MotionEvent.ACTION_DOWN,t,t,x-Ui.dp(context,40),y);
                pair(photo,MotionEvent.ACTION_POINTER_DOWN|(1<<8),t,t+20,x-Ui.dp(context,40),x+Ui.dp(context,40),y);
                pair(photo,MotionEvent.ACTION_MOVE,t,t+45,x-Ui.dp(context,70),x+Ui.dp(context,70),y);
                pair(photo,MotionEvent.ACTION_MOVE,t,t+75,x-Ui.dp(context,120),x+Ui.dp(context,120),y);
                pair(photo,MotionEvent.ACTION_MOVE,t,t+100,x-Ui.dp(context,155),x+Ui.dp(context,155),y);
                pair(photo,MotionEvent.ACTION_MOVE,t,t+125,x-Ui.dp(context,175),x+Ui.dp(context,175),y);
                pair(photo,MotionEvent.ACTION_POINTER_UP|(1<<8),t,t+155,x-Ui.dp(context,175),x+Ui.dp(context,175),y);
                touch(photo,MotionEvent.ACTION_UP,t,t+180,x-Ui.dp(context,175),y);
            });
            check(photo.zoomed(),"Pinch did not zoom.");pass("Photo viewer: two-finger pinch enlarges image");
            int systemBefore=android.provider.Settings.System.getInt(context.getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS);
            runOnMainSync(()->{photo.reset();swipe(photo,photo.getWidth()*.8f,photo.getHeight()*.4f,photo.getHeight()*.65f);});
            float brightness=viewer.getWindow().getAttributes().screenBrightness;
            check(brightness>=.02f&&brightness<=1,"Right-side gesture did not set window brightness.");
            check(systemBefore==android.provider.Settings.System.getInt(context.getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS),"System brightness changed.");
            pass("Right-side gesture: window brightness changes; system brightness stays unchanged");
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.min(3,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),0);
            int before=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            runOnMainSync(()->swipe(photo,photo.getWidth()*.2f,photo.getHeight()*.65f,photo.getHeight()*.4f));
            await(()->audio.getStreamVolume(AudioManager.STREAM_MUSIC)>before,"Left-side gesture did not raise media volume.");
            check(brightness==viewer.getWindow().getAttributes().screenBrightness,"Left-side volume gesture changed brightness.");
            pass("Left-side gesture: media volume rises independently of brightness");
            check(viewer.getRequestedOrientation()==android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER,"Viewer ignores Android rotation policy.");
            pass("Rotation: viewer uses Android user orientation policy");
            LinearLayout viewerTools=(LinearLayout)((LinearLayout)field(viewer,"bottom")).getChildAt(0);
            String[] actions={"Editar","Favoritar","Apagar","Informações","Mais opções"};check(viewerTools.getChildCount()==5,"Viewer must have five actions.");for(int i=0;i<5;i++)check(actions[i].contentEquals(viewerTools.getChildAt(i).getContentDescription()),"Incorrect viewer action order.");
            check(!((TextView)field(viewer,"caption")).getText().toString().contains(" de "),"Redundant viewer item counter.");
            check(descriptions(viewer.getWindow().getDecorView(),"Mais opções")==1&&descriptions(viewer.getWindow().getDecorView(),"Opções da mídia")==0,"Viewer menu remains duplicated at the top.");
            pass("Viewer: five icon actions in requested order, only bottom More, no item counter");
            runOnMainSync(viewer::finish);viewer=null;
            Thread.sleep(900);check((int)field(cachedRepo,"queryCount")==queriesBefore,"Returning from the viewer queried unchanged media again.");check(field(library,"adapter")==adapterBefore,"Returning rebuilt the grid.");
            pass("Cache: viewer return keeps the grid and skips unchanged MediaStore queries");
            MediaItem video=null;for(MediaItem m:MediaRepo.query(context,false))if(m.video&&m.duration>15000){video=m;break;}
            check(video!=null,"A test video longer than 15 seconds is required.");prefs.set("position:"+video.key(),0);
            viewer=(ViewerActivity)startActivitySync(new Intent(context,ViewerActivity.class).setData(video.uri).putExtra("video",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->field(viewer,"player")!=null,"Video viewer did not open.");
            PlayerSurface player=(PlayerSurface)field(viewer,"player");
            await(()->(boolean)field(player,"ready"),"Video player did not prepare.");
            View touch=player.getChildAt(2);
            runOnMainSync(()->doubleTap(touch,touch.getWidth()*.8f,touch.getHeight()*.4f));
            await(()->player.renderedPosition()>=4000&&player.renderedPosition()<=6500,"Video double tap did not advance five seconds.");
            Thread.sleep(600);
            runOnMainSync(()->doubleTap(touch,touch.getWidth()*.2f,touch.getHeight()*.4f));
            await(()->player.renderedPosition()<1500,"Video double tap did not go back five seconds.");
            pass("Video viewer: right double tap +5 s; left double tap -5 s");
            SeekBar seek=(SeekBar)field(player,"seek");
            runOnMainSync(()->{long t=SystemClock.uptimeMillis();touch(seek,MotionEvent.ACTION_DOWN,t,t,seek.getWidth()*.3f,seek.getHeight()/2f);touch(seek,MotionEvent.ACTION_MOVE,t,t+40,seek.getWidth()*.5f,seek.getHeight()/2f);});
            await(()->Math.abs(player.renderedPosition()-seek.getProgress())<700&&player.renderedPosition()>5000,"Dragging did not seek before release: position="+player.renderedPosition()+", ready="+field(player,"ready")+", duration="+field(player,"duration")+", seek="+seek.getWidth()+"x"+seek.getHeight()+", progress="+seek.getProgress());
            check(player.interacting(),"Player is not tracking the active drag.");
            final int preciseBefore=seek.getProgress();
            runOnMainSync(()->{long t=SystemClock.uptimeMillis();touch(seek,MotionEvent.ACTION_MOVE,t,t,seek.getWidth()*.5f+1,seek.getHeight()/2f-210*context.getResources().getDisplayMetrics().density);});
            check(Math.abs(seek.getProgress()-preciseBefore)<=2,"Fine scrub skips more than 2 ms per pixel.");
            runOnMainSync(()->{long t=SystemClock.uptimeMillis();touch(seek,MotionEvent.ACTION_UP,t,t,seek.getWidth()*.5f,seek.getHeight()/2f);});
            pass("Video timeline: actual player seeks while finger is still dragging");
            final MediaItem.Album[] album=new MediaItem.Album[1];for(MediaItem.Album a:MediaRepo.albums(list(library),prefs))if(!a.items.isEmpty()){album[0]=a;break;}
            runOnMainSync(viewer::finish);viewer=null;
            runOnMainSync(()->library.album(album[0]));
            check(findDescription(library.getWindow().getDecorView(),"Pesquisar")==null,"Search is visible inside an album.");
            runOnMainSync(library::onBackPressed);
            final int oldSort=prefs.integer("sort",0);runOnMainSync(()->{prefs.set("sort",4);try{call(library,"update");}catch(Exception e){throw new RuntimeException(e);}});
            final MediaGridAdapter sizeAdapter=(MediaGridAdapter)field(library,"adapter");final List<?> sizeItems=(List<?>)field(library,"visible");final String[] badge={""};
            runOnMainSync(()->{LinearLayout row=(LinearLayout)sizeAdapter.getView(0,null,(android.view.ViewGroup)unchecked(library,"list"));android.widget.FrameLayout cell=(android.widget.FrameLayout)row.getChildAt(0);badge[0]=((TextView)cell.getChildAt(2)).getText().toString();});
            check(badge[0].equals(Ui.bytes(((MediaItem)sizeItems.get(0)).size)),"Size sorting hides the individual file size.");
            runOnMainSync(()->{prefs.set("sort",oldSort);try{call(library,"update");}catch(Exception e){throw new RuntimeException(e);}});
            pass("Albums/search and size order: album search hidden; file size visible on its thumbnail");
            final long[] buildTime={0};
            runOnMainSync(()->{List<MediaItem> tenThousand=new ArrayList<>();for(int i=0;i<10000;i++){MediaItem m=new MediaItem();m.uri=Uri.parse("content://media/external/images/media/"+(900000+i));m.added=System.currentTimeMillis()-(i/25)*86400000L;tenThousand.add(m);}MediaGridAdapter a=new MediaGridAdapter(context,library);long begin=SystemClock.elapsedRealtime();a.media(tenThousand,true);buildTime[0]=SystemClock.elapsedRealtime()-begin;check(a.totalItems()==10000,"Fast scroll lost files.");int middle=a.firstItem(a.rowForFraction(.5f));check(middle>=4995&&middle<=5005,"Fast scroll file index is incorrect.");});
            check(buildTime[0]<1500,"Ten-thousand-file row preparation blocks UI for "+buildTime[0]+" ms.");
            pass("Large grid: 10000 files indexed in "+buildTime[0]+" ms, correct midpoint count");
            editor=(EditorActivity)startActivitySync(new Intent(context,EditorActivity.class).setData(owned).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->(boolean)field(editor,"loaded"),"Image editor did not load.");EditorCanvas edit=(EditorCanvas)field(editor,"canvas");await(()->!(boolean)field(edit,"busy"),"Editor preview did not render.");
            check(edit.tool()==EditorCanvas.NONE&&!edit.dirty(),"Editor opens with active crop or modified media.");
            runOnMainSync(()->swipe(edit,edit.getWidth()*.5f,edit.getHeight()*.4f,edit.getHeight()*.6f));check(!edit.dirty(),"Neutral editor changes crop on touch.");
            runOnMainSync(()->{edit.tool(EditorCanvas.BLUR);swipe(edit,edit.getWidth()*.5f,edit.getHeight()*.4f,edit.getHeight()*.6f);});
            check(edit.snapshot().filters.size()==1&&edit.snapshot().filters.get(0).points.size()>3&&edit.snapshot().regions.isEmpty(),"Blur is not stored as a finger stroke.");
            runOnMainSync(()->{edit.tool(EditorCanvas.MOSAIC);swipe(edit,edit.getWidth()*.7f,edit.getHeight()*.4f,edit.getHeight()*.6f);});
            check(edit.snapshot().filters.size()==2&&edit.snapshot().filters.get(1).mosaic,"Mosaic brush did not track the finger.");
            pass("Editor: opens neutral; touch is inert until tool chosen; blur and mosaic follow the finger");
            runOnMainSync(()->{edit.undo();edit.undo();edit.tool(EditorCanvas.CROP);edit.aspect(1);edit.rotate();edit.dimensions(200,200);});
            await(()->!(boolean)field(edit,"busy"),"Rotated editor preview did not render.");
            runOnMainSync(()->((View)unchecked(editor,"save")).performClick());
            await(()->editor.isFinishing(),"Image editor did not save a copy.");
            Uri edited=null;for(MediaItem m:MediaRepo.query(context,false))if(m.name.startsWith("Ação_Check_"+stamp+"_editada_")){edited=m.uri;break;}check(edited!=null,"Edited copy did not enter MediaStore.");copies.add(edited);BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;try(InputStream in=context.getContentResolver().openInputStream(edited)){BitmapFactory.decodeStream(in,null,size);}check(size.outWidth==200&&size.outHeight==200,"Edited image resolution is incorrect.");try(InputStream in=context.getContentResolver().openInputStream(owned)){BitmapFactory.decodeStream(in,null,size);}check(size.outWidth==800&&size.outHeight==1000,"Image editing altered the original.");
            pass("Image editor: square crop, clockwise rotation, resize, saved copy; original preserved");
            MediaItem clip=null;for(MediaItem m:MediaRepo.query(context,false))if(m.video&&m.name.equals("EditorQA.mp4")){clip=m;break;}check(clip!=null,"EditorQA.mp4 fixture is missing.");
            java.io.File output=new java.io.File(context.getExternalFilesDir(null),"integration-export.mp4");EditSpec spec=new EditSpec();spec.rotation=90;spec.crop.set(.1f,.1f,.9f,.9f);spec.targetWidth=160;spec.targetHeight=96;EditSpec.FilterStroke mosaic=new EditSpec.FilterStroke(true,.15f);mosaic.points.add(new PointF(.2f,.2f));mosaic.points.add(new PointF(.4f,.3f));spec.filters.add(mosaic);EditSpec.FilterStroke blur=new EditSpec.FilterStroke(false,.15f);blur.points.add(new PointF(.65f,.6f));blur.points.add(new PointF(.8f,.8f));spec.filters.add(blur);EditSpec.Stroke line=new EditSpec.Stroke(android.graphics.Color.WHITE,.02f);line.points.add(new PointF(.4f,.3f));line.points.add(new PointF(.6f,.6f));spec.strokes.add(line);
            VideoExporter.export(context,clip.uri,output,spec,true,new VideoExporter.Cancel(),v->{});MediaExtractor exported=new MediaExtractor();exported.setDataSource(output.getAbsolutePath());boolean videoOk=false,audioOk=false;for(int i=0;i<exported.getTrackCount();i++){android.media.MediaFormat f=exported.getTrackFormat(i);String mime=f.getString(android.media.MediaFormat.KEY_MIME);if(mime.startsWith("video/"))videoOk=f.getInteger("width")==160&&f.getInteger("height")==96;if(mime.startsWith("audio/"))audioOk=true;}exported.release();check(videoOk&&audioOk,"Video export lost dimensions or audio.");
            pass("Video editor: crop, rotation, blur, mosaic, drawing, 160x96 H264 export with audio");
            Bitmap checker=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888);for(int y=0;y<120;y++)for(int x=0;x<120;x++)checker.setPixel(x,y,(x/3+y/3)%2==0?Color.WHITE:Color.BLACK);
            EditSpec maskSpec=new EditSpec();EditSpec.FilterStroke painted=new EditSpec.FilterStroke(false,.12f);painted.points.add(new PointF(.2f,.2f));painted.points.add(new PointF(.8f,.2f));painted.points.add(new PointF(.8f,.8f));maskSpec.filters.add(painted);Bitmap masked=EditSpec.render(checker,maskSpec,120);
            check(masked.getPixel(60,24)!=checker.getPixel(60,24),"Painted pixels were not blurred.");check(masked.getPixel(60,60)==checker.getPixel(60,60),"Blur filled the bounding rectangle instead of the L-shaped finger path.");check(masked.getPixel(10,100)==checker.getPixel(10,100),"Blur changed untouched pixels.");masked.recycle();checker.recycle();pass("Brush output: painted pixels change; center of L-shaped bounding box and outside pixels stay original");
            final MediaItem photoItem=MediaRepo.query(context,false).stream().filter(m->m.uri.equals(owned)).findFirst().get();String folder="Envio_QA_"+stamp;qaFolder=FileAccess.folder(folder);File sourcePhoto=FileAccess.source(context,photoItem);
            FolderTransfers.Result copied=FolderTransfers.send(context,Collections.singletonList(photoItem),folder,false);check(copied.completed==1&&copied.failed==0&&sourcePhoto.exists(),"Direct copy failed or deleted the original.");
            FolderTransfers.Result again=FolderTransfers.send(context,Collections.singletonList(photoItem),folder,false);check(again.completed==1&&qaFolder.listFiles().length==2,"Existing named folder was not reused / filename collision not preserved.");
            pass("Named destination: two immediate copies reuse Pictures/Gallery/owner/<name>, preserve source and filename collision");
            File originalVideo=FileAccess.source(context,clip);File oldParent=originalVideo.getParentFile();String oldName=originalVideo.getName();FolderTransfers.Result moved=FolderTransfers.send(context,Collections.singletonList(clip),folder,true);
            check(moved.completed==1&&moved.failed==0&&!originalVideo.exists(),"Direct move of other-owner video failed: "+moved.error);File relocated=FileAccess.source(context,clip);check(relocated.getParentFile().equals(qaFolder)&&relocated.length()>0,"Video is not in fixed Pictures destination.");
            check(relocated.renameTo(new File(oldParent,oldName)),"Could not restore the fixture.");FolderTransfers.scan(context,new File(oldParent,oldName).getAbsolutePath(),clip.mime);FolderTransfers.scan(context,relocated.getAbsolutePath(),clip.mime);
            pass("Direct move: other-owner video moves into Pictures without a write request; source disappears; fixture restored");
            MediaItem.Album target=null;for(MediaItem.Album a:MediaRepo.albums(MediaRepo.query(context,false),prefs))if(a.name.equals(folder)){target=a;break;}check(target!=null&&target.items.size()==2,"Copied album not indexed.");final MediaItem.Album deleteTarget=target;final MediaActions mediaActions=(MediaActions)field(library,"actions");
            runOnMainSync(()->{try{Method deletion=MediaActions.class.getDeclaredMethod("deleteAlbumNow",MediaItem.Album.class);deletion.setAccessible(true);deletion.invoke(mediaActions,deleteTarget);}catch(Exception e){throw new RuntimeException(e);}});
            await(()->!qaFolder.exists(),"Deleting the album did not remove its files and directory.");await(()->!MediaRepo.query(context,false).stream().anyMatch(m->m.album.equals(folder)),"Deleted media are still indexed in the album.");check(!prefs.folders().stream().anyMatch(f->f.name.equals(folder)),"Deleted album remains registered.");pass("Delete album: both media files, physical directory and album registration removed");
            // The editor has its own task in this instrumentation run. Bring the actual
            // library window back before testing animated scrolling and layout.
            runOnMainSync(()->library.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
            await(library::hasWindowFocus,"Library window did not return to the foreground for scrolling.");
            runOnMainSync(()->((MediaRepo)unchecked(library,"repo")).stop());
            final MediaGridAdapter scroller=(MediaGridAdapter)field(library,"adapter");final List<MediaItem> synthetic=new ArrayList<>();for(int i=0;i<240;i++)synthetic.add(photoItem);ListView mediaList=(ListView)field(library,"list");
            runOnMainSync(()->{scroller.media(synthetic,false);mediaList.setVisibility(View.VISIBLE);((View)unchecked(library,"empty")).setVisibility(View.GONE);mediaList.setSelectionFromTop(0,0);});await(()->mediaList.getChildCount()>2,"Synthetic scroll list did not lay out.");Thread.sleep(350);
            ScrollIndicator indicator=(ScrollIndicator)field(library,"scrollIndicator");
            nativeScroll(mediaList,true);await(()->((View)field(library,"navigation")).getHeight()==0,"Bottom navigation did not hide.");
            await(()->(int)field(indicator,"state")==AbsListView.OnScrollListener.SCROLL_STATE_IDLE,"Downward fling did not settle.");
            nativeScroll(mediaList,false);await(()->((View)field(library,"navigation")).getHeight()>=Ui.dp(context,70),"Bottom navigation did not reappear on upward scroll.");pass("Navigation: hides scrolling down and returns only after scrolling up");
            await(()->(int)field(indicator,"state")==AbsListView.OnScrollListener.SCROLL_STATE_IDLE,"Upward fling did not settle.");
            runOnMainSync(()->mediaList.setSelectionFromTop(10,-30));Thread.sleep(350);float fractionA=(float)field(indicator,"fraction");runOnMainSync(()->mediaList.setSelectionFromTop(10,-33));Thread.sleep(350);float fractionB=(float)field(indicator,"fraction");check(fractionB>fractionA&&fractionB-fractionA<.001f,"Scrollbar position jumps by a whole row or ignores partial pixel offsets.");pass("Scrollbar: 3px scroll changes thumb fraction continuously within the same row");
            if(!AppLock.deviceSecure(context))shell("locksettings set-pin 1234");
            check(AppLock.deviceSecure(context),"Configure the disposable emulator with PIN 1234 for lock checks.");
            settings=(SettingsActivity)startActivitySync(new Intent(context,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            View lockOption=findDescription(settings.getWindow().getDecorView(),"Bloquear ao abrir");check(lockOption instanceof Switch,"Startup authentication option is missing.");
            runOnMainSync(()->((Switch)lockOption).setChecked(true));await(()->AppLock.authenticating,"Enabling lock did not request Android authentication.");enterCredential();
            await(()->AppLock.enabled(context)&&AppLock.canAccess(context),"Native credential did not enable lock / unlock the session.");pass("App lock setting: Android PIN confirmation enables the option and authenticates the session");
            runOnMainSync(settings::finish);settings=null;shell("input keyevent 3");Thread.sleep(1000);check(AppLock.canAccess(context)&&!AppLock.authenticating,"Home invalidated the open session.");pass("App lock session: Android Home preserves authentication and does not prompt again");
            runOnMainSync(library::finishAndRemoveTask);await(()->!AppLock.canAccess(context),"Closing the root task did not clear authentication.");pass("Closing the app task requires authentication on the next opening");
            prefs.set("autoplay",true);viewer=(ViewerActivity)startActivitySync(new Intent(context,ViewerActivity.class).setData(video.uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->field(viewer,"shield")!=null&&AppLock.authenticating,"External viewer startup bypassed the lock.");
            check((viewer.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)==0,"Screenshots remain blocked.");PlayerSurface protectedPlayer=(PlayerSurface)field(viewer,"player");if(protectedPlayer!=null)check(!protectedPlayer.playing(),"Video plays before authentication.");
            enterCredential();await(()->AppLock.canAccess(context)&&field(viewer,"shield")==null,"Valid Android PIN did not reveal the viewer.");if(protectedPlayer!=null)await(protectedPlayer::playing,"Autoplay did not resume after successful authentication.");
            pass("Startup lock: external viewer is shielded, video waits; Android PIN reveals media and permits playback");prefs.set("app_lock",false);
            result.putString("stream","PASS: "+passed.size()+" integration checks\n");result.putString("checks",String.join("\n",passed));
            cleanup();finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){
            result.putString("stream","FAIL: "+failure.toString()+"\n"+android.util.Log.getStackTraceString(failure));result.putString("checks",String.join("\n",passed));cleanup();finish(Activity.RESULT_CANCELED,result);
        }
    }
    private View findDescription(View v,String text){if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=findDescription(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
    private void shell(String cmd)throws Exception{try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(cmd))){byte[] b=new byte[4096];while(in.read(b)>0){}}}
    private void nativeScroll(View view,boolean down)throws Exception{
        int[] xy=new int[2];runOnMainSync(()->view.getLocationOnScreen(xy));int x=xy[0]+view.getWidth()/2,low=xy[1]+view.getHeight()*3/4,high=xy[1]+view.getHeight()/3;
        shell("input swipe "+x+" "+(down?low:high)+" "+x+" "+(down?high:low)+" 650");
    }
    private android.view.accessibility.AccessibilityNodeInfo credentialNode(android.view.accessibility.AccessibilityNodeInfo node){if(node==null)return null;String text=node.getText()==null?"":node.getText().toString();String id=node.getViewIdResourceName()==null?"":node.getViewIdResourceName();if(text.equalsIgnoreCase("Use PIN")||text.equalsIgnoreCase("Usar PIN")||id.endsWith("device_credential_button"))return node;for(int i=0;i<node.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo found=credentialNode(node.getChild(i));if(found!=null)return found;}return null;}
    private void enterCredential()throws Exception{
        Thread.sleep(800);android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();android.view.accessibility.AccessibilityNodeInfo button=credentialNode(root);
        if(button!=null){button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);Thread.sleep(1000);}
        shell("input text 1234");shell("input keyevent 66");
    }
    private boolean hasText(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasText(((ViewGroup)v).getChildAt(i),text))return true;return false;}
    private int descriptions(View v,String description){int n=description.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=descriptions(((ViewGroup)v).getChildAt(i),description);return n;}
    private Object unchecked(Object o,String n){try{return field(o,n);}catch(Exception e){throw new RuntimeException(e);}}
    private void cleanup(){
        if(prefs!=null)prefs.set("app_lock",false);try{if(settings!=null)runOnMainSync(settings::finish);}catch(Exception ignored){}
        if(qaFolder!=null){File[] children=qaFolder.listFiles();if(children!=null)for(File file:children)file.delete();qaFolder.delete();}

        try{if(editor!=null&&!editor.isFinishing())runOnMainSync(editor::finish);}catch(Exception ignored){}
        for(Uri uri:copies)try{getTargetContext().getContentResolver().delete(uri,null,null);}catch(Exception ignored){}
        try{if(viewer!=null)runOnMainSync(viewer::finish);}catch(Exception ignored){}
        try{if(prefs!=null)prefs.set("autoplay",autoplay);((AudioManager)getTargetContext().getSystemService(Context.AUDIO_SERVICE)).setStreamVolume(AudioManager.STREAM_MUSIC,volume,0);}catch(Exception ignored){}
        try{if(owned!=null)getTargetContext().getContentResolver().delete(owned,null,null);}catch(Exception ignored){}
    }
}
