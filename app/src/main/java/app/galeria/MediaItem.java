package app.galeria;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

public final class MediaItem {
    public Uri uri;
    public String name="",mime="image/jpeg",album="",path="",volume="external_primary";
    public long id,size,date,added,modified,duration,generation;
    public int width,height;
    public boolean video,trashed;
    public String searchText="",sortName="";
    public String key(){return uri.toString();}
    public String albumKey(){return volume+":"+path;}
    public static final class Album {
        public String key,name,path,volume;
        public Prefs.Folder folder;
        public final List<MediaItem> items=new ArrayList<>();
        public long size;
        public Album(String k,String n,String p,String v){key=k;name=n;path=p;volume=v;}
    }
    static final class Session {
        static String id;
        static volatile List<MediaItem> items=new ArrayList<>();
    }
}
