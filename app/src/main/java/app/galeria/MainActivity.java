package app.galeria;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.provider.MediaStore;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

public final class MainActivity extends SecureActivity implements MediaGridAdapter.Listener {
    private Prefs prefs;private MediaRepo repo;private MediaActions actions;
    private List<MediaItem> all=new ArrayList<>(),visible=new ArrayList<>();
    private List<MediaItem.Album> albums=new ArrayList<>();
    private final Set<String> selected=new HashSet<>();
    private int tab=0,filter=0;private String albumKey=null,query="",error=null;
    private boolean selecting,trash,loaded,permissionScreen;
    private LinearLayout root,filterBar,selectionBar;
    private View navigation;private boolean navHidden;private android.animation.ValueAnimator navAnimation;
    private ListView list;private MediaGridAdapter adapter;
    private TextView title,subtitle,count;
    private FrameLayout area;private LinearLayout empty;
    private int themeCode;
    private ScrollIndicator scrollIndicator;
    private String renderKey,groupKey="";
    private List<MediaItem> cachedAll,base=Collections.emptyList();
    private int images,videos,revision;
    private final Map<String,List<MediaItem>> datasets=new LinkedHashMap<String,List<MediaItem>>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,List<MediaItem>> e){return size()>12;}};
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Ui.theme(this);prefs=new Prefs(this);
        // A tela de entrada é sempre Galeria (aba 0). Não reutilizar a última aba
        // salva nem a aba recriada após a concessão das permissões de mídia.
        tab=0;filter=0;albumKey=null;query="";trash=false;
        repo=new MediaRepo(this,(items,message)->{
            all=items;error=message;loaded=true;revision++;datasets.clear();renderKey=null;
            Set<String> available=new HashSet<>();for(MediaItem m:all)available.add(m.key());selected.retainAll(available);
            if(permissionScreen && anyPermission()){build();}else update();
        });
        actions=new MediaActions(this,removed->{selected.removeAll(removed);if(selecting)leaveSelection();repo.refresh(true);});
        themeCode=themeCode();build();
    }
    private int themeCode(){return prefs.integer("accent",0xFFB497FF)+prefs.integer("columns",3)+(prefs.bool("oled",false)?71:0)+(prefs.bool("names",false)?17:0);}
    @Override protected void onResume(){super.onResume();Ui.theme(this);if(themeCode!=themeCode()||permissionScreen!=!anyPermission()){themeCode=themeCode();build();}else update();Ui.bars(this,Ui.BG);repo.trash(trash);repo.start();}
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        if(Intent.ACTION_MAIN.equals(intent.getAction())&&intent.hasCategory(Intent.CATEGORY_LAUNCHER)){
            selecting=false;selected.clear();tab=0;filter=0;albumKey=null;query="";trash=false;
            if(repo!=null)repo.trash(false);if(root!=null)build();
        }
    }
    @Override protected void onPause(){super.onPause();repo.stop();}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);build();}
    @Override protected void onDestroy(){super.onDestroy();main.removeCallbacksAndMessages(null);repo.close();actions.close();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("tab",tab);out.putInt("filter",filter);out.putString("query",query);out.putString("album",albumKey);out.putBoolean("trash",trash);}
    public boolean anyPermission() {
        if(FileAccess.direct(this))return true;
        if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED
            ||checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED
            ||(Build.VERSION.SDK_INT>=34&&checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED);
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }
    private boolean fullPermission(){
        if(FileAccess.direct(this))return true;
        if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED;
        return anyPermission();
    }
    private void requestAccess() {
        if(Build.VERSION.SDK_INT>=34)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},100);
        else if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO},100);
        else if(Build.VERSION.SDK_INT<=29)requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},100);
        else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},100);
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(actions!=null&&actions.onPermissionsResult(r))return;
        if(r==100){tab=0;filter=0;albumKey=null;trash=false;query="";loaded=false;all=new ArrayList<>();build();repo.refresh(true);if(!anyPermission())Ui.toast(this,"Você pode liberar o acesso em Ajustes quando quiser.");}
    }
    private void build() {
        int pos=list==null?0:list.getFirstVisiblePosition(),offset=list==null||list.getChildAt(0)==null?0:list.getChildAt(0).getTop();
        renderKey=null;Ui.bars(this,Ui.BG);permissionScreen=!anyPermission();
        if(navAnimation!=null)navAnimation.cancel();navigation=null;navHidden=false;
        root=Ui.column(this);root.setBackgroundColor(Ui.BG);
        root.setPadding(0,Ui.dp(this,8),0,0);setContentView(root);
        LinearLayout heading=Ui.column(this);heading.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,16),Ui.dp(this,13));
        LinearLayout titleRow=Ui.row(this);
        if(selecting||albumKey!=null||trash)titleRow.addView(Ui.iconButton(this,selecting?"close":"back",selecting?"Cancelar seleção":"Voltar",this::onBackPressed));
        title=Ui.bold(this,currentTitle(),selecting?25:32,Ui.TEXT);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(0,Ui.dp(this,7),0,Ui.dp(this,6));
        titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if(selecting)titleRow.addView(Ui.iconButton(this,"select","Selecionar todos",this::selectAll));
        else if(tab==1&&albumKey==null)titleRow.addView(Ui.iconButton(this,"plus","Criar nova pasta",()->actions.newFolder(()->repo.refresh(true))));
        if(!selecting&&albumKey==null&&!trash&&(tab==0||tab==1))titleRow.addView(Ui.iconButton(this,"search","Pesquisar",()->startActivityForResult(new Intent(this,SearchActivity.class),720)));
        if(!selecting)titleRow.addView(Ui.iconButton(this,"settings","Ajustes",this::settings));
        heading.addView(titleRow);
        subtitle=Ui.text(this,"",12,Ui.MUTED);subtitle.setPadding(Ui.dp(this,(selecting||albumKey!=null||trash)?48:0),Ui.dp(this,2),0,0);heading.addView(subtitle);root.addView(heading);
        if(permissionScreen) {
            buildPermission();root.addView(navigation());return;
        }
        root.setFocusableInTouchMode(true);root.requestFocus();
        if(!fullPermission()){
            TextView banner=Ui.text(this,"Acesso limitado · toque para escolher mais mídias",12,Ui.ACCENT);banner.setPadding(Ui.dp(this,22),Ui.dp(this,9),Ui.dp(this,22),Ui.dp(this,9));
            banner.setBackground(Ui.ripple(this,Ui.SOFT,0));banner.setOnClickListener(v->requestAccess());root.addView(banner);
        }
        filterBar=Ui.row(this);filterBar.setPadding(Ui.dp(this,20),Ui.dp(this,4),Ui.dp(this,20),Ui.dp(this,4));root.addView(filterBar);
        LinearLayout meta=Ui.row(this);meta.setPadding(Ui.dp(this,22),0,Ui.dp(this,12),0);
        count=Ui.text(this,"",12,Ui.MUTED);meta.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        if(tab==1&&albumKey==null&&!trash)meta.addView(Ui.iconButton(this,"refresh","Atualizar álbuns",()->repo.refresh(true)));
        else meta.addView(Ui.iconButton(this,"sort","Ordenar mídias",this::sort));
        meta.addView(Ui.iconButton(this,"more","Mais opções",this::menu));root.addView(meta,new LinearLayout.LayoutParams(-1,Ui.dp(this,44)));
        area=new FrameLayout(this);list=new GalleryListView(this,this::navigationHidden);list.setBackgroundColor(Color.TRANSPARENT);list.setDivider(null);
        list.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),Ui.dp(this,16));list.setClipToPadding(false);list.setVerticalScrollBarEnabled(false);list.setCacheColorHint(Color.TRANSPARENT);
        adapter=new MediaGridAdapter(this,this);list.setAdapter(adapter);area.addView(list,new FrameLayout.LayoutParams(-1,-1));
        scrollIndicator=new ScrollIndicator(this,list,adapter,down->navigationHidden(down));area.addView(scrollIndicator,new FrameLayout.LayoutParams(-1,-1));
        empty=Ui.column(this);empty.setGravity(Gravity.CENTER);empty.setPadding(Ui.dp(this,30),Ui.dp(this,20),Ui.dp(this,30),Ui.dp(this,20));area.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        root.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        if(selecting)root.addView(selectionTools());else root.addView(navigation());
        update();list.setSelectionFromTop(pos,offset);
    }
    private String currentTitle(){
        if(selecting)return selected.size()+" "+(selected.size()==1?"selecionado":"selecionados");
        if(trash)return "Lixeira";
        if(albumKey!=null)for(MediaItem.Album a:albums)if(a.key.equals(albumKey))return a.name;
        return tab==1?"Álbuns":tab==2?"Favoritos":"Galeria";
    }
    private void buildPermission(){
        LinearLayout body=Ui.column(this);body.setGravity(Gravity.CENTER);body.setPadding(Ui.dp(this,30),Ui.dp(this,24),Ui.dp(this,30),Ui.dp(this,28));
        ImageView icon=new ImageView(this);icon.setImageResource(R.mipmap.ic_launcher);body.addView(icon,new LinearLayout.LayoutParams(Ui.dp(this,120),Ui.dp(this,120)));
        TextView title=Ui.bold(this,"Seu mundo,\nem imagens.",29,Ui.TEXT);title.setGravity(Gravity.CENTER);title.setLineSpacing(Ui.dp(this,5),1);title.setPadding(0,Ui.dp(this,24),0,Ui.dp(this,14));body.addView(title);
        TextView explanation=Ui.text(this,"Fotos e vídeos organizados, com o seu estilo.\nLibere o acesso para começar.",15,Ui.MUTED);explanation.setGravity(Gravity.CENTER);explanation.setLineSpacing(Ui.dp(this,5),1);body.addView(explanation);
        TextView button=Ui.button(this,"Permitir acesso às mídias",this::requestAccess);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=Ui.dp(this,30);body.addView(button,bp);
        TextView appSettings=Ui.text(this,"Abrir permissões do aplicativo",12,Ui.MUTED);appSettings.setGravity(Gravity.CENTER);appSettings.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,10));
        appSettings.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))));body.addView(appSettings);
        TextView local=Ui.text(this,"Suas mídias permanecem no aparelho.",12,Ui.DIM);local.setGravity(Gravity.CENTER);local.setPadding(0,Ui.dp(this,14),0,0);body.addView(local);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));subtitle.setText("Fotos, vídeos e tudo o que vale guardar");
    }
    private LinearLayout navigation(){
        LinearLayout nav=Ui.row(this);nav.setBackground(Ui.border(Ui.SURFACE,Ui.LINE,0,this));nav.setPadding(Ui.dp(this,7),Ui.dp(this,7),Ui.dp(this,7),Ui.dp(this,6));
        String[] icons={"gallery","albums","heart"},labels={"Mídias","Álbuns","Favoritos"};
        for(int i=0;i<3;i++){
            final int index=i;boolean active=tab==i;
            LinearLayout cell=Ui.column(this);cell.setGravity(Gravity.CENTER);cell.setMinimumHeight(Ui.dp(this,58));cell.setBackground(Ui.ripple(this,Color.TRANSPARENT,18));
            FrameLayout pill=new FrameLayout(this);pill.setBackground(Ui.bg(active?Ui.SOFT:Color.TRANSPARENT,16,this));
            ImageView icon=Ui.icon(this,icons[i],active?Ui.ACCENT:Ui.MUTED,30);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(Ui.dp(this,30),Ui.dp(this,30),Gravity.CENTER);pill.addView(icon,ip);
            cell.addView(pill,new LinearLayout.LayoutParams(Ui.dp(this,60),Ui.dp(this,46)));
            cell.setOnClickListener(v->{switchTab(index);});cell.setContentDescription(labels[i]);cell.setFocusable(true);nav.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
        }
        navigation=nav;return nav;
    }
    private void navigationHidden(boolean hide){
        if(selecting||navigation==null||navHidden==hide)return;navHidden=hide;
        if(navAnimation!=null)navAnimation.cancel();
        final View bar=navigation;final LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)bar.getLayoutParams();
        int full=Ui.dp(this,71),start=bar.getHeight();bar.setVisibility(View.VISIBLE);
        navAnimation=android.animation.ValueAnimator.ofInt(start,hide?0:full);navAnimation.setDuration(180);
        navAnimation.setInterpolator(new android.view.animation.DecelerateInterpolator());
        navAnimation.addUpdateListener(a->{lp.height=(int)a.getAnimatedValue();bar.setLayoutParams(lp);bar.setAlpha(full==0?1:lp.height/(float)full);});
        navAnimation.start();
    }
    private void switchTab(int index){
        selecting=false;selected.clear();albumKey=null;trash=false;tab=index;query="";repo.trash(false);build();if(list!=null)list.setSelection(0);
    }
    private void settings(){startActivity(new Intent(this,SettingsActivity.class));}
    private void update() {
        if(permissionScreen||adapter==null)return;
        Set<String> hidden=prefs.hidden(),favs=prefs.favorites();String q=Ui.normalize(query.trim());
        String g=trash+hidden.toString()+prefs.string("folders","[]");
        if(cachedAll!=all||!groupKey.equals(g)){
            cachedAll=all;groupKey=g;datasets.clear();base=new ArrayList<>();images=videos=0;
            for(MediaItem m:all)if(trash||!hidden.contains(m.albumKey())){base.add(m);if(m.video)videos++;else images++;}
            albums=MediaRepo.albums(base,prefs);albums.removeIf(a->hidden.contains(a.key));
        }
        int ordering=prefs.integer("sort",0);
        String key=revision+"|"+g+"|"+tab+"|"+albumKey+"|"+filter+"|"+trash+"|"+q+"|"+ordering+"|"+(tab==2?favs.toString():"");
        String rendering=key+"|"+favs+"|"+prefs.bool("group_dates",true)+"|"+selecting;
        if(rendering.equals(renderKey))return;renderKey=rendering;
        visible=datasets.get(key);
        if(visible==null){
            List<MediaItem> scope=base;
            if(albumKey!=null){scope=Collections.emptyList();for(MediaItem.Album a:albums)if(a.key.equals(albumKey)){scope=a.items;break;}}
            visible=new ArrayList<>();
            for(MediaItem m:scope){if(tab==2&&!favs.contains(m.key()))continue;if(filter==1&&m.video||filter==2&&!m.video)continue;
                if(!q.isEmpty()&&!m.searchText.contains(q))continue;visible.add(m);}
            Comparator<MediaItem> comparator=ordering==1?(a,b)->Long.compare(a.added,b.added):ordering==2?(a,b)->Long.compare(b.date,a.date):ordering==3?(a,b)->a.sortName.compareTo(b.sortName):ordering==4?(a,b)->Long.compare(b.size,a.size):(a,b)->Long.compare(b.added,a.added);
            if(ordering!=0)visible.sort(comparator);datasets.put(key,visible);
        }
        boolean albumRoot=tab==1&&albumKey==null&&!trash;
        if(albumRoot){
            List<MediaItem.Album> filtered=new ArrayList<>();for(MediaItem.Album a:albums)if(q.isEmpty()||Ui.normalize(a.name+" "+a.path).contains(q))filtered.add(a);
            adapter.albums(filtered);count.setText(filtered.size()+" "+(filtered.size()==1?"álbum":"álbuns"));filterBar.setVisibility(View.GONE);setEmpty(filtered.isEmpty(),"albums","Seus álbuns, aqui","Toque em + para criar uma pasta e organizar suas mídias.",q);
        }else{
            adapter.setSelection(selected,selecting,false);
            adapter.media(visible,prefs.bool("group_dates",true)&&(ordering==0||ordering==1));filterBar.setVisibility(View.VISIBLE);updateFilters();
            count.setText(visible.size()+" "+(visible.size()==1?"item":"itens")+(ordering==0?" · mais recentes":""));
            String emptyTitle=trash?"Lixeira vazia":tab==2?"Guarde seus favoritos":"Ainda sem mídias";
            String emptyText=trash?"Os arquivos enviados para a lixeira aparecem aqui.":tab==2?"Toque no coração ao abrir uma foto ou um vídeo.":albumKey!=null?"Copie ou mova suas fotos e vídeos para esta pasta.":"As fotos e vídeos adicionados ao aparelho aparecerão aqui.";
            setEmpty(visible.isEmpty(),trash?"trash":tab==2?"heart":"gallery",emptyTitle,emptyText,q);
        }
        title.setText(currentTitle());
        if(scrollIndicator!=null)scrollIndicator.changed();
        subtitle.setText(trash?"Os arquivos ficam na lixeira até você restaurar ou apagar permanentemente.":selecting?"Toque nas mídias para selecionar":tab==1&&albumKey==null?albums.size()+" álbuns · organize do seu jeito":
            albumKey!=null?visible.size()+" "+(visible.size()==1?"arquivo nesta pasta":"arquivos nesta pasta"):
            tab==2?visible.size()+" "+(visible.size()==1?"lembrança favorita":"lembranças favoritas"):images+" fotos · "+videos+" vídeos");
    }
    private void updateFilters(){
        filterBar.removeAllViews();String[] labels={"Tudo","Fotos","Vídeos"};
        for(int i=0;i<3;i++){
            final int id=i;TextView chip=Ui.bold(this,labels[i],13,filter==i?Ui.BG:Ui.MUTED);
            chip.setGravity(Gravity.CENTER);chip.setPadding(Ui.dp(this,18),Ui.dp(this,11),Ui.dp(this,18),Ui.dp(this,11));
            chip.setBackground(Ui.ripple(this,filter==i?Ui.ACCENT:Ui.SURFACE,22));chip.setMinHeight(Ui.dp(this,42));
            chip.setOnClickListener(v->{filter=id;update();list.setSelection(0);});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.rightMargin=Ui.dp(this,8);filterBar.addView(chip,p);
        }
    }
    private void setEmpty(boolean show,String icon,String heading,String text,String q) {
        empty.setVisibility(show?View.VISIBLE:View.GONE);list.setVisibility(show?View.GONE:View.VISIBLE);empty.removeAllViews();
        if(!show)return;
        ImageView illustration=Ui.icon(this,q.isEmpty()?icon:"search",Ui.ACCENT,48);illustration.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));illustration.setBackground(Ui.bg(Ui.SOFT,26,this));empty.addView(illustration,new LinearLayout.LayoutParams(Ui.dp(this,76),Ui.dp(this,76)));
        TextView h=Ui.bold(this,!loaded?"Carregando sua biblioteca":!q.isEmpty()?"Nenhum resultado":heading,21,Ui.TEXT);h.setGravity(Gravity.CENTER);h.setPadding(0,Ui.dp(this,20),0,Ui.dp(this,10));empty.addView(h);
        TextView t=Ui.text(this,error!=null?error:!q.isEmpty()?"Tente outro nome, pasta ou data.":text,14,Ui.MUTED);t.setGravity(Gravity.CENTER);t.setLineSpacing(Ui.dp(this,5),1);empty.addView(t);
        if(albumKey!=null&&q.isEmpty()){
            TextView back=Ui.button(this,"Escolher mídias",()->{tab=0;albumKey=null;build();Ui.toast(this,"Segure uma mídia para selecionar e mover para a pasta.");});
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.topMargin=Ui.dp(this,24);empty.addView(back,bp);
        }
    }
    @Override public void open(MediaItem m) {
        String session=ViewerSession.start(this,visible);Bundle scope=new Bundle();scope.putInt("tab",tab);scope.putInt("filter",filter);scope.putInt("sort",prefs.integer("sort",0));scope.putString("album",albumKey);scope.putString("query",query);scope.putBoolean("trash",trash);
        Intent intent=new Intent(this,ViewerActivity.class);intent.setData(m.uri);intent.putExtra("video",m.video);intent.putExtra("trash",trash);intent.putExtra("session",session);intent.putExtra("scope",scope);startActivity(intent);
    }
    @Override public void select(MediaItem m){
        boolean initial=!selecting;selecting=true;
        if(!selected.remove(m.key()))selected.add(m.key());
        if(initial)build();else{title.setText(currentTitle());adapter.setSelection(selected,true);}
    }
    private void selectAll(){
        boolean allSelected=!visible.isEmpty();for(MediaItem m:visible)allSelected&=selected.contains(m.key());
        for(MediaItem m:visible){if(allSelected)selected.remove(m.key());else selected.add(m.key());}
        title.setText(currentTitle());adapter.setSelection(selected,true);
    }
    private List<MediaItem> chosen(){List<MediaItem> items=new ArrayList<>();for(MediaItem m:all)if(selected.contains(m.key()))items.add(m);return items;}
    private void leaveSelection(){selecting=false;selected.clear();build();}
    private LinearLayout selectionTools(){
        LinearLayout bar=Ui.row(this);bar.setPadding(Ui.dp(this,12),Ui.dp(this,6),Ui.dp(this,12),Ui.dp(this,8));bar.setBackgroundColor(Ui.SURFACE);
        if(trash){tool(bar,"restore","Restaurar",()->actions.restore(chosen()));tool(bar,"trash","Excluir",()->actions.delete(chosen(),true));}
        else {
            tool(bar,"share","Compartilhar",()->actions.share(chosen()));
            tool(bar,"heart","Favoritar",()->{
                List<MediaItem> items=chosen();boolean allFavorite=!items.isEmpty();for(MediaItem m:items)allFavorite&=prefs.favorite(m.key());
                for(MediaItem m:items)if(prefs.favorite(m.key())==allFavorite)prefs.toggleFavorite(m.key());
                Ui.toast(this,allFavorite?"Removidos dos favoritos.":"Adicionados aos favoritos.");leaveSelection();
            });
            tool(bar,"folder","Organizar",()->new Ui.Sheet(this,"Organizar",chosen().size()+" arquivos")
                .item("move","Mover para uma pasta",null,()->actions.organize(chosen(),true)).item("copy","Copiar para uma pasta",null,()->actions.organize(chosen(),false)).show());
            tool(bar,"trash","Excluir",()->actions.delete(chosen(),false));
        }return bar;
    }
    private void tool(LinearLayout bar,String icon,String text,Runnable run){
        LinearLayout b=Ui.column(this);b.setGravity(Gravity.CENTER);b.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,8));b.setBackground(Ui.ripple(this,Color.TRANSPARENT,16));b.addView(Ui.icon(this,icon,Ui.ACCENT,29));b.setMinimumHeight(Ui.dp(this,56));b.setContentDescription(text);
        b.setOnClickListener(v->{if(chosen().isEmpty())Ui.toast(this,"Selecione pelo menos um arquivo.");else run.run();});bar.addView(b,new LinearLayout.LayoutParams(0,-2,1));
    }
    @Override public void album(MediaItem.Album album){albumKey=album.key;query="";filter=0;build();list.setSelection(0);}
    @Override public void albumMenu(MediaItem.Album a){
        Ui.Sheet sheet=new Ui.Sheet(this,a.name,a.path.isEmpty()?a.items.size()+" itens":a.path);
        if(!a.items.isEmpty())sheet.item("share","Compartilhar álbum",a.items.size()+" arquivos",()->actions.share(a.items));
        sheet.item("hide","Ocultar álbum","Pode ser exibido novamente nos Ajustes",()->{
            prefs.hide(a.key,true);if(a.key.equals(albumKey))albumKey=null;update();Ui.toast(this,"Álbum ocultado.");
        });
        sheet.item("trash","Excluir álbum e arquivos",a.items.isEmpty()?"Excluir esta pasta vazia":a.items.size()+" arquivos serão excluídos permanentemente",()->actions.deleteAlbum(a));
        sheet.show();
    }
    private void sort(){
        String[] options={"Mais recentes","Mais antigos","Data da captura","Nome · A a Z","Maior tamanho"};
        new AlertDialog.Builder(this).setTitle("Ordenar mídias").setSingleChoiceItems(options,prefs.integer("sort",0),(d,i)->{prefs.set("sort",i);d.dismiss();update();list.setSelection(0);}).setNegativeButton("Cancelar",null).show();
    }
    private void menu(){
        Ui.Sheet sheet=new Ui.Sheet(this,"Sua biblioteca",null);
        sheet.item("refresh","Atualizar agora",null,()->{repo.refresh(true);Ui.toast(this,"Atualizando biblioteca…");});
        if(!(tab==1&&albumKey==null))sheet.item("select","Selecionar mídias","Compartilhar e organizar vários arquivos",()->{selecting=true;build();});
        sheet.item("plus","Criar nova pasta",null,()->actions.newFolder(()->repo.refresh(true)));
        sheet.item("grid","Tamanho da grade",prefs.integer("columns",3)+" colunas",()->{
            String[] opts={"2 colunas","3 colunas","4 colunas","5 colunas"};new AlertDialog.Builder(this).setTitle("Grade de mídias").setSingleChoiceItems(opts,prefs.integer("columns",3)-2,(d,i)->{prefs.set("columns",i+2);d.dismiss();build();}).setNegativeButton("Cancelar",null).show();
        });
        if(!trash)sheet.item("trash","Lixeira","Restaurar fotos e vídeos excluídos",()->{trash=true;tab=0;albumKey=null;query="";loaded=false;all=new ArrayList<>();repo.trash(true);build();});
        sheet.item("settings","Ajustes",null,this::settings);sheet.show();
    }
    @Override public void onBackPressed(){
        if(selecting){leaveSelection();return;}
        if(albumKey!=null){albumKey=null;query="";build();return;}
        if(trash){switchTab(0);return;}
        if(!query.isEmpty()){query="";update();return;}
        if(isTaskRoot())finishAndRemoveTask();else super.onBackPressed();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==720&&result==RESULT_OK&&data!=null){tab=1;trash=false;filter=0;query="";albumKey=data.getStringExtra("album");repo.trash(false);build();list.setSelection(0);}else actions.onResult(request,result,data);}
}
