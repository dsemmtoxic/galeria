package app.galeria;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

public final class Prefs {
    private final SharedPreferences p;
    public Prefs(Context c) { p = c.getSharedPreferences("galeria_preferences", Context.MODE_PRIVATE); }
    public boolean bool(String key, boolean def) { return p.getBoolean(key, def); }
    public int integer(String key, int def) { return p.getInt(key, def); }
    public String string(String key, String def) { return p.getString(key, def); }
    public void set(String key, boolean value) { p.edit().putBoolean(key, value).apply(); }
    public void set(String key, int value) { p.edit().putInt(key, value).apply(); }
    public void set(String key, String value) { p.edit().putString(key, value).apply(); }
    public synchronized Set<String> favorites() { return new HashSet<>(p.getStringSet("favorites", new HashSet<String>())); }
    public synchronized boolean favorite(String uri) { return p.getStringSet("favorites", java.util.Collections.emptySet()).contains(uri); }
    public synchronized boolean toggleFavorite(String uri) {
        Set<String> set = favorites();
        boolean added = !set.remove(uri);
        if (added) set.add(uri);
        p.edit().putStringSet("favorites", set).apply();
        return added;
    }
    public synchronized void migrateFavorite(String from, String to) {
        Set<String> set = favorites();
        if (set.remove(from)) { set.add(to); p.edit().putStringSet("favorites", set).apply(); }
    }
    public synchronized void removeFavorite(String uri){Set<String> set=favorites();if(set.remove(uri))p.edit().putStringSet("favorites",set).apply();}
    public synchronized Set<String> hidden() { return new HashSet<>(p.getStringSet("hidden_albums", new HashSet<String>())); }
    public synchronized void hide(String key, boolean hidden) {
        Set<String> set = hidden();
        if (hidden) set.add(key); else set.remove(key);
        p.edit().putStringSet("hidden_albums", set).apply();
    }
    /** Most recently used destination albums, newest first, with no duplicates. */
    public synchronized List<String> recentAlbums(){
        List<String> result=new ArrayList<>();
        try{JSONArray json=new JSONArray(string("recent_destination_albums","[]"));
            for(int i=0;i<json.length()&&result.size()<5;i++){
                String key=json.optString(i,"");if(!key.isEmpty()&&!result.contains(key))result.add(key);
            }
        }catch(Exception ignored){}return result;
    }
    public synchronized void rememberAlbum(String key){
        if(key==null||key.isEmpty())return;
        List<String> list=recentAlbums();list.remove(key);list.add(0,key);
        JSONArray json=new JSONArray();for(int i=0;i<Math.min(5,list.size());i++)json.put(list.get(i));
        set("recent_destination_albums",json.toString());
    }
    public List<Folder> folders() {
        List<Folder> result = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(string("folders", "[]"));
            for (int i=0;i<a.length();i++) {
                JSONObject j=a.getJSONObject(i);
                result.add(new Folder(j.getString("name"),j.getString("uri"),j.optString("path"),j.optString("volume","external_primary")));
            }
        } catch (Exception ignored) { }
        return result;
    }
    public synchronized void addFolder(Folder f) {
        List<Folder> folders=folders();
        for (Folder old:folders) if(old.uri.equals(f.uri)) return;
        folders.add(f); saveFolders(folders);
    }
    public synchronized void removeFolder(String uri) {
        List<Folder> folders=folders();
        folders.removeIf(f -> f.uri.equals(uri)); saveFolders(folders);
    }
    private void saveFolders(List<Folder> folders) {
        JSONArray a=new JSONArray();
        for(Folder f:folders) try {
            JSONObject j=new JSONObject();
            j.put("name",f.name);j.put("uri",f.uri);j.put("path",f.path);j.put("volume",f.volume);a.put(j);
        } catch(Exception ignored) { }
        set("folders",a.toString());
    }
    public static final class Folder {
        public final String name,uri,path,volume;
        public Folder(String n,String u,String p,String v){name=n;uri=u;path=p;volume=v;}
        public String key(){return path.isEmpty()?uri:volume+":"+path;}
    }
}
