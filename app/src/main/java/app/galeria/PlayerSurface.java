package app.galeria;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.atomic.AtomicLong;

/** Every MediaPlayer operation, including setup, seeking and teardown, runs off the UI thread. */
public final class PlayerSurface extends FrameLayout implements TextureView.SurfaceTextureListener {
    private final Prefs prefs;private final MediaItem item;private final ZoomImageView.Gestures gestures;private final Runnable stateChanged;
    private final TextureView texture;private final Touch touch;private final Matrix displayMatrix=new Matrix();private final AudioManager audio;private final AudioFocusRequest focus;
    private final Handler main=new Handler(Looper.getMainLooper());private final HandlerThread thread=new HandlerThread("Galeria-player",android.os.Process.THREAD_PRIORITY_DISPLAY);private final Handler engine;
    private MediaPlayer player;private Surface surface;private SurfaceTexture sourceTexture;
    private volatile boolean ready,active=true,released,muted,hasFocus,autoResume,draggingSeek,ended,playing;
    private boolean seeking;private volatile boolean resumeAfterScrub;private float zoom=1,panX,panY;private int rotation,vw,vh;private volatile long duration,position,decodedPosition;
    private final AtomicLong pendingSeek=new AtomicLong(-1);
    private TextView current,total,speedLabel,status;private SeekBar seek;private ImageView playButton,muteButton;private boolean shownPlaying,shownMuted;
    private final TextView seekHint;private int burst;private long burstAt;
    private final Runnable hideSeek=()->seekHintVisibility();
    private void seekHintVisibility(){seekHint.setVisibility(GONE);}
    private final Runnable ticker=new Runnable(){public void run(){if(released||!active)return;if(ready&&player!=null&&!draggingSeek&&!seeking)try{position=decodedPosition=player.getCurrentPosition();}catch(Exception ignored){}ui(()->updateControls());engine.postDelayed(this,playing?100:600);}};
    public PlayerSurface(Context c,MediaItem m,ZoomImageView.Gestures g,Runnable changed){
        super(c);item=m;gestures=g;stateChanged=changed;prefs=new Prefs(c);muted=prefs.bool("muted",false);setBackgroundColor(Color.BLACK);thread.start();engine=new Handler(thread.getLooper());
        audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()).setOnAudioFocusChangeListener(change->{
            if(!ready||player==null)return;try{if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){player.setVolume(muted?0:.25f,muted?0:.25f);return;}if(change<0){autoResume=change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT&&playing;pauseEngine(false);if(change==AudioManager.AUDIOFOCUS_LOSS)abandonFocus();}else if(change==AudioManager.AUDIOFOCUS_GAIN){player.setVolume(muted?0:1,muted?0:1);if(autoResume&&active){autoResume=false;startEngine();}}}catch(Exception ignored){}
        },engine).build();
        texture=new TextureView(c);texture.setSurfaceTextureListener(this);addView(texture,new LayoutParams(-1,-1));
        status=Ui.text(c,"Carregando vídeo…",14,Ui.MUTED);status.setGravity(Gravity.CENTER);addView(status,new LayoutParams(-1,-1));
        touch=new Touch(c);touch.setContentDescription("Vídeo. Pinça para ampliar, arraste para percorrer e duplo toque nas laterais para voltar ou avançar.");addView(touch,new LayoutParams(-1,-1));
        seekHint=Ui.bold(c,"",23,Ui.TEXT);seekHint.setGravity(Gravity.CENTER);seekHint.setPadding(Ui.dp(c,22),Ui.dp(c,16),Ui.dp(c,22),Ui.dp(c,16));seekHint.setBackground(Ui.bg(0xC02C223D,22,c));seekHint.setVisibility(GONE);addView(seekHint,new LayoutParams(-2,-2,Gravity.CENTER));
    }
    private void ui(Runnable r){main.post(()->{if(!released)r.run();});}
    private void changed(){ui(()->{updateControls();stateChanged.run();});}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){if(released)return;sourceTexture=t;surface=new Surface(t);engine.post(()->prepare());}
    private void prepare(){
        if(released)return;
        try{player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());player.setSurface(surface);player.setDataSource(getContext(),item.uri);player.setLooping(prefs.bool("loop_video",false));player.setVolume(muted?0:1,muted?0:1);
            player.setOnPreparedListener(mp->{if(released)return;ready=true;waitingForUnlock=prefs.bool("autoplay",true)&&!AppLock.canAccess(getContext());duration=mp.getDuration();vw=mp.getVideoWidth();vh=mp.getVideoHeight();ui(()->{status.setVisibility(GONE);transform();});
                float speed=prefs.integer("speed_percent",100)/100f;if(speed!=1)try{mp.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(1));mp.pause();}catch(Exception ignored){}
                long p=prefs.bool("remember_position",true)?prefs.integer("position:"+item.key(),0):0;
                if(p>0&&p<duration-1500){resumeAfterScrub=active&&prefs.bool("autoplay",true);pendingSeek.set(p);seekEngine();}else if(active&&prefs.bool("autoplay",true))startEngine();
                engine.removeCallbacks(ticker);engine.post(ticker);changed();
            });
            player.setOnSeekCompleteListener(mp->{if(released)return;seeking=false;try{decodedPosition=mp.getCurrentPosition();if(!draggingSeek)position=decodedPosition;}catch(Exception ignored){}if(pendingSeek.get()>=0)seekEngine();else if(!draggingSeek&&resumeAfterScrub&&active){resumeAfterScrub=false;startEngine();}changed();});
            player.setOnVideoSizeChangedListener((mp,w,h)->{vw=w;vh=h;ui(this::transform);});
            player.setOnCompletionListener(mp->{ended=true;playing=false;position=duration;prefs.set("position:"+item.key(),0);changed();});
            player.setOnErrorListener((mp,what,extra)->{ready=false;playing=false;ui(()->{status.setText("Não foi possível reproduzir este vídeo.\nTente outro player pelo menu.");status.setVisibility(VISIBLE);stateChanged.run();});return true;});player.prepareAsync();
        }catch(Exception e){ready=false;ui(()->{status.setText("Não foi possível abrir este vídeo.");gestures.error("O vídeo está indisponível ou usa um formato não suportado.");});}
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){transform();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){release();return false;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture t){}
    public void rotate(int degrees){rotation=((degrees%360)+360)%360;zoom=1;panX=panY=0;transform();}
    private void clampPan(){
        int w=texture.getWidth(),h=texture.getHeight(),rw=rotation%180==0?vw:vh,rh=rotation%180==0?vh:vw;
        if(rw<=0||rh<=0||w<=0||h<=0)return;float fit=Math.min(w/(float)rw,h/(float)rh);
        float x=Math.max(0,(rw*fit*zoom-w)/2f),y=Math.max(0,(rh*fit*zoom-h)/2f);
        panX=Math.max(-x,Math.min(x,panX));panY=Math.max(-y,Math.min(y,panY));
    }
    private void transform(){
        int w=texture.getWidth(),h=texture.getHeight();if(vw<=0||vh<=0||w<=0||h<=0)return;
        int rw=rotation%180==0?vw:vh,rh=rotation%180==0?vh:vw;float fit=Math.min(w/(float)rw,h/(float)rh);clampPan();
        displayMatrix.reset();displayMatrix.setScale(vw*fit/w*zoom,vh*fit/h*zoom,w/2f,h/2f);displayMatrix.postRotate(rotation,w/2f,h/2f);displayMatrix.postTranslate(panX,panY);texture.setTransform(displayMatrix);
    }
    public boolean playing(){return ready&&!released&&playing;}
    public boolean muted(){return muted;}
    public boolean interacting(){return draggingSeek||touch.touching;}
    public long position(){return position;}
    public long renderedPosition(){return decodedPosition;}
    public void unmute(){if(muted)toggleMute();}
    private boolean requestAudioFocus(){if(hasFocus)return true;hasFocus=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;return hasFocus;}
    private void abandonFocus(){if(hasFocus){audio.abandonAudioFocusRequest(focus);hasFocus=false;}}
    public void toggle(){if(released)return;engine.post(()->{if(playing)pauseEngine(true);else startEngine();});}
    private boolean waitingForUnlock;
    private void startEngine(){if(!AppLock.canAccess(getContext())){waitingForUnlock=true;return;}if(!ready||player==null||!active||released||draggingSeek)return;if(!muted&&!requestAudioFocus())return;try{if(ended){pendingSeek.set(0);ended=false;resumeAfterScrub=true;seekEngine();return;}player.start();playing=true;changed();}catch(Exception ignored){}}
    private void pauseEngine(boolean abandon){try{if(ready&&player!=null){if(playing)player.pause();if(!seeking)position=decodedPosition=player.getCurrentPosition();}}catch(Exception ignored){}playing=false;if(abandon){autoResume=false;abandonFocus();}save();changed();}
    public void lifecycle(boolean foreground){active=foreground;if(released)return;engine.post(()->{engine.removeCallbacks(ticker);if(!foreground){resumeAfterScrub=false;pauseEngine(true);}else{engine.post(ticker);if(waitingForUnlock&&AppLock.canAccess(getContext())){waitingForUnlock=false;startEngine();}}});}
    public void toggleMute(){if(released)return;engine.post(()->{if(muted&&playing&&!requestAudioFocus())return;muted=!muted;try{if(ready&&player!=null)player.setVolume(muted?0:1,muted?0:1);}catch(Exception ignored){}if(muted)abandonFocus();changed();});}
    private void save(){if(prefs.bool("remember_position",true)&&ready)prefs.set("position:"+item.key(),position>=duration-1000?0:(int)Math.min(Integer.MAX_VALUE,position));}
    public void release(){if(released)return;save();released=true;active=false;playing=false;ready=false;main.removeCallbacksAndMessages(null);engine.removeCallbacksAndMessages(null);
        engine.post(()->{try{abandonFocus();if(player!=null)player.release();}catch(Exception ignored){}finally{player=null;if(surface!=null){surface.release();surface=null;}if(sourceTexture!=null){sourceTexture.release();sourceTexture=null;}thread.quitSafely();}});stateChanged.run();}
    private void seekEngine(){if(!ready||player==null||released||seeking)return;long target=pendingSeek.getAndSet(-1);if(target<0)return;try{seeking=true;ended=false;player.seekTo(target,MediaPlayer.SEEK_CLOSEST);}catch(Exception e){seeking=false;}}
    private void seekTo(long ms){if(!ready||released)return;position=Math.max(0,Math.min(Math.max(0,duration-1),ms));pendingSeek.set(position);engine.post(this::seekEngine);}
    public void jump(int seconds,boolean feedback){if(!ready||released)return;seekTo(position+seconds*1000L);updateControls();if(feedback){long now=SystemClock.uptimeMillis();if(now-burstAt>900||(burst>0)!=(seconds>0))burst=0;burst+=seconds;burstAt=now;seekHint.setText((burst>0?"+ ":"− ")+Math.abs(burst)+" s");LayoutParams p=(LayoutParams)seekHint.getLayoutParams();p.gravity=Gravity.CENTER_VERTICAL|(seconds>0?Gravity.RIGHT:Gravity.LEFT);p.leftMargin=p.rightMargin=Ui.dp(getContext(),30);seekHint.setLayoutParams(p);seekHint.setVisibility(VISIBLE);main.removeCallbacks(hideSeek);main.postDelayed(hideSeek,800);}}
    public void speed(){String[] names={"0,5×","0,75×","1×","1,25×","1,5×","2×"};int[] values={50,75,100,125,150,200};int selected=2;for(int i=0;i<values.length;i++)if(prefs.integer("speed_percent",100)==values[i])selected=i;
        new AlertDialog.Builder(getContext()).setTitle("Velocidade de reprodução").setSingleChoiceItems(names,selected,(d,i)->{engine.post(()->{if(!ready||released)return;try{boolean was=playing;player.setPlaybackParams(new PlaybackParams().setSpeed(values[i]/100f).setPitch(1));if(!was)player.pause();prefs.set("speed_percent",values[i]);changed();}catch(Exception e){ui(()->Ui.toast(getContext(),"Esta velocidade não está disponível neste vídeo."));}});d.dismiss();}).setNegativeButton("Cancelar",null).show();}
    public void fit(){int rw=rotation%180==0?vw:vh,rh=rotation%180==0?vh:vw;float a=texture.getWidth()/(float)Math.max(1,rw),b=texture.getHeight()/(float)Math.max(1,rh);zoom=zoom>1.05f?1:Math.min(6,Math.max(a,b)/Math.min(a,b));panX=panY=0;transform();}
    public View controls(){Context c=getContext();LinearLayout col=Ui.column(c);col.setPadding(Ui.dp(c,16),0,Ui.dp(c,16),Ui.dp(c,6));LinearLayout timeline=Ui.row(c);
        current=Ui.text(c,"0:00",11,Ui.MUTED);timeline.addView(current,new LinearLayout.LayoutParams(Ui.dp(c,72),-2));seek=new PreciseSeekBar(c);seek.setMax((int)Math.max(1,Math.min(Integer.MAX_VALUE,item.duration)));seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));seek.setPadding(Ui.dp(c,8),0,Ui.dp(c,8),0);seek.setContentDescription("Tempo do vídeo. Arraste para ver os frames. Afaste o dedo da barra para maior precisão.");timeline.addView(seek,new LinearLayout.LayoutParams(0,Ui.dp(c,48),1));total=Ui.text(c,Ui.duration(item.duration),11,Ui.MUTED);total.setGravity(Gravity.RIGHT);timeline.addView(total,new LinearLayout.LayoutParams(Ui.dp(c,47),-2));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){beginScrub();}public void onProgressChanged(SeekBar b,int p,boolean user){if(user){seekTo(p);current.setText(timePrecise(p));}}public void onStopTrackingTouch(SeekBar b){endScrub();}});col.addView(timeline);
        LinearLayout buttons=Ui.row(c);buttons.setGravity(Gravity.CENTER);muteButton=Ui.iconButton(c,muted?"mute":"volume",muted?"Ativar som":"Silenciar",this::toggleMute);buttons.addView(muteButton);buttons.addView(new View(c),new LinearLayout.LayoutParams(0,1,1));playButton=Ui.iconButton(c,"play","Reproduzir ou pausar",this::toggle);playButton.setPadding(Ui.dp(c,15),Ui.dp(c,15),Ui.dp(c,15),Ui.dp(c,15));playButton.setBackground(Ui.ripple(c,Ui.ACCENT,30));playButton.setImageDrawable(new Icons("play",Ui.BG));buttons.addView(playButton,new LinearLayout.LayoutParams(Ui.dp(c,60),Ui.dp(c,60)));buttons.addView(new View(c),new LinearLayout.LayoutParams(0,1,1));speedLabel=Ui.bold(c,"1×",13,Ui.TEXT);speedLabel.setGravity(Gravity.CENTER);speedLabel.setBackground(Ui.ripple(c,Ui.SURFACE,20));speedLabel.setOnClickListener(v->speed());buttons.addView(speedLabel,new LinearLayout.LayoutParams(Ui.dp(c,48),Ui.dp(c,40)));col.addView(buttons);updateControls();return col;}
    private String timePrecise(long ms){return Ui.duration(ms)+String.format(java.util.Locale.ROOT,".%03d",ms%1000);}
    private void text(TextView v,String s){if(v!=null&&!s.contentEquals(v.getText()))v.setText(s);}
    private void updateControls(){if(seek==null)return;long d=duration>0?duration:item.duration;if(!draggingSeek){seek.setMax((int)Math.max(1,Math.min(Integer.MAX_VALUE,d)));seek.setProgress((int)Math.min(Integer.MAX_VALUE,position));text(current,Ui.duration(position));}text(total,Ui.duration(d));
        if(playButton!=null&&(shownPlaying!=playing||playButton.getTag()==null)){shownPlaying=playing;playButton.setTag(Boolean.TRUE);playButton.setImageDrawable(new Icons(playing?"pause":"play",Ui.BG));playButton.setContentDescription(playing?"Pausar":"Reproduzir");}if(muteButton!=null&&(shownMuted!=muted||muteButton.getTag()==null)){shownMuted=muted;muteButton.setTag(Boolean.TRUE);muteButton.setImageDrawable(new Icons(muted?"mute":"volume",Ui.TEXT));muteButton.setContentDescription(muted?"Ativar som":"Silenciar");}text(speedLabel,(prefs.integer("speed_percent",100)/100f+"").replace(".0","").replace(".",",")+"×");}
    private void beginScrub(){if(draggingSeek)return;resumeAfterScrub=playing;draggingSeek=true;engine.post(()->pauseEngine(false));stateChanged.run();}
    private void endScrub(){seekTo(seek.getProgress());draggingSeek=false;engine.post(()->{if(!seeking&&pendingSeek.get()<0&&resumeAfterScrub&&active){resumeAfterScrub=false;startEngine();}});updateControls();stateChanged.run();}
    private final class PreciseSeekBar extends SeekBar {private float x,y,precise;PreciseSeekBar(Context c){super(c);}
        @Override public boolean onTouchEvent(MotionEvent e){int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();precise=Math.max(0,Math.min(getMax(),(x-getPaddingLeft())*getMax()/Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight())));beginScrub();setPressed(true);getParent().requestDisallowInterceptTouchEvent(true);setProgress(Math.round(precise));seekTo(getProgress());text(current,timePrecise(getProgress()));return true;}
            if(a==MotionEvent.ACTION_MOVE){float dy=Math.abs(e.getY()-y)/getResources().getDisplayMetrics().density;float factor=dy>96?.05f:dy>48?.2f:1;float step=dy>144?1:getMax()/(float)Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight())*factor;precise=Math.max(0,Math.min(getMax(),precise+(e.getX()-x)*step));x=e.getX();setProgress(Math.round(precise));seekTo(getProgress());text(current,timePrecise(getProgress()));return true;}
            if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){endScrub();setPressed(false);getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private final class Touch extends View {
        private final GestureDetector taps;private final ScaleGestureDetector pinch;
        private float downX,downY,lastX,lastY,focusX,focusY;private int drag;private boolean multiple,right,touching,doubleTap;
        Touch(Context c){
            super(c);setFocusable(true);
            taps=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onDown(MotionEvent e){return true;}
                @Override public boolean onSingleTapConfirmed(MotionEvent e){if(!multiple&&drag==0){gestures.tap();performClick();}return true;}
                @Override public boolean onDoubleTap(MotionEvent e){doubleTap=true;return true;}
                @Override public boolean onDoubleTapEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_UP&&doubleTap&&!multiple&&drag==0&&zoom<=1.01f&&prefs.bool("video_double",true))jump((e.getX()<getWidth()/2f?-1:1)*prefs.integer("skip_seconds",5),true);return true;}
            });
            pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScaleBegin(ScaleGestureDetector d){multiple=true;doubleTap=false;focusX=d.getFocusX();focusY=d.getFocusY();return prefs.bool("pinch",true);}
                @Override public boolean onScale(ScaleGestureDetector d){
                    float next=Math.max(1,Math.min(6,zoom*d.getScaleFactor())),ratio=next/zoom,cx=getWidth()/2f,cy=getHeight()/2f;
                    panX=d.getFocusX()-cx-(focusX-cx-panX)*ratio;panY=d.getFocusY()-cy-(focusY-cy-panY)*ratio;
                    focusX=d.getFocusX();focusY=d.getFocusY();zoom=next;transform();return true;
                }
            });pinch.setQuickScaleEnabled(false);pinch.setStylusScaleEnabled(false);
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public boolean onTouchEvent(MotionEvent e){
            int a=e.getActionMasked();
            if(a==MotionEvent.ACTION_DOWN){downX=lastX=e.getX();downY=lastY=e.getY();drag=0;multiple=false;doubleTap=false;touching=true;stateChanged.run();getParent().requestDisallowInterceptTouchEvent(true);}
            if(e.getPointerCount()>1){
                if(!multiple){MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);taps.onTouchEvent(cancel);cancel.recycle();if(drag==2)gestures.verticalEnd();drag=0;texture.setTranslationX(0);}
                multiple=true;doubleTap=false;
            }
            pinch.onTouchEvent(e);if(!multiple)taps.onTouchEvent(e);
            if(a==MotionEvent.ACTION_POINTER_UP){int remaining=e.getActionIndex()==0?1:0;lastX=e.getX(remaining);lastY=e.getY(remaining);}
            if(a==MotionEvent.ACTION_MOVE&&e.getPointerCount()==1){
                float dx=e.getX()-downX,dy=e.getY()-downY;
                if(zoom>1.01f){panX+=e.getX()-lastX;panY+=e.getY()-lastY;drag=3;transform();}
                else if(!multiple){
                    if(drag==0&&Math.max(Math.abs(dx),Math.abs(dy))>Ui.dp(getContext(),18)){
                        right=downX>getWidth()/2f;
                        if(Math.abs(dy)>Math.abs(dx)*1.25f&&(right?prefs.bool("brightness_gesture",true):prefs.bool("volume_gesture",true))){drag=2;gestures.verticalStart(right);}
                        else if(Math.abs(dx)>Math.abs(dy)*1.25f&&prefs.bool("swipe_nav",true))drag=1;
                    }
                    if(drag==2)gestures.vertical(right,-dy/Math.max(1,getHeight()));
                    if(drag==1)texture.setTranslationX(dx*.6f);
                }
                lastX=e.getX();lastY=e.getY();
            }
            if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
                touching=false;getParent().requestDisallowInterceptTouchEvent(false);
                if(drag==2)gestures.verticalEnd();
                if(drag==1){
                    int direction=e.getX()<downX?1:-1;
                    if(a==MotionEvent.ACTION_UP&&Math.abs(e.getX()-downX)>getWidth()*.22f&&gestures.canNavigate(direction)){
                        texture.animate().translationX(0).setDuration(150).withEndAction(()->gestures.navigate(direction)).start();
                    }else{
                        float from=texture.getTranslationX();
                        texture.animate().cancel();
                        texture.setTranslationX(from);
                        texture.animate().translationX(-from*.08f).setDuration(110).withEndAction(()->texture.animate().translationX(0).setDuration(170).start()).start();
                    }
                }
                drag=0;stateChanged.run();
            }return true;
        }
    }
}
