package app.galeria;

import android.net.Uri;
import org.json.*;

/** Shared metadata format for recoverable viewer sessions and the local recycle bin. */
final class MediaJson {
    static JSONObject encode(MediaItem m)throws JSONException{
        JSONObject j=new JSONObject();j.put("uri",m.key());j.put("name",m.name);j.put("mime",m.mime);j.put("album",m.album);j.put("path",m.path);j.put("volume",m.volume);
        j.put("id",m.id);j.put("size",m.size);j.put("date",m.date);j.put("added",m.added);j.put("modified",m.modified);j.put("duration",m.duration);j.put("generation",m.generation);
        j.put("width",m.width);j.put("height",m.height);j.put("video",m.video);j.put("trashed",m.trashed);return j;
    }
    static MediaItem decode(JSONObject j)throws JSONException{
        MediaItem m=new MediaItem();m.uri=Uri.parse(j.getString("uri"));m.name=j.optString("name");m.mime=j.optString("mime","image/jpeg");m.album=j.optString("album");m.path=j.optString("path");m.volume=j.optString("volume","external_primary");
        m.id=j.optLong("id");m.size=j.optLong("size");m.date=j.optLong("date");m.added=j.optLong("added");m.modified=j.optLong("modified");m.duration=j.optLong("duration");m.generation=j.optLong("generation");
        m.width=j.optInt("width");m.height=j.optInt("height");m.video=j.optBoolean("video");m.trashed=j.optBoolean("trashed");index(m);return m;
    }
    static void index(MediaItem m){m.sortName=Ui.normalize(m.name);m.searchText=Ui.normalize(m.name+" "+m.album+" "+m.mime+" "+Ui.date(m.date,"dd/MM/yyyy"));}
}
