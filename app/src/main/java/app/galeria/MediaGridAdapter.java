package app.galeria;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

/** ListView recycles entire rows and all their children; binding allocates no view hierarchy. */
public final class MediaGridAdapter extends BaseAdapter {
    public interface Listener {void open(MediaItem m);void select(MediaItem m);void album(MediaItem.Album a);void albumMenu(MediaItem.Album a);}
    private final Context context;private final Listener listener;private final Prefs prefs;
    private final List<Row> rows=new ArrayList<>();
    private Set<String> selected=Collections.emptySet(),favorites=Collections.emptySet();
    private boolean selection,albumMode,showSizes,showNames;private int columns,total;
    private float[] offsets;private int offsetWidth=-1;
    private static class Row {String header;int first,count;List<MediaItem> items=new ArrayList<>();List<MediaItem.Album> albums=new ArrayList<>();}
    private static class Cell {FrameLayout root;ImageView image,heart,check,play;TextView label;View shade,border;MediaItem item;}
    private static class AlbumCell {LinearLayout root;FrameLayout cover;ImageView image;TextView name,count;MediaItem.Album album;}
    private static class Line {LinearLayout root;List<Cell> cells=new ArrayList<>();List<AlbumCell> albums=new ArrayList<>();int width=-1;}
    public MediaGridAdapter(Context c,Listener l){context=c;listener=l;prefs=new Prefs(c);}
    public void setSelection(Set<String> s,boolean mode){setSelection(s,mode,true);}
    public void setSelection(Set<String> s,boolean mode,boolean notify){selected=new HashSet<>(s);selection=mode;favorites=prefs.favorites();if(notify)notifyDataSetChanged();}
    public void media(List<MediaItem> items,boolean grouped){
        albumMode=false;columns=Math.max(2,Math.min(5,prefs.integer("columns",3)));showSizes=prefs.integer("sort",0)==4;showNames=prefs.bool("names",false);rows.clear();favorites=prefs.favorites();total=items.size();
        long day=Long.MIN_VALUE;TimeZone zone=TimeZone.getDefault();Row current=null;int index=0;
        for(MediaItem m:items){
            long key=grouped?(m.added+zone.getOffset(m.added))/86400000L:0;
            if(grouped&&key!=day){Row h=new Row();h.header=dayTitle(m.added);h.first=index;rows.add(h);day=key;current=null;}
            if(current==null||current.items.size()>=columns){current=new Row();current.first=index;rows.add(current);}
            current.items.add(m);current.count++;index++;
        }
        offsets=null;notifyDataSetChanged();
    }
    public void albums(List<MediaItem.Album> albums){albumMode=true;columns=2;rows.clear();total=albums.size();Row row=null;int i=0;
        for(MediaItem.Album a:albums){if(row==null||row.albums.size()==2){row=new Row();row.first=i;rows.add(row);}row.albums.add(a);row.count++;i++;}offsets=null;notifyDataSetChanged();}
    private String dayTitle(long t){Calendar today=Calendar.getInstance();today.set(Calendar.HOUR_OF_DAY,0);today.set(Calendar.MINUTE,0);today.set(Calendar.SECOND,0);today.set(Calendar.MILLISECOND,0);if(t>=today.getTimeInMillis())return "Hoje";today.add(Calendar.DAY_OF_MONTH,-1);if(t>=today.getTimeInMillis())return "Ontem";return Ui.date(t,"d 'de' MMMM 'de' yyyy");}
    public int totalItems(){return total;}
    public int firstItem(int row){return rows.isEmpty()?0:rows.get(Math.max(0,Math.min(row,rows.size()-1))).first;}
    public int lastItem(int row){if(rows.isEmpty())return 0;Row r=rows.get(Math.max(0,Math.min(row,rows.size()-1)));return Math.min(total,r.first+Math.max(1,r.count));}
    public int rowForFraction(float fraction){int wanted=Math.round(Math.max(0,Math.min(1,fraction))*Math.max(0,total-1)),low=0,high=rows.size()-1;while(low<high){int mid=(low+high+1)/2;if(rows.get(mid).first<=wanted)low=mid;else high=mid-1;}return low;}
    private void offsets(int width){
        if(offsets!=null&&offsetWidth==width)return;offsetWidth=width;offsets=new float[rows.size()+1];
        int available=width-Ui.dp(context,40),gap=Ui.dp(context,albumMode?12:5),size=Math.max(Ui.dp(context,40),(available-gap*Math.max(0,columns-1))/Math.max(1,columns));
        TextView header=Ui.bold(context,"",14,Ui.MUTED);header.setPadding(Ui.dp(context,2),Ui.dp(context,20),0,Ui.dp(context,12));
        header.setText("Hoje");header.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,available),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        TextView name=Ui.bold(context,"Álbum",15,Ui.TEXT),count=Ui.text(context,"1 item",12,Ui.MUTED);
        name.measure(0,0);count.measure(0,0);
        float albumHeight=Math.round(size*.88f)+name.getMeasuredHeight()+count.getMeasuredHeight()+Ui.dp(context,34);
        for(int i=0;i<rows.size();i++)offsets[i+1]=offsets[i]+(rows.get(i).header!=null?header.getMeasuredHeight():albumMode?albumHeight:size+Ui.dp(context,5));
    }
    public float offsetOf(int row,int width){offsets(width);return offsets[Math.max(0,Math.min(row,rows.size()))];}
    public float totalPixels(int width){return offsetOf(rows.size(),width);}
    public int rowAtOffset(float pixels,int width){offsets(width);int lo=0,hi=Math.max(0,rows.size()-1);while(lo<hi){int mid=(lo+hi+1)/2;if(offsets[mid]<=pixels)lo=mid;else hi=mid-1;}return lo;}
    public boolean albumMode(){return albumMode;}
    @Override public int getCount(){return rows.size();}
    @Override public Object getItem(int i){return rows.get(i);}
    @Override public long getItemId(int i){return i;}
    @Override public int getViewTypeCount(){return 3;}
    @Override public int getItemViewType(int i){return rows.get(i).header!=null?0:albumMode?2:1;}
    @Override public boolean isEnabled(int i){return false;}
    @Override public View getView(int position,View convert,ViewGroup parent){
        Row row=rows.get(position);
        if(row.header!=null){TextView t=convert instanceof TextView?(TextView)convert:Ui.bold(context,"",14,Ui.MUTED);t.setText(row.header);t.setPadding(Ui.dp(context,2),Ui.dp(context,20),0,Ui.dp(context,12));return t;}
        Line line=convert!=null&&convert.getTag() instanceof Line?(Line)convert.getTag():new Line();
        if(line.root==null){line.root=Ui.row(context);line.root.setGravity(Gravity.TOP);line.root.setTag(line);}
        if(line.root.getChildCount()!=columns){line.root.removeAllViews();line.cells.clear();line.albums.clear();for(int i=0;i<columns;i++){if(albumMode){AlbumCell a=newAlbumCell();line.albums.add(a);line.root.addView(a.root);}else{Cell c=newCell();line.cells.add(c);line.root.addView(c.root);}}line.width=-1;}
        int width=(parent.getWidth()>0?parent.getWidth():context.getResources().getDisplayMetrics().widthPixels)-parent.getPaddingLeft()-parent.getPaddingRight();
        int gap=Ui.dp(context,albumMode?12:5),size=Math.max(Ui.dp(context,40),(width-gap*(columns-1))/columns);
        if(line.width!=width){line.width=width;line.root.setPadding(0,0,0,Ui.dp(context,albumMode?18:5));for(int i=0;i<columns;i++){View v=line.root.getChildAt(i);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(size,albumMode?-2:size);if(i<columns-1)p.rightMargin=gap;v.setLayoutParams(p);if(albumMode)line.albums.get(i).cover.setLayoutParams(new LinearLayout.LayoutParams(size,Math.round(size*.88f)));}}
        for(int i=0;i<columns;i++){if(albumMode){AlbumCell a=line.albums.get(i);a.root.setVisibility(i<row.albums.size()?View.VISIBLE:View.INVISIBLE);if(i<row.albums.size())bindAlbum(a,row.albums.get(i),size);else ThumbLoader.get(context).clear(a.image);}else{Cell c=line.cells.get(i);c.root.setVisibility(i<row.items.size()?View.VISIBLE:View.INVISIBLE);if(i<row.items.size())bind(c,row.items.get(i),size);else ThumbLoader.get(context).clear(c.image);}}
        return line.root;
    }
    private FrameLayout.LayoutParams corner(int w,int h,int gravity,int margin){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Ui.dp(context,w),Ui.dp(context,h),gravity);p.setMargins(Ui.dp(context,margin),Ui.dp(context,margin),Ui.dp(context,margin),Ui.dp(context,margin));return p;}
    private Cell newCell(){
        Cell c=new Cell();c.root=new FrameLayout(context);c.root.setBackground(Ui.bg(Ui.CARD,10,context));c.root.setClipToOutline(true);c.root.setFocusable(true);
        c.image=new ImageView(context);c.image.setScaleType(ImageView.ScaleType.CENTER_CROP);c.root.addView(c.image,new FrameLayout.LayoutParams(-1,-1));
        c.shade=new View(context);c.shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.TRANSPARENT,0xCE000000}));c.root.addView(c.shade,new FrameLayout.LayoutParams(-1,Ui.dp(context,42),Gravity.BOTTOM));
        c.label=Ui.bold(context,"",11,Color.WHITE);c.label.setMaxLines(1);c.label.setEllipsize(android.text.TextUtils.TruncateAt.END);FrameLayout.LayoutParams lp=corner(-2,-2,Gravity.BOTTOM|Gravity.RIGHT,7);lp.width=-2;lp.height=-2;c.root.addView(c.label,lp);
        c.play=Ui.icon(context,"play",Color.WHITE,15);c.root.addView(c.play,corner(15,15,Gravity.BOTTOM|Gravity.LEFT,8));
        c.heart=Ui.icon(context,"heartFill",Color.WHITE,19);c.heart.setPadding(Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4));c.heart.setBackground(Ui.bg(0x77000000,16,context));c.root.addView(c.heart,corner(28,28,Gravity.TOP|Gravity.RIGHT,6));
        c.border=new View(context);c.border.setBackground(Ui.border(0x25B497FF,Ui.ACCENT,10,context));c.root.addView(c.border,new FrameLayout.LayoutParams(-1,-1));
        c.check=Ui.icon(context,"check",Ui.BG,24);c.check.setPadding(Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4));c.root.addView(c.check,corner(27,27,Gravity.TOP|Gravity.LEFT,7));
        c.root.setOnClickListener(v->{if(c.item==null)return;if(selection)listener.select(c.item);else listener.open(c.item);});c.root.setOnLongClickListener(v->{if(c.item!=null)listener.select(c.item);return true;});return c;
    }
    private void bind(Cell c,MediaItem m,int size){c.item=m;ThumbLoader.get(context).bind(c.image,m,Math.min(384,Math.max(128,size)));
        boolean label=m.video||showSizes||showNames;c.shade.setVisibility(label?View.VISIBLE:View.GONE);c.label.setVisibility(label?View.VISIBLE:View.GONE);
        c.label.setText(showSizes?Ui.bytes(m.size):m.video?Ui.duration(m.duration):m.name);c.play.setVisibility(m.video?View.VISIBLE:View.GONE);
        c.heart.setVisibility(favorites.contains(m.key())?View.VISIBLE:View.GONE);boolean checked=selected.contains(m.key());c.border.setVisibility(selection&&checked?View.VISIBLE:View.GONE);c.check.setVisibility(selection?View.VISIBLE:View.GONE);
        if(selection){c.check.setImageDrawable(checked?new Icons("check",Ui.BG):null);c.check.setBackground(Ui.border(checked?Ui.ACCENT:0x44000000,checked?Ui.ACCENT:Color.WHITE,20,context));}
        c.root.setContentDescription(m.name+(m.video?", vídeo":", foto")+(showSizes?", "+Ui.bytes(m.size):""));
    }
    private AlbumCell newAlbumCell(){AlbumCell c=new AlbumCell();c.root=Ui.column(context);c.root.setFocusable(true);c.cover=new FrameLayout(context);c.cover.setBackground(Ui.bg(Ui.CARD,18,context));c.cover.setClipToOutline(true);c.image=new ImageView(context);c.image.setScaleType(ImageView.ScaleType.CENTER_CROP);c.cover.addView(c.image,new FrameLayout.LayoutParams(-1,-1));
        ImageView badge=Ui.icon(context,"albums",Color.WHITE,22);badge.setPadding(Ui.dp(context,5),Ui.dp(context,5),Ui.dp(context,5),Ui.dp(context,5));badge.setBackground(Ui.bg(0x66000000,12,context));c.cover.addView(badge,corner(32,32,Gravity.BOTTOM|Gravity.RIGHT,9));c.root.addView(c.cover);
        c.name=Ui.bold(context,"",15,Ui.TEXT);c.name.setMaxLines(1);c.name.setEllipsize(android.text.TextUtils.TruncateAt.END);c.name.setPadding(Ui.dp(context,2),Ui.dp(context,11),0,Ui.dp(context,5));c.root.addView(c.name);c.count=Ui.text(context,"",12,Ui.MUTED);c.root.addView(c.count);
        c.root.setOnClickListener(v->{if(c.album!=null)listener.album(c.album);});c.root.setOnLongClickListener(v->{if(c.album!=null)listener.albumMenu(c.album);return true;});return c;
    }
    private void bindAlbum(AlbumCell c,MediaItem.Album a,int size){c.album=a;c.image.setPadding(0,0,0,0);c.image.setScaleType(ImageView.ScaleType.CENTER_CROP);if(!a.items.isEmpty())ThumbLoader.get(context).bind(c.image,a.items.get(0),Math.min(384,size));else{ThumbLoader.get(context).clear(c.image);c.image.setImageDrawable(new Icons("folder",Ui.ACCENT));c.image.setScaleType(ImageView.ScaleType.FIT_CENTER);c.image.setPadding(size/3,size/3,size/3,size/3);}c.name.setText(a.name);c.count.setText(a.items.size()+" "+(a.items.size()==1?"item":"itens")+" · "+Ui.bytes(a.size));c.root.setContentDescription("Álbum "+a.name+", "+a.items.size()+" itens");}
}
