package app.galeria;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Destination chooser: five recent albums with real covers, then device albums. */
public final class AlbumPicker {
    public interface Destination {void selected(MediaItem.Album album);}
    private final Activity activity;
    private final Prefs prefs;
    private final Ui.Sheet sheet;
    private final LinearLayout results;
    private final Destination receiver;
    private final Runnable create;

    public AlbumPicker(Activity a, Prefs p, int count, boolean move, Destination receiver, Runnable create){
        activity=a;prefs=p;this.receiver=receiver;this.create=create;
        sheet=new Ui.Sheet(a,move?"Mover para álbum":"Copiar para álbum",count+ (count==1?" mídia selecionada":" mídias selecionadas"));
        results=Ui.column(a);
        TextView loading=Ui.text(a,"Procurando álbuns do aparelho…",14,Ui.MUTED);
        loading.setPadding(0,Ui.dp(a,18),0,Ui.dp(a,18));results.addView(loading);
        sheet.content.addView(results);
    }
    public void show(){
        sheet.show();Window w=sheet.dialog.getWindow();
        if(w!=null){w.setGravity(Gravity.BOTTOM);w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
            Math.round(activity.getResources().getDisplayMetrics().heightPixels*.83f));}
    }
    public boolean isOpen(){return sheet.dialog.isShowing()&&!activity.isFinishing()&&!activity.isDestroyed();}
    public void failure(){
        if(!isOpen())return;
        results.removeAllViews();TextView warning=Ui.text(activity,"Não foi possível carregar os álbuns. Verifique as permissões de fotos e vídeos.",14,Ui.MUTED);
        warning.setPadding(0,Ui.dp(activity,18),0,Ui.dp(activity,14));results.addView(warning);
        addCreationSection();
    }
    public void populate(List<MediaItem.Album> fromDevice){
        if(!isOpen())return;
        results.removeAllViews();
        List<MediaItem.Album> albums=new ArrayList<>();Set<String> hidden=prefs.hidden();
        for(MediaItem.Album a:fromDevice)if(FileAccess.supportedAlbum(a)&&!hidden.contains(a.key))albums.add(a);
        Map<String,MediaItem.Album> byKey=new HashMap<>();for(MediaItem.Album a:albums)byKey.put(a.key,a);
        List<MediaItem.Album> recent=new ArrayList<>();
        for(String key:prefs.recentAlbums()){
            MediaItem.Album a=byKey.get(key);if(a!=null)recent.add(a);
        }
        heading("Recentes");
        HorizontalScrollView horizontal=new HorizontalScrollView(activity);
        horizontal.setHorizontalScrollBarEnabled(false);horizontal.setClipToPadding(false);
        LinearLayout row=Ui.row(activity);row.setGravity(Gravity.TOP);
        row.addView(tile(null,true,false,()->{sheet.close();create.run();}),tileLayout(105));
        for(MediaItem.Album a:recent)row.addView(tile(a,false,false,()->choose(a)),tileLayout(105));
        horizontal.addView(row);results.addView(horizontal,new LinearLayout.LayoutParams(-1,Ui.dp(activity,160)));
        if(recent.isEmpty()){
            TextView hint=Ui.text(activity,"Os cinco últimos destinos utilizados aparecerão aqui.",12,Ui.MUTED);
            hint.setPadding(0,0,0,Ui.dp(activity,14));results.addView(hint);
        }
        View separator=new View(activity);separator.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,Ui.dp(activity,1));line.topMargin=Ui.dp(activity,8);
        line.bottomMargin=Ui.dp(activity,16);results.addView(separator,line);
        heading("Álbuns do celular");
        if(albums.isEmpty()){
            TextView empty=Ui.text(activity,"Nenhum álbum disponível. Use Criar álbum para adicionar um destino.",14,Ui.MUTED);
            empty.setPadding(0,Ui.dp(activity,8),0,Ui.dp(activity,24));results.addView(empty);return;
        }
        for(int i=0;i<albums.size();i+=3){
            LinearLayout grid=Ui.row(activity);grid.setGravity(Gravity.TOP);
            for(int k=0;k<3;k++){
                int position=i+k;
                if(position<albums.size()){
                    MediaItem.Album a=albums.get(position);
                    grid.addView(tile(a,false,true,()->choose(a)),new LinearLayout.LayoutParams(0,Ui.dp(activity,164),1));
                }else grid.addView(new View(activity),new LinearLayout.LayoutParams(0,Ui.dp(activity,164),1));
            }
            results.addView(grid);
        }
    }
    private LinearLayout.LayoutParams tileLayout(int width){
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(Ui.dp(activity,width),-1);
        layout.rightMargin=Ui.dp(activity,12);return layout;
    }
    private void heading(String value){
        TextView title=Ui.bold(activity,value,16,Ui.TEXT);
        title.setPadding(0,Ui.dp(activity,5),0,Ui.dp(activity,14));results.addView(title);
    }
    private void addCreationSection(){
        results.addView(tile(null,true,false,()->{sheet.close();create.run();}),tileLayout(110));
    }
    private void choose(MediaItem.Album album){sheet.close();receiver.selected(album);}
    private View tile(MediaItem.Album album,boolean creation,boolean device,Runnable action){
        LinearLayout item=Ui.column(activity);item.setPadding(Ui.dp(activity,3),0,Ui.dp(activity,5),Ui.dp(activity,5));
        FrameLayout cover=new FrameLayout(activity);
        int side=device?96:98;
        cover.setBackground(Ui.bg(Ui.CARD,17,activity));cover.setClipToOutline(true);
        ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.addView(image,new FrameLayout.LayoutParams(-1,-1));
        if(creation){
            image.setImageDrawable(new Icons("plus",Ui.ACCENT));image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            image.setPadding(Ui.dp(activity,27),Ui.dp(activity,27),Ui.dp(activity,27),Ui.dp(activity,27));
        }else if(!album.items.isEmpty()){
            ThumbLoader.get(activity).bind(image,album.items.get(0),192);
        }else{
            image.setImageDrawable(new Icons("folder",Ui.ACCENT));image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            image.setPadding(Ui.dp(activity,26),Ui.dp(activity,26),Ui.dp(activity,26),Ui.dp(activity,26));
        }
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,Ui.dp(activity,side));cp.bottomMargin=Ui.dp(activity,7);item.addView(cover,cp);
        String label=creation?"Criar álbum":album.name;
        TextView name=Ui.bold(activity,label,13,Ui.TEXT);name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);item.addView(name);
        if(!creation){TextView count=Ui.text(activity,String.valueOf(album.items.size()),12,Ui.MUTED);
            count.setPadding(0,Ui.dp(activity,3),0,0);item.addView(count);}
        item.setBackground(Ui.ripple(activity,Color.TRANSPARENT,13));item.setFocusable(true);
        item.setContentDescription(creation?"Criar novo álbum":"Selecionar álbum "+album.name);
        item.setOnClickListener(v->action.run());return item;
    }
}
