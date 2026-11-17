package app.galeria;

import android.content.Context;
import android.content.pm.*;
import java.util.*;
import java.util.regex.*;

/** Immutable index assembled on a worker. Suggestions never scan or decode files on the UI thread. */
public final class SearchIndex {
    static final int ALBUM=0,FILE=1,APP=2,QUERY=3;
    static final class Suggestion {
        final int kind;final String title,key;final MediaItem cover;final boolean approximate;
        Suggestion(int k,String t,String id,MediaItem m,boolean fuzzy){kind=k;title=t;key=id;cover=m;approximate=fuzzy;}
        String category(){return kind==ALBUM?"Álbum":kind==APP?"Capturas de tela de apps":"Nome do arquivo";}
    }
    private static final class Group {String title,key,normalized;MediaItem cover;final List<MediaItem> items=new ArrayList<>();}
    private final List<MediaItem> files=new ArrayList<>();private final List<Group> albums=new ArrayList<>(),apps=new ArrayList<>();
    private final Map<String,List<MediaItem>> albumFiles=new HashMap<>(),appFiles=new HashMap<>();
    private static final Pattern PACKAGE=Pattern.compile("_([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+)\\.(?:jpe?g|png|webp|heic)$",Pattern.CASE_INSENSITIVE);
    static String screenshotPackage(MediaItem m){
        if(m.video)return null;String name=m.name.toLowerCase(Locale.ROOT),path=m.path.toLowerCase(Locale.ROOT);
        if(!(name.startsWith("screenshot")||name.startsWith("screen_shot")||name.startsWith("captura")||path.contains("screenshots")||path.contains("capturas")))return null;
        Matcher match=PACKAGE.matcher(m.name);return match.find()?match.group(1):null;
    }
    SearchIndex(Context c,List<MediaItem> source){
        Set<String> hidden=new Prefs(c).hidden();for(MediaItem m:source)if(!m.trashed&&!hidden.contains(m.albumKey()))files.add(m);
        for(MediaItem.Album a:MediaRepo.albums(files,new Prefs(c)))if(!hidden.contains(a.key)){
            Group g=new Group();g.title=a.name;g.key=a.key;g.normalized=Ui.normalize(a.name);g.cover=a.items.isEmpty()?null:a.items.get(0);g.items.addAll(a.items);albums.add(g);albumFiles.put(g.key,g.items);
        }
        Map<String,Group> byPackage=new LinkedHashMap<>();PackageManager pm=c.getPackageManager();
        for(MediaItem m:files){String pkg=screenshotPackage(m);if(pkg==null)continue;Group g=byPackage.get(pkg);
            if(g==null){g=new Group();g.key=pkg;g.title=pkg;try{g.title=String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)));}catch(PackageManager.NameNotFoundException|RuntimeException ignored){}
                g.normalized=Ui.normalize(g.title+" "+pkg);g.cover=m;byPackage.put(pkg,g);appFiles.put(pkg,g.items);}
            g.items.add(m);
        }apps.addAll(byPackage.values());apps.sort((a,b)->a.normalized.compareTo(b.normalized));
    }
    List<Suggestion> suggest(String input){
        String q=Ui.normalize(input.trim());List<Suggestion> result=new ArrayList<>();if(q.isEmpty())return result;
        List<Group> strict=new ArrayList<>(),fuzzy=new ArrayList<>();
        for(Group g:albums){if(g.normalized.contains(q))strict.add(g);else if(close(g.normalized,q))fuzzy.add(g);}
        strict.sort((a,b)->{int x=a.normalized.startsWith(q)?0:1,y=b.normalized.startsWith(q)?0:1;return x!=y?x-y:a.normalized.compareTo(b.normalized);});
        for(Group g:strict){if(result.size()>=60)break;result.add(new Suggestion(ALBUM,g.title,g.key,g.cover,false));}
        for(Group g:apps)if(g.normalized.contains(q)){if(result.size()>=80)break;result.add(new Suggestion(APP,g.title,g.key,g.cover,false));}
        List<MediaItem> names=new ArrayList<>();int scanned=0;for(MediaItem m:files){if((++scanned&255)==0&&Thread.currentThread().isInterrupted())return Collections.emptyList();if(m.sortName.contains(q)){names.add(m);if(names.size()==7)break;}}
        if(!names.isEmpty()){
            result.add(new Suggestion(QUERY,input.trim(),q,names.get(0),false));
            for(MediaItem m:names)result.add(new Suggestion(FILE,m.name,m.key(),m,false));
        }
        for(Group g:fuzzy){if(result.size()>=100)break;result.add(new Suggestion(ALBUM,g.title,g.key,g.cover,true));}
        return result;
    }
    private static boolean close(String title,String q){
        if(q.length()<2)return false;int i=0;for(int n=0;n<title.length()&&i<q.length();n++)if(title.charAt(n)==q.charAt(i))i++;
        if(i==q.length())return true;if(q.length()<3)return false;
        for(String word:title.split("[ _.-]+")){int wrong=0;for(int n=0;n<q.length()&&n<word.length();n++)if(word.charAt(n)!=q.charAt(n))wrong++;if(word.length()>=q.length()&&wrong==1)return true;}return false;
    }
    List<MediaItem> results(Suggestion s,String query){
        if(s.kind==ALBUM)return new ArrayList<>(albumFiles.getOrDefault(s.key,Collections.emptyList()));
        if(s.kind==APP)return new ArrayList<>(appFiles.getOrDefault(s.key,Collections.emptyList()));
        String q=Ui.normalize(query.trim());List<MediaItem> result=new ArrayList<>();for(MediaItem m:files)if(m.sortName.contains(q))result.add(m);return result;
    }
}
