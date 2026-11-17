package app.galeria;

import android.app.*;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.animation.DecelerateInterpolator;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class ViewerActivity extends SecureActivity implements ZoomImageView.Gestures {
    private String sessionId;private Bundle browseScope;private boolean recovering;
    private Prefs prefs;private MediaActions actions;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService metadata=Executors.newSingleThreadExecutor();
    private final ExecutorService audioWorker=Executors.newSingleThreadExecutor();
    private final AtomicInteger requestedVolume=new AtomicInteger(-1);
    private final AtomicBoolean settingVolume=new AtomicBoolean();
    private volatile int volumeEpoch;
    private int lastVolume=-1;private boolean gestureOnRight;
    private List<MediaItem> items=new ArrayList<>();private int index;
    private FrameLayout root,media,hud,lockOverlay;private LinearLayout top,bottom;
    private ImageView adjacentPreview,previousPreview,nextPreview;private int adjacentIndex=-1;private float swipeOffset;
    private ValueAnimator swipeAnimator;private boolean swipeAnimating;
    private ZoomImageView photo;private PlayerSurface player;private TextView heading,caption,hudLabel;
    private ImageView favoriteIcon,hudIcon;private ProgressBar hudBar;
    private boolean controls=true,locked,trash,resumed,manualBrightness;
    private float originalBrightness=-1,currentBrightness=-1,gestureBrightness;
    private int gestureVolume,maxVolume;
    private AudioManager audio;
    private final Runnable hideControls=()->chrome(false);
    private final Runnable hideHud=()->{if(hud!=null)hud.setVisibility(View.GONE);};
    private final Map<String,Integer> rotations=new HashMap<>();
    private int rotation(){Integer r=rotations.get(item().key());return r==null?0:r;}
    private void rotate(){int r=(rotation()+90)%360;rotations.put(item().key(),r);if(photo!=null)photo.rotate(r);if(player!=null)player.rotate(r);schedule();}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Ui.theme(this);prefs=new Prefs(this);audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        originalBrightness=getWindow().getAttributes().screenBrightness;
        currentBrightness=state!=null?state.getFloat("brightness",-1):prefs.bool("max_brightness",false)?1:-1;
        manualBrightness=state!=null&&state.getBoolean("manual_brightness");
        trash=getIntent().getBooleanExtra("trash",false);
        Uri target=state!=null?Uri.parse(state.getString("uri",getIntent().getData()==null?"":getIntent().getData().toString())):getIntent().getData();
        if(target==null||target.toString().isEmpty()){finish();return;}
        boolean external=Intent.ACTION_VIEW.equals(getIntent().getAction());
        sessionId=state==null?getIntent().getStringExtra("session"):state.getString("session",getIntent().getStringExtra("session"));
        browseScope=state==null?getIntent().getBundleExtra("scope"):state.getBundle("scope");
        if(!external&&browseScope==null)browseScope=new Bundle();
        items=!external&&sessionId!=null&&sessionId.equals(MediaItem.Session.id)?new ArrayList<>(MediaItem.Session.items):new ArrayList<>();
        boolean found=false;for(int i=0;i<items.size();i++)if(items.get(i).uri.equals(target)){index=i;found=true;break;}
        if(!found){items.clear();MediaItem placeholder=new MediaItem();placeholder.uri=target;items.add(placeholder);index=0;}
        actions=new MediaActions(this,removed->{
            if(!removed.isEmpty()){
                items.removeIf(m->removed.contains(m.key()));persistSession();
                if(items.isEmpty()){finish();return;}index=Math.min(index,items.size()-1);show();
            }else{updateTitle();persistSession();}
        });
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        orientation();
        if(found)show();
        else{
            FrameLayout loading=new FrameLayout(this);loading.setBackgroundColor(Color.BLACK);
            TextView label=Ui.text(this,"Abrindo mídia…",14,Ui.MUTED);label.setGravity(Gravity.CENTER);loading.addView(label,new FrameLayout.LayoutParams(-1,-1));
            ImageView back=Ui.iconButton(this,"back","Voltar",this::finish);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48),Gravity.TOP|Gravity.LEFT);p.topMargin=statusInset()+Ui.dp(this,8);loading.addView(back,p);setContentView(loading);
            recovering=true;metadata.execute(()->{
                List<MediaItem> recovered=ViewerSession.recover(this,sessionId,browseScope);MediaItem resolved=incoming(target);
                main.post(()->{if(isFinishing()||isDestroyed())return;items=new ArrayList<>(recovered);index=-1;for(int i=0;i<items.size();i++)if(items.get(i).uri.equals(target)){index=i;break;}
                    if(index<0){items.add(0,resolved);index=0;}recovering=false;if(sessionId==null&&!external)sessionId=ViewerSession.start(this,items);else persistSession();show();
                });
            });
        }
    }
    private void persistSession(){if(sessionId!=null&&!recovering)ViewerSession.save(this,sessionId,items);}
    private MediaItem incoming(Uri uri) {
        MediaItem m=new MediaItem();m.uri=uri;m.video=getIntent().getBooleanExtra("video",false);
        String mime=getIntent().getType();
        try{String actual=getContentResolver().getType(uri);if(actual!=null)mime=actual;}catch(Exception ignored){}
        if(mime!=null){m.mime=mime;m.video=mime.startsWith("video/");}
        m.name="Mídia";m.album="Arquivo";m.added=m.date=System.currentTimeMillis();
        try(Cursor c=getContentResolver().query(uri,null,null,null,null)){
            if(c!=null&&c.moveToFirst()){
                String name=MediaRepo.string(c,"_display_name");if(!name.isEmpty())m.name=name;
                m.size=MediaRepo.number(c,"_size");m.width=(int)MediaRepo.number(c,"width");m.height=(int)MediaRepo.number(c,"height");m.duration=MediaRepo.number(c,"duration");
                String album=MediaRepo.string(c,"bucket_display_name");if(!album.isEmpty())m.album=album;
                m.path=MediaRepo.string(c,"relative_path");long date=MediaRepo.number(c,"datetaken");if(date>0)m.date=date;
                long added=MediaRepo.number(c,"date_added");if(added>0)m.added=added*1000;if(date<=0)m.date=m.added;
            }
        }catch(Exception ignored){}return m;
    }
    private MediaItem item(){return items.get(index);}
    private int statusInset(){int id=getResources().getIdentifier("status_bar_height","dimen","android");return id==0?Ui.dp(this,24):getResources().getDimensionPixelSize(id);}
    private int navInset(){int id=getResources().getIdentifier("navigation_bar_height","dimen","android");return id==0?Ui.dp(this,24):getResources().getDimensionPixelSize(id);}
    private void show(){
        if(items.isEmpty()||isFinishing())return;
        if(swipeAnimator!=null){swipeAnimator.cancel();swipeAnimator=null;}
        swipeAnimating=false;swipeOffset=0;adjacentIndex=-1;
        if(previousPreview!=null)ThumbLoader.get(this).clear(previousPreview);
        if(nextPreview!=null)ThumbLoader.get(this).clear(nextPreview);
        adjacentPreview=previousPreview=nextPreview=null;
        if(photo!=null){photo.close();photo=null;}if(player!=null){player.release();player=null;}
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        previousPreview=createNeighborCover();nextPreview=createNeighborCover();
        if(index>0)ThumbLoader.get(this).bind(previousPreview,items.get(index-1),384);
        if(index+1<items.size())ThumbLoader.get(this).bind(nextPreview,items.get(index+1),384);
        media=new FrameLayout(this);media.setBackgroundColor(Color.BLACK);root.addView(media,new FrameLayout.LayoutParams(-1,-1));
        if(item().video){player=new PlayerSurface(this,item(),this,this::state);media.addView(player,new FrameLayout.LayoutParams(-1,-1));player.lifecycle(resumed);}
        else {photo=new ZoomImageView(this,this);media.addView(photo,new FrameLayout.LayoutParams(-1,-1));photo.lifecycle(resumed);photo.load(item());}
        top=Ui.column(this);top.setPadding(Ui.dp(this,8),statusInset()+Ui.dp(this,7),Ui.dp(this,8),Ui.dp(this,22));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xDC000000,0x00000000}));
        LinearLayout bar=Ui.row(this);bar.addView(viewerButton("back","Voltar para a biblioteca",this::finish));
        LinearLayout names=Ui.column(this);names.setPadding(Ui.dp(this,6),0,Ui.dp(this,8),0);
        heading=Ui.bold(this,"",15,Ui.TEXT);heading.setMaxLines(1);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);names.addView(heading);
        caption=Ui.text(this,"",11,Ui.MUTED);caption.setPadding(0,Ui.dp(this,6),0,0);caption.setMaxLines(1);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);names.addView(caption);bar.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(viewerButton("rotate","Girar 90 graus no sentido horário",this::rotate));top.addView(bar);root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        bottom=Ui.column(this);bottom.setPadding(Ui.dp(this,6),Ui.dp(this,35),Ui.dp(this,6),navInset()+Ui.dp(this,7));
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xEB000000}));
        if(player!=null)bottom.addView(player.controls());
        LinearLayout tools=Ui.row(this);tools.setPadding(Ui.dp(this,6),0,Ui.dp(this,6),0);
        if(trash){
            action(tools,"restore","Restaurar",()->actions.restore(Collections.singletonList(item())));
            action(tools,"trash","Apagar",()->actions.delete(Collections.singletonList(item()),true));
            action(tools,"info","Informações",()->actions.details(item()));
            action(tools,"more","Mais opções",this::menu);
        }else{
            action(tools,"edit","Editar",this::edit);
            favoriteIcon=action(tools,prefs.favorite(item().key())?"heartFill":"heart","Favoritar",()->{
                boolean added=prefs.toggleFavorite(item().key());favoriteIcon.setImageDrawable(new ViewerIcons(added?"heartFill":"heart",added?Ui.ACCENT:Ui.TEXT));favoriteIcon.setContentDescription(added?"Remover dos favoritos":"Favoritar");schedule();
            });
            action(tools,"trash","Apagar",()->actions.delete(Collections.singletonList(item()),false));
            action(tools,"info","Informações",()->{cancelHide();actions.details(item());});
            action(tools,"more","Mais opções",this::menu);
        }
        bottom.addView(tools);root.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        if(photo!=null)photo.rotate(rotation());if(player!=null)player.rotate(rotation());
        buildHud();applyBrightness(currentBrightness);updateTitle();chrome(controls);if(locked)lock();state();
    }
    private ImageView createNeighborCover(){
        ImageView image=new ImageView(this);image.setBackgroundColor(Color.BLACK);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setVisibility(View.GONE);
        root.addView(image,new FrameLayout.LayoutParams(-1,-1));return image;
    }
    private ImageView action(LinearLayout row,String icon,String label,Runnable click){
        FrameLayout cell=new FrameLayout(this);cell.setMinimumHeight(Ui.dp(this,60));cell.setBackground(Ui.ripple(this,Color.TRANSPARENT,18));cell.setContentDescription(label);cell.setFocusable(true);
        ImageView image=Ui.icon(this,icon,icon.equals("heartFill")?Ui.ACCENT:Ui.TEXT,24);image.setImageDrawable(new ViewerIcons(icon,icon.equals("heartFill")?Ui.ACCENT:Ui.TEXT));cell.addView(image,new FrameLayout.LayoutParams(Ui.dp(this,24),Ui.dp(this,24),Gravity.CENTER));
        cell.setOnClickListener(v->click.run());row.addView(cell,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));return image;
    }
    private ImageView viewerButton(String icon,String label,Runnable click){ImageView v=Ui.iconButton(this,icon,label,click);v.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));v.setImageDrawable(new ViewerIcons(icon,Ui.TEXT));return v;}
    private void updateTitle(){if(heading==null||items.isEmpty())return;heading.setText(item().video?item().name:Ui.date(item().date,"d 'de' MMMM 'de' yyyy"));caption.setText(Ui.date(item().date,"HH:mm"));}
    private void orientation(){setRequestedOrientation(prefs.bool("auto_rotate",true)?ActivityInfo.SCREEN_ORIENTATION_FULL_USER:ActivityInfo.SCREEN_ORIENTATION_LOCKED);}
    private void edit(){cancelHide();if(player!=null)player.lifecycle(false);Intent i=new Intent(this,EditorActivity.class).setData(item().uri);i.putExtra("video",item().video);i.putExtra("rotation",rotation());startActivityForResult(i,450);}
    @Override protected void onResume(){
        super.onResume();resumed=true;if(prefs!=null){orientation();if(!manualBrightness)currentBrightness=prefs.bool("max_brightness",false)?1:-1;applyBrightness(currentBrightness);}
        if(player!=null)player.lifecycle(true);if(photo!=null)photo.lifecycle(true);schedule();
    }
    @Override protected void onLocked(){if(player!=null)player.lifecycle(false);if(photo!=null)photo.lifecycle(false);}
    @Override protected void onUnlocked(){if(resumed){if(player!=null)player.lifecycle(true);if(photo!=null)photo.lifecycle(true);schedule();}}
    @Override protected void onPause(){
        resumed=false;super.onPause();cancelHide();if(swipeAnimator!=null){swipeAnimator.cancel();swipeAnimator=null;swipeAnimating=false;}if(media!=null)media.setTranslationX(0);if(adjacentPreview!=null)adjacentPreview.setVisibility(View.GONE);if(player!=null)player.lifecycle(false);if(photo!=null)photo.lifecycle(false);
        WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=originalBrightness;getWindow().setAttributes(a);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override protected void onDestroy(){super.onDestroy();if(swipeAnimator!=null)swipeAnimator.cancel();if(previousPreview!=null)ThumbLoader.get(this).clear(previousPreview);if(nextPreview!=null)ThumbLoader.get(this).clear(nextPreview);metadata.shutdownNow();audioWorker.shutdownNow();main.removeCallbacksAndMessages(null);if(photo!=null)photo.close();if(player!=null)player.release();if(actions!=null)actions.close();}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(top!=null)top.setPadding(Ui.dp(this,8),statusInset()+Ui.dp(this,7),Ui.dp(this,8),Ui.dp(this,22));if(bottom!=null)bottom.setPadding(Ui.dp(this,6),Ui.dp(this,35),Ui.dp(this,6),navInset()+Ui.dp(this,7));}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(!items.isEmpty())state.putString("uri",item().uri.toString());state.putString("session",sessionId);if(browseScope!=null)state.putBundle("scope",browseScope);state.putFloat("brightness",currentBrightness);state.putBoolean("manual_brightness",manualBrightness);}
    @Override public void tap(){if(!locked)chrome(!controls);}
    private void chrome(boolean visible){
        controls=visible;int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if(!visible)flags|=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if(top!=null){top.setVisibility(visible?View.VISIBLE:View.GONE);bottom.setVisibility(visible?View.VISIBLE:View.GONE);}
        schedule();
    }
    private void cancelHide(){main.removeCallbacks(hideControls);}
    private void schedule(){cancelHide();if(resumed&&controls&&player!=null&&player.playing()&&!player.interacting()&&!locked)main.postDelayed(hideControls,4000);}
    private void state(){
        if(prefs==null)return;
        if(resumed&&player!=null&&player.playing()&&prefs.bool("keep_screen",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        schedule();
    }
    @Override public boolean canNavigate(int direction){
        return !locked&&!recovering&&index+direction>=0&&index+direction<items.size();
    }
    @Override public void navigate(int direction){
        if(!canNavigate(direction))return;
        index+=direction;show();
    }
    private int slideWidth(){return media!=null&&media.getWidth()>0?media.getWidth():getResources().getDisplayMetrics().widthPixels;}
    private void previewAdjacent(int direction){
        if(!canNavigate(direction))return;
        ImageView chosen=direction>0?nextPreview:previousPreview;
        if(chosen==null)return;
        if(adjacentPreview!=chosen&&adjacentPreview!=null)adjacentPreview.setVisibility(View.GONE);
        adjacentPreview=chosen;adjacentIndex=index+direction;
        adjacentPreview.setVisibility(View.VISIBLE);
    }
    /** Both ZoomImageView and PlayerSurface use the same horizontal carousel. */
    @Override public void swipe(float offset){
        if(media==null||swipeAnimating)return;
        int direction=offset<0?1:-1;
        float translation=offset;
        if(!canNavigate(direction)){
            // Resistance is only used at the first/last media item.
            translation=Math.copySign(Math.min(slideWidth()*.13f,Math.abs(offset)*.26f),offset);
            if(adjacentPreview!=null)adjacentPreview.setVisibility(View.GONE);
        }else{
            previewAdjacent(direction);
            if(adjacentPreview!=null)adjacentPreview.setTranslationX(translation+direction*(slideWidth()+Ui.dp(this,14)));
        }
        swipeOffset=translation;media.setTranslationX(translation);
    }
    @Override public void swipeEnd(float offset,boolean commit){
        if(media==null||swipeAnimating)return;
        int direction=adjacentIndex>=0&&adjacentPreview!=null&&adjacentPreview.getVisibility()==View.VISIBLE
                ? (adjacentIndex>index?1:-1) : (offset<0?1:-1);
        boolean advance=commit&&Math.abs(offset)>Ui.dp(this,20)&&canNavigate(direction);
        if(swipeAnimator!=null)swipeAnimator.cancel();
        float start=swipeOffset;
        float finish=advance?-direction*(slideWidth()+Ui.dp(this,14)):0f;
        swipeAnimating=true;
        swipeAnimator=ValueAnimator.ofFloat(start,finish);
        swipeAnimator.setDuration(advance?240:300);
        swipeAnimator.setInterpolator(new DecelerateInterpolator());
        swipeAnimator.addUpdateListener(a->{
            float x=(float)a.getAnimatedValue();swipeOffset=x;
            if(media!=null)media.setTranslationX(x);
            if(adjacentPreview!=null&&adjacentPreview.getVisibility()==View.VISIBLE){
                adjacentPreview.setTranslationX(x+direction*(slideWidth()+Ui.dp(this,14)));
            }
        });
        swipeAnimator.addListener(new AnimatorListenerAdapter(){
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator a){cancelled=true;}
            @Override public void onAnimationEnd(Animator a){
                swipeAnimator=null;swipeAnimating=false;
                if(cancelled||isFinishing()||isDestroyed())return;
                if(advance&&canNavigate(direction))navigate(direction);
                else{
                    swipeOffset=0;if(media!=null)media.setTranslationX(0);
                    if(adjacentPreview!=null)adjacentPreview.setVisibility(View.GONE);
                }
            }
        });
        swipeAnimator.start();
    }
    private void buildHud(){
        hud=new FrameLayout(this);hud.setBackground(Ui.bg(0xE81B1724,22,this));
        LinearLayout col=Ui.column(this);col.setGravity(Gravity.CENTER);col.setPadding(Ui.dp(this,18),Ui.dp(this,19),Ui.dp(this,18),Ui.dp(this,19));
        hudIcon=Ui.icon(this,"sun",Ui.ACCENT,27);col.addView(hudIcon);hudLabel=Ui.bold(this,"",18,Ui.TEXT);hudLabel.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,12));col.addView(hudLabel);
        hudBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);hudBar.setMax(100);hudBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));col.addView(hudBar,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,4)));hud.addView(col);hud.setVisibility(View.GONE);
        root.addView(hud,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER_VERTICAL));
    }
    private float systemBrightness(){
        try{return Math.max(.02f,Math.min(1,android.provider.Settings.System.getInt(getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS)/255f));}catch(Exception e){return .5f;}
    }
    private void applyBrightness(float value){
        if(prefs==null)return;currentBrightness=value;WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=value;getWindow().setAttributes(a);
    }
    @Override public void verticalStart(boolean right){
        if(locked)return;cancelHide();main.removeCallbacks(hideHud);
        gestureBrightness=currentBrightness<0?systemBrightness():currentBrightness;gestureVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);maxVolume=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        gestureOnRight=right;lastVolume=gestureVolume;volumeEpoch++;
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)hud.getLayoutParams();p.gravity=Gravity.CENTER_VERTICAL|(right?Gravity.RIGHT:Gravity.LEFT);p.leftMargin=p.rightMargin=Ui.dp(this,30);hud.setLayoutParams(p);
        hudIcon.setImageDrawable(new Icons(right?"sun":"volume",Ui.ACCENT));hud.setVisibility(View.VISIBLE);
    }
    @Override public void vertical(boolean right,float delta){
        if(locked)return;
        int percent;
        if(right){manualBrightness=true;float value=Math.max(.02f,Math.min(1,gestureBrightness+delta*1.6f));applyBrightness(value);percent=Math.round(value*100);}
        else {
            int target=Math.max(0,Math.min(maxVolume,Math.round(gestureVolume+delta*maxVolume*1.6f)));
            if(player!=null&&target>0)player.unmute();
            if(target!=lastVolume){lastVolume=target;requestedVolume.set(target);drainVolume();}
            percent=maxVolume>0?Math.round(target*100f/maxVolume):0;
        }
        hudLabel.setText(percent+"%");hudBar.setProgress(percent);
    }
    private void drainVolume(){
        if(audioWorker.isShutdown()||!settingVolume.compareAndSet(false,true))return;
        audioWorker.execute(()->{
            try{
                int target;
                while((target=requestedVolume.getAndSet(-1))>=0&&!Thread.currentThread().isInterrupted()){
                    final int requested=target,epoch=volumeEpoch;
                    try{
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC,target,0);
                        final int actual=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                        main.post(()->{
                            if(isFinishing()||isDestroyed()||gestureOnRight||epoch!=volumeEpoch||requested!=lastVolume||hud==null)return;
                            int value=maxVolume>0?Math.round(actual*100f/maxVolume):0;hudLabel.setText(value+"%");hudBar.setProgress(value);
                        });
                    }catch(SecurityException e){main.post(()->{if(!isFinishing())Ui.toast(this,"O sistema limitou o ajuste de volume.");});}
                }
            }finally{settingVolume.set(false);if(requestedVolume.get()>=0)drainVolume();}
        });
    }
    @Override public void verticalEnd(){main.removeCallbacks(hideHud);main.postDelayed(hideHud,650);schedule();}
    @Override public void error(String message){Ui.toast(this,message);}
    private void menu(){
        cancelHide();Ui.Sheet s=new Ui.Sheet(this,"Opções",item().name);
        if(trash){s.item("restore","Restaurar arquivo",null,()->actions.restore(Collections.singletonList(item())));s.item("trash","Excluir permanentemente",null,()->actions.delete(Collections.singletonList(item()),true));}
        else {
            s.item("edit","Renomear arquivo",null,()->actions.rename(item()));
            s.item("move","Mover para pasta",null,()->actions.organize(Collections.singletonList(item()),true));
            s.item("copy","Copiar para pasta",null,()->actions.organize(Collections.singletonList(item()),false));
            s.item("share","Compartilhar",null,()->actions.share(Collections.singletonList(item())));
            if(!item().video)s.item("lens","Google Lens",null,()->LensIntegration.open(this,item()));
            if(player!=null){s.item("speed","Velocidade de reprodução",null,()->player.speed());s.item("fullscreen","Ajustar vídeo à tela",null,()->player.fit());}
            s.item("lock","Bloquear controles","Use o cadeado para desbloquear",this::lock);
            s.item("trash","Excluir arquivo","Enviar para a lixeira",()->actions.delete(Collections.singletonList(item()),false));
        }
        s.item("video","Abrir em outro aplicativo",null,this::external);
        s.item("settings","Ajustes do visualizador",null,()->startActivity(new Intent(this,SettingsActivity.class)));s.show();
    }
    private void external(){
        Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(item().uri,item().mime);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setPackage(null);
        try{startActivity(Intent.createChooser(intent,"Abrir com"));}catch(Exception e){Ui.toast(this,"Nenhum aplicativo compatível disponível.");}
    }
    private void lock(){
        locked=true;chrome(false);
        if(lockOverlay!=null)root.removeView(lockOverlay);
        lockOverlay=new FrameLayout(this);lockOverlay.setClickable(true);lockOverlay.setBackgroundColor(Color.TRANSPARENT);root.addView(lockOverlay,new FrameLayout.LayoutParams(-1,-1));
        ImageView unlock=Ui.iconButton(this,"unlock","Desbloquear controles",this::unlock);unlock.setBackground(Ui.ripple(this,0xAA24202E,25));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,50),Gravity.BOTTOM|Gravity.RIGHT);p.rightMargin=Ui.dp(this,20);p.bottomMargin=navInset()+Ui.dp(this,25);lockOverlay.addView(unlock,p);
    }
    private void unlock(){locked=false;if(lockOverlay!=null){root.removeView(lockOverlay);lockOverlay=null;}chrome(true);}
    @Override public void onBackPressed(){
        if(locked){unlock();return;}
        if(photo!=null&&photo.zoomed()){photo.reset();return;}
        super.onBackPressed();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(actions!=null)actions.onPermissionsResult(r);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==450&&result==RESULT_OK&&data!=null&&data.getData()!=null){
        Uri saved=data.getData();metadata.execute(()->{MediaItem m=incoming(saved);main.post(()->{if(isFinishing()||isDestroyed())return;items.add(index+1,m);index++;persistSession();show();Ui.toast(this,"Cópia editada salva.");});});
        }else if(actions!=null)actions.onResult(request,result,data);}
}
