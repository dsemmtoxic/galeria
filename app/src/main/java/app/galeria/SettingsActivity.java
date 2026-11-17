package app.galeria;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class SettingsActivity extends SecureActivity {
    private Prefs prefs;private LinearLayout content;
    @Override public void onCreate(Bundle state){super.onCreate(state);prefs=new Prefs(this);build();}
    private void build(){
        Ui.theme(this);Ui.bars(this,Ui.BG);LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);setContentView(root);
        LinearLayout header=Ui.row(this);header.setPadding(Ui.dp(this,10),Ui.dp(this,18),Ui.dp(this,20),Ui.dp(this,12));header.addView(Ui.iconButton(this,"back","Voltar",this::finish));
        TextView title=Ui.bold(this,"Ajustes",29,Ui.TEXT);title.setPadding(Ui.dp(this,7),0,0,0);header.addView(title);root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
        content=Ui.column(this);content.setPadding(Ui.dp(this,20),Ui.dp(this,5),Ui.dp(this,20),Ui.dp(this,30));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout brand=Ui.row(this);brand.setPadding(Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18));brand.setBackground(Ui.bg(Ui.SOFT,22,this));
        ImageView logo=new ImageView(this);logo.setImageResource(R.mipmap.ic_launcher);brand.addView(logo,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,62)));
        LinearLayout words=Ui.column(this);words.setPadding(Ui.dp(this,15),0,0,0);words.addView(Ui.bold(this,"Galeria",20,Ui.TEXT));
        TextView tag=Ui.text(this,"Do seu jeito, em cada detalhe.",12,Ui.MUTED);tag.setPadding(0,Ui.dp(this,7),0,0);words.addView(tag);brand.addView(words);content.addView(brand);
        section("APARÊNCIA");
        accent();
        toggle("palette","Preto profundo","Fundo preto para telas OLED","oled",false,()->{int y=scroll.getScrollY();build();scrollRestore(y);});
        choice("grid","Grade de mídias",prefs.integer("columns",3)+" colunas",new String[]{"2 colunas","3 colunas","4 colunas","5 colunas"},prefs.integer("columns",3)-2,i->prefs.set("columns",i+2));
        toggle("calendar","Agrupar por data","Separa a grade por dia de adição","group_dates",true,null);
        toggle("info","Nome nas miniaturas","Exibe o nome sobre as fotos","names",false,null);
        String[] sorts={"Mais recentes","Mais antigos","Data da captura","Nome · A a Z","Maior tamanho"};
        choice("sort","Ordenação padrão",sorts[Math.min(4,prefs.integer("sort",0))],sorts,prefs.integer("sort",0),i->prefs.set("sort",i));
        section("PRIVACIDADE");
        appLock();
        section("VISUALIZAÇÃO");
        toggle("sun","Brilho máximo ao abrir","Aplica apenas ao visualizar uma foto ou vídeo","max_brightness",false,null);
        toggle("rotate","Rotação automática","Respeita o bloqueio de rotação do Android","auto_rotate",true,null);
        toggle("gesture","Navegar deslizando","Deslize na horizontal para trocar de mídia","swipe_nav",true,null);
        toggle("fullscreen","Zoom com pinça","Ampliar fotos e vídeos com dois dedos","pinch",true,null);
        toggle("photo","Duplo toque nas fotos","Alterna entre tamanho normal e zoom","photo_double",true,null);
        section("GESTOS");
        toggle("volume","Volume à esquerda","Deslize para cima ou para baixo","volume_gesture",true,null);
        toggle("sun","Brilho à direita","Deslize para cima ou para baixo","brightness_gesture",true,null);
        toggle("video","Duplo toque nos vídeos","Esquerda volta; direita avança","video_double",true,null);
        int skip=prefs.integer("skip_seconds",5);int[] skips={5,10,15,30};
        choice("speed","Salto no duplo toque",skip+" segundos",new String[]{"5 segundos","10 segundos","15 segundos","30 segundos"},indexOf(skips,skip),i->prefs.set("skip_seconds",skips[i]));
        TextView hint=Ui.text(this,"Quando a foto estiver ampliada, arraste para explorar a imagem. Os gestos de brilho e volume ficam disponíveis no tamanho normal.",12,Ui.DIM);
        hint.setLineSpacing(Ui.dp(this,4),1);hint.setPadding(Ui.dp(this,4),Ui.dp(this,12),Ui.dp(this,4),Ui.dp(this,8));content.addView(hint);
        section("VÍDEOS");
        toggle("play","Reprodução automática","Começa ao abrir um vídeo","autoplay",true,null);
        toggle("mute","Começar sem som","Pode ativar o áudio no visualizador","muted",false,null);
        toggle("loop","Repetir vídeo","Recomeça quando chega ao final","loop_video",false,null);
        toggle("restore","Lembrar posição","Continua de onde você parou","remember_position",true,null);
        toggle("sun","Manter tela ligada","Enquanto um vídeo está tocando","keep_screen",true,null);
        section("BIBLIOTECA");
        TextView cache=Ui.text(this,"Miniaturas e álbuns ficam em cache. As mudanças são agrupadas antes de atualizar a lista; a verificação de segurança ocorre a cada minuto.",12,Ui.DIM);cache.setLineSpacing(Ui.dp(this,4),1);cache.setPadding(Ui.dp(this,4),Ui.dp(this,10),Ui.dp(this,4),Ui.dp(this,14));content.addView(cache);
        section("ORGANIZAÇÃO");
        action("hide","Álbuns ocultos",prefs.hidden().size()+" "+(prefs.hidden().size()==1?"álbum oculto":"álbuns ocultos"),this::hidden);
        action("folder","Organização direta",FileAccess.direct(this)?"Autorizado · pastas em Pictures/Gallery/owner":"Autorizar acesso para mover e copiar sem confirmações repetidas",()->{try{FileAccess.request(this,612);}catch(Exception e){Ui.toast(this,"Abra o acesso aos arquivos nas permissões do Android.");}});
        action("lock","Acesso às mídias","Gerenciar as permissões do aplicativo",()->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))));
        section("SOBRE");
        action("gesture","Guia de gestos","Aprenda os controles do visualizador",this::guide);
        TextView about=Ui.text(this,"Galeria 1.3.0\nSuas fotos e vídeos permanecem no aparelho.\nSem anúncios ou conta.",12,Ui.DIM);about.setGravity(Gravity.CENTER);about.setLineSpacing(Ui.dp(this,6),1);about.setPadding(0,Ui.dp(this,25),0,Ui.dp(this,12));content.addView(about);
    }
    private void appLock(){
        LinearLayout row=setting("lock","Bloquear ao abrir","Digital, PIN, senha ou padrão apenas ao abrir uma nova sessão. Retornar do segundo plano não pede novamente.");Switch toggle=new Switch(this);toggle.setChecked(AppLock.enabled(this));toggle.setShowText(false);toggle.setContentDescription("Bloquear ao abrir");toggle.setThumbTintList(ColorStateList.valueOf(toggle.isChecked()?Ui.ACCENT:Ui.MUTED));toggle.setTrackTintList(ColorStateList.valueOf(Ui.DIM));row.addView(toggle);
        row.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));toggle.setOnCheckedChangeListener((button,checked)->{
            if(!checked){prefs.set("app_lock",false);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);toggle.setThumbTintList(ColorStateList.valueOf(Ui.MUTED));return;}
            toggle.setEnabled(false);row.setEnabled(false);
            if(!AppLock.deviceSecure(this)){new AlertDialog.Builder(this).setTitle("Configure o bloqueio do celular").setMessage("Adicione uma digital, PIN, senha ou padrão nos Ajustes do Android antes de proteger a Galeria.").setPositiveButton("OK",null).show();build();return;}
            enableLock(this::build);
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==612)build();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==612)build();}
    private void scrollRestore(int y){content.post(()->{View parent=(View)content.getParent();if(parent instanceof ScrollView)((ScrollView)parent).scrollTo(0,y);});}
    private static int indexOf(int[] values,int value){for(int i=0;i<values.length;i++)if(values[i]==value)return i;return 0;}
    private void section(String name){
        TextView t=Ui.bold(this,name,10,Ui.ACCENT);t.setLetterSpacing(.13f);t.setPadding(Ui.dp(this,3),Ui.dp(this,28),0,Ui.dp(this,10));content.addView(t);
    }
    private LinearLayout setting(String icon,String title,String subtitle){
        LinearLayout r=Ui.row(this);r.setPadding(Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16));r.setMinimumHeight(Ui.dp(this,76));r.setBackground(Ui.ripple(this,Ui.SURFACE,17));
        ImageView image=Ui.icon(this,icon,Ui.ACCENT,23);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(Ui.dp(this,23),Ui.dp(this,23));ip.rightMargin=Ui.dp(this,16);r.addView(image,ip);
        LinearLayout col=Ui.column(this);TextView label=Ui.bold(this,title,14,Ui.TEXT);label.setMaxLines(2);col.addView(label);
        if(subtitle!=null){TextView sub=Ui.text(this,subtitle,12,Ui.MUTED);sub.setPadding(0,Ui.dp(this,6),Ui.dp(this,8),0);sub.setLineSpacing(Ui.dp(this,3),1);col.addView(sub);}
        r.addView(col,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=Ui.dp(this,5);content.addView(r,rp);return r;
    }
    private void toggle(String icon,String title,String subtitle,String key,boolean def,Runnable after){
        LinearLayout r=setting(icon,title,subtitle);Switch s=new Switch(this);s.setChecked(prefs.bool(key,def));s.setShowText(false);s.setThumbTintList(ColorStateList.valueOf(s.isChecked()?Ui.ACCENT:Ui.MUTED));
        s.setTrackTintList(ColorStateList.valueOf(Ui.DIM));s.setContentDescription(title);r.addView(s);
        s.setOnCheckedChangeListener((button,checked)->{prefs.set(key,checked);s.setThumbTintList(ColorStateList.valueOf(checked?Ui.ACCENT:Ui.MUTED));if(after!=null)after.run();});r.setOnClickListener(v->s.setChecked(!s.isChecked()));
    }
    private interface Selected{void select(int index);}
    private void choice(String icon,String title,String subtitle,String[] options,int chosen,Selected selected){
        action(icon,title,subtitle,()->new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(options,Math.max(0,Math.min(options.length-1,chosen)),(d,i)->{selected.select(i);d.dismiss();int y=((ScrollView)content.getParent()).getScrollY();build();scrollRestore(y);}).setNegativeButton("Cancelar",null).show());
    }
    private void action(String icon,String title,String subtitle,Runnable action){
        LinearLayout r=setting(icon,title,subtitle);r.addView(Ui.icon(this,"chevron",Ui.DIM,18));r.setOnClickListener(v->action.run());
    }
    private void accent(){
        LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,16),Ui.dp(this,17),Ui.dp(this,16),Ui.dp(this,18));box.setBackground(Ui.bg(Ui.SURFACE,17,this));box.addView(Ui.bold(this,"Cor de destaque",14,Ui.TEXT));
        LinearLayout colors=Ui.row(this);colors.setPadding(0,Ui.dp(this,17),0,0);int[] values={0xFFB497FF,0xFF9979EE,0xFFD6ACFF,0xFFEE96C6,0xFF90BBFF};
        for(int value:values){
            FrameLayout cell=new FrameLayout(this);ImageView dot=Ui.icon(this,value==Ui.ACCENT?"check":"",value==Ui.ACCENT?0xFF21152F:value,24);dot.setPadding(Ui.dp(this,9),Ui.dp(this,9),Ui.dp(this,9),Ui.dp(this,9));dot.setBackground(Ui.bg(value,25,this));
            FrameLayout.LayoutParams d=new FrameLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44),Gravity.CENTER);cell.addView(dot,d);cell.setContentDescription("Selecionar cor de destaque");
            cell.setOnClickListener(v->{prefs.set("accent",value);int y=((ScrollView)content.getParent()).getScrollY();build();scrollRestore(y);});colors.addView(cell,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        }box.addView(colors);content.addView(box);
    }
    private void hidden(){
        Set<String> keys=prefs.hidden();Ui.Sheet sheet=new Ui.Sheet(this,"Álbuns ocultos","Toque em um álbum para voltar a exibi-lo.");
        if(keys.isEmpty()){TextView none=Ui.text(this,"Nenhum álbum oculto.",15,Ui.MUTED);none.setPadding(0,Ui.dp(this,20),0,Ui.dp(this,20));sheet.content.addView(none);}
        for(String key:keys){
            String path=key.contains(":")?key.substring(key.indexOf(':')+1):key;String trim=path.replaceAll("/+$","");String name=trim.substring(trim.lastIndexOf('/')+1);
            sheet.item("eye",name,path,()->{prefs.hide(key,false);Ui.toast(this,"Álbum exibido novamente.");int y=((ScrollView)content.getParent()).getScrollY();build();scrollRestore(y);});
        }sheet.show();
    }
    private void guide(){
        Ui.Sheet sheet=new Ui.Sheet(this,"Guia de gestos",null);
        String[][] tips={
            {"Fotos","Duplo toque para ampliar. Use dois dedos para ajustar o zoom e arraste para explorar a foto."},
            {"Vídeos","Duplo toque à esquerda volta 5 segundos; à direita avança 5 segundos. O intervalo pode ser alterado nos Ajustes."},
            {"Volume e brilho","No tamanho normal, deslize para cima ou para baixo: volume na metade esquerda, brilho na metade direita."},
            {"Navegação","Deslize para os lados para abrir a mídia anterior ou seguinte."},
            {"Controles","Um toque mostra ou oculta os controles. Segure uma miniatura na biblioteca para selecionar várias mídias."}};
        for(String[] tip:tips){TextView h=Ui.bold(this,tip[0],16,Ui.TEXT);h.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,7));sheet.content.addView(h);TextView t=Ui.text(this,tip[1],14,Ui.MUTED);t.setLineSpacing(Ui.dp(this,4),1);sheet.content.addView(t);}sheet.show();
    }
}
