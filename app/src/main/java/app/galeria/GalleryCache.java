package app.galeria;

import java.util.*;

/** The search screen can use the loaded collection immediately while observing subsequent changes. */
final class GalleryCache {
    static volatile List<MediaItem> items;
    static void remember(List<MediaItem> source){items=Collections.unmodifiableList(new ArrayList<>(source));}
}
