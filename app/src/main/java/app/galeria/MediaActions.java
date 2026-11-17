package app.galeria;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MediaActions {
    public interface Listener { void changed(Set<String> removed); }
    private static final int ACCESS=612;
    private final Activity activity;private final Prefs prefs;private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Runnable afterAccess;
    private Dialog progress;
    public MediaActions(Activity a,Listener l){activity=a;prefs=new Prefs(a);listener=l;}
    private boolean alive(){return !activity.isFinishing()&&!activity.isDestroyed();}
    public void close(){worker.shutdownNow();if(progress!=null)progress.dismiss();}
    public void share(List<MediaItem> items) {
        if(items.isEmpty())return;
        ArrayList<Uri> uris=new ArrayList<>();boolean images=true,videos=true;
        for(MediaItem m:items){uris.add(m.uri);images&=!m.video;videos&=m.video;}
        Intent intent=new Intent(items.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE);
        intent.setType(images?"image/*":videos?"video/*":"*/*");
        if(items.size()==1)intent.putExtra(Intent.EXTRA_STREAM,uris.get(0));else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
        ClipData clip=ClipData.newUri(activity.getContentResolver(),"Mídias",uris.get(0));
        for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));
        intent.setClipData(clip);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{activity.startActivity(Intent.createChooser(intent,"Compartilhar"));}catch(Exception e){Ui.toast(activity,"Nenhum aplicativo disponível para compartilhar.");}
    }
    public void details(MediaItem m) {
        Ui.Sheet s=new Ui.Sheet(activity,"Informações",m.name);
        detail(s,"Tipo",m.mime);detail(s,"Tamanho",Ui.bytes(m.size));
        if(m.width>0)detail(s,"Dimensões",m.width+" × "+m.height+" px");
        if(m.video)detail(s,"Duração",Ui.duration(m.duration));
        detail(s,"Captura",Ui.date(m.date,"dd/MM/yyyy 'às' HH:mm"));
        detail(s,"Adicionado",Ui.date(m.added,"dd/MM/yyyy 'às' HH:mm"));
        detail(s,"Pasta",m.path.isEmpty()?m.album:m.path);s.show();
    }
    private void detail(Ui.Sheet s,String label,String value){
        LinearLayout r=Ui.column(activity);r.setPadding(0,Ui.dp(activity,10),0,Ui.dp(activity,10));
        r.addView(Ui.text(activity,label,12,Ui.MUTED));TextView t=Ui.text(activity,value,15,Ui.TEXT);t.setTextIsSelectable(true);t.setPadding(0,Ui.dp(activity,5),0,0);r.addView(t);s.content.addView(r);
    }
    static boolean validName(String name){
        return !name.isEmpty() && name.length()<=160 && !name.equals(".") && !name.equals("..") && !java.util.regex.Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]").matcher(name).find();
    }
    public void rename(MediaItem m) {
        EditText input=new EditText(activity);input.setSingleLine(true);input.setText(m.name);input.setSelectAllOnFocus(true);
        LinearLayout box=Ui.column(activity);box.setPadding(Ui.dp(activity,22),Ui.dp(activity,4),Ui.dp(activity,22),0);box.addView(input);
        AlertDialog d=new AlertDialog.Builder(activity).setTitle("Renomear arquivo").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=input.getText().toString().trim();
            if(!validName(name)){input.setError("Digite um nome válido, sem / ou caracteres especiais.");return;}
            int ext=m.name.lastIndexOf('.');
            if(ext>=0 && name.lastIndexOf('.')<0)name+=m.name.substring(ext);
            final String target=name;d.dismiss();
            direct(()->write(()->DirectMedia.rename(activity,m,target),"Arquivo renomeado."));
        }));d.show();
    }
    public void delete(List<MediaItem> source,boolean permanent) {
        final List<MediaItem> items=new ArrayList<>(source);if(items.isEmpty())return;
        Runnable operation=()->direct(()->mutate(items,permanent?"Excluindo arquivos":"Enviando para a lixeira",permanent?0:1));
        if(permanent)new AlertDialog.Builder(activity).setTitle("Excluir permanentemente?")
            .setMessage(items.size()+" arquivo(s) serão apagados. Esta ação não pode ser desfeita.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->operation.run()).show();
        else operation.run();
    }
    public void restore(List<MediaItem> source) {
        List<MediaItem> items=new ArrayList<>(source);if(!items.isEmpty())direct(()->mutate(items,"Restaurando arquivos",2));
    }
    private void mutate(List<MediaItem> items,String title,int kind){
        showProgress(title);worker.execute(()->{
            Set<String> removed=new HashSet<>();int failed=0;String error=null;
            for(MediaItem m:items)try{
                if(kind==0)TrashStore.get(activity).remove(m);
                else if(kind==1)TrashStore.get(activity).put(m);
                else TrashStore.get(activity).restore(m);
                removed.add(m.key());
            }catch(Exception e){failed++;error=e.getMessage();}
            final int failures=failed;final String message=error;main.post(()->{hideProgress();if(!alive())return;listener.changed(removed);
                Ui.toast(activity,failures>0?removed.size()+" concluído(s); "+failures+" não concluído(s). "+(message==null?"Verifique o acesso e o espaço disponível.":message):kind==0?"Arquivos excluídos.":kind==1?"Arquivos enviados para a lixeira.":"Arquivos restaurados.");
            });
        });
    }
    public boolean onResult(int request,int result,Intent data) {if(request==ACCESS){accessReturned();return true;}return false;}
    private interface Work {void run()throws Exception;}
    private void write(Work work,String message){
        showProgress("Organizando arquivo");worker.execute(()->{
            String error=null;try{work.run();}catch(Exception e){error=e.getMessage();}
            final String failure=error;main.post(()->{hideProgress();if(!alive())return;listener.changed(Collections.emptySet());Ui.toast(activity,failure==null?message:failure);});
        });
    }
    private void accessReturned(){
        Runnable next=afterAccess;afterAccess=null;
        if(FileAccess.direct(activity)){if(next!=null&&alive())next.run();}
        else if(alive())Ui.toast(activity,"A operação precisa da autorização de acesso aos arquivos. Seus originais foram mantidos.");
    }
    public boolean onPermissionsResult(int request){if(request!=ACCESS)return false;accessReturned();return true;}
    private void direct(Runnable operation){
        if(FileAccess.direct(activity)){operation.run();return;}
        new AlertDialog.Builder(activity).setTitle("Acesso direto aos arquivos")
            .setMessage("Autorize o acesso aos arquivos uma vez nos Ajustes do Android. Ao voltar, a operação continuará automaticamente. Esse acesso permite mover, renomear, excluir e restaurar sem pedir confirmação para cada foto ou vídeo.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Autorizar",(d,w)->{
                afterAccess=operation;
                try{FileAccess.request(activity,ACCESS);}catch(Exception e){afterAccess=null;Ui.toast(activity,"Abra as permissões de acesso a arquivos nos Ajustes do Android.");}
            }).show();
    }
    public void newFolder(Runnable done){folderInput(Collections.emptyList(),false,done);}
    public void organize(List<MediaItem> source,boolean move){
        if(source.isEmpty())return;
        List<MediaItem> items=new ArrayList<>(source);
        AlbumPicker picker=new AlbumPicker(activity,prefs,items.size(),move,
            album->direct(()->sendToAlbum(items,album,move)),
            ()->folderInput(items,move,null));
        picker.show();
        worker.execute(()->{
            try{
                List<MediaItem.Album> albums=MediaRepo.albums(MediaRepo.query(activity,false),prefs);
                main.post(()->picker.populate(albums));
            }catch(Exception e){main.post(picker::failure);}
        });
    }
    private void sendToAlbum(List<MediaItem> items,MediaItem.Album album,boolean move){
        showProgress(move?"Movendo arquivos":"Copiando arquivos");
        worker.execute(()->{
            String message;try{
                FolderTransfers.Result result=FolderTransfers.send(activity,items,album,move);
                if(result.completed>0)prefs.rememberAlbum(album.key);
                message=result.completed+" "+(result.completed==1?"arquivo ":"arquivos ")+(move?"movido":"copiado")+(result.completed==1?".":"s.");
                if(result.failed>0)message+=" "+result.failed+" não concluído(s). "+(result.error==null?"Verifique o acesso e o espaço disponível.":result.error);
            }catch(Exception e){message=e.getMessage()==null?"Não foi possível usar este álbum.":e.getMessage();}
            final String status=message;
            main.post(()->{hideProgress();if(!alive())return;listener.changed(Collections.emptySet());Ui.toast(activity,status);});
        });
    }
    private void folderInput(List<MediaItem> items,boolean move,Runnable done){
        boolean sending=!items.isEmpty();EditText input=new EditText(activity);input.setSingleLine(true);input.setTextColor(Ui.TEXT);input.setHint("Nome do álbum");input.setSelectAllOnFocus(true);
        LinearLayout box=Ui.column(activity);box.setPadding(Ui.dp(activity,22),Ui.dp(activity,8),Ui.dp(activity,22),0);
        TextView hint=Ui.text(activity,"Crie um álbum para organizar suas mídias. Se ele já existir, será reutilizado.",13,Ui.MUTED);hint.setLineSpacing(Ui.dp(activity,4),1);box.addView(hint);box.addView(input,new LinearLayout.LayoutParams(-1,Ui.dp(activity,56)));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(sending?"Criar álbum e "+(move?"mover":"copiar"):"Criar um álbum")
            .setView(box).setNegativeButton("Cancelar",null).setPositiveButton(sending?(move?"Mover":"Copiar"):"Criar",null).create();
        dialog.setOnShowListener(d->{input.requestFocus();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String name=input.getText().toString().trim();
                if(!validName(name)||name.length()>80||name.startsWith(".")){input.setError("Nome válido, sem / ou caracteres especiais, com até 80 caracteres.");return;}
                dialog.dismiss();direct(()->sendToFolder(items,name,move,done));
            });});dialog.show();
    }
    private void sendToFolder(List<MediaItem> items,String name,boolean move,Runnable done){
        showProgress(items.isEmpty()?"Criando pasta":move?"Movendo arquivos":"Copiando arquivos");
        worker.execute(()->{String message;try{
                if(items.isEmpty()){File folder=FileAccess.folder(name);FileAccess.remember(activity,folder);message="Pasta pronta para usar.";}
                else{FolderTransfers.Result result=FolderTransfers.send(activity,items,name,move);
                    if(result.completed>0){String path=android.os.Build.VERSION.SDK_INT>=29?FileAccess.ROOT+name+"/":FileAccess.folder(name).getAbsolutePath()+"/";
                        prefs.rememberAlbum((android.os.Build.VERSION.SDK_INT>=29?"external_primary":"external")+":"+path);}
                    message=result.completed+" "+(result.completed==1?"arquivo ":"arquivos ")+(move?"movido":"copiado")+(result.completed==1?".":"s.");
                    if(result.failed>0)message+=" "+result.failed+" não concluído(s). "+(result.error==null?"Verifique o acesso e o espaço disponível.":result.error);
                }
            }catch(Exception e){message=e.getMessage()==null?"Não foi possível organizar os arquivos.":e.getMessage();}
            final String status=message;main.post(()->{hideProgress();if(!alive())return;listener.changed(Collections.emptySet());Ui.toast(activity,status);if(done!=null)done.run();});
        });
    }
    public void deleteAlbum(MediaItem.Album album){
        new AlertDialog.Builder(activity).setTitle("Excluir álbum e arquivos?")
            .setMessage("Excluir permanentemente “"+album.name+"” e "+album.items.size()+" arquivos? Esta ação não pode ser desfeita.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->direct(()->deleteAlbumNow(album))).show();
    }
    private void deleteAlbumNow(MediaItem.Album album){
        showProgress("Excluindo álbum");worker.execute(()->{
            Set<String> removed=new HashSet<>();File directory=null;int failed=0;boolean folderRemoved=false;
            for(MediaItem m:new ArrayList<>(album.items))try{File file=FileAccess.source(activity,m);directory=file.getParentFile();DirectMedia.delete(activity,m);removed.add(m.key());}catch(Exception e){failed++;}
            try{
                if(directory==null&&album.folder!=null&&"file".equals(Uri.parse(album.folder.uri).getScheme()))directory=new File(Uri.parse(album.folder.uri).getPath());
                if(directory==null&&!album.path.isEmpty()){
                    File volume=album.volume.equals("external_primary")||album.volume.equals("external")?Environment.getExternalStorageDirectory():new File("/storage",album.volume);
                    directory=Build.VERSION.SDK_INT>=29?new File(volume,album.path):new File(album.path);
                }
                if(directory!=null&&directory.isDirectory())folderRemoved=directory.delete();
                if(failed==0){if(album.folder!=null)prefs.removeFolder(album.folder.uri);prefs.hide(album.key,false);}
            }catch(Exception ignored){}
            final int failures=failed;final boolean kept=directory!=null&&!folderRemoved&&directory.exists();main.post(()->{hideProgress();if(!alive())return;listener.changed(removed);
                Ui.toast(activity,failures>0?removed.size()+" arquivos excluídos; "+failures+" não puderam ser excluídos.":kept?"Mídias excluídas. A pasta contém outros arquivos e foi mantida.":"Álbum e arquivos excluídos.");});
        });
    }
    private void showProgress(String title) {
        if(!alive())return;
        if(progress!=null)progress.dismiss();progress=new Dialog(activity);progress.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout l=Ui.column(activity);l.setPadding(Ui.dp(activity,28),Ui.dp(activity,28),Ui.dp(activity,28),Ui.dp(activity,28));l.setBackground(Ui.bg(Ui.SURFACE,22,activity));l.setGravity(Gravity.CENTER);
        ProgressBar bar=new ProgressBar(activity);l.addView(bar,new LinearLayout.LayoutParams(Ui.dp(activity,40),Ui.dp(activity,40)));
        TextView t=Ui.bold(activity,title,17,Ui.TEXT);t.setPadding(0,Ui.dp(activity,20),0,0);l.addView(t);progress.setContentView(l);progress.setCancelable(false);
        if(progress.getWindow()!=null)progress.getWindow().setBackgroundDrawableResource(android.R.color.transparent);progress.show();
    }
    private void hideProgress(){if(progress!=null){progress.dismiss();progress=null;}}
}
