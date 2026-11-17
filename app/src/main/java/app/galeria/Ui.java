package app.galeria;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Ui {
    public static int BG=0xFF101017,SURFACE=0xFF1B1B25,CARD=0xFF242332,LINE=0xFF302E40;
    public static final int TEXT=0xFFF5F1FF,MUTED=0xFFAAA4BC,DIM=0xFF8C819F;
    public static int ACCENT=0xFFB497FF,SOFT=0xFF302342;
    public static void theme(Context c) {
        Prefs p=new Prefs(c);
        ACCENT=p.integer("accent",0xFFB497FF);
        boolean black=p.bool("oled",false);
        BG=black?Color.BLACK:0xFF101017;
        SURFACE=black?0xFF121218:0xFF1B1B25;
        CARD=black?0xFF1D1B27:0xFF242332;
        SOFT=Color.rgb((Color.red(ACCENT)+45)/5,(Color.green(ACCENT)+20)/5,(Color.blue(ACCENT)+70)/5);
    }
    public static int dp(Context c,float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    public static TextView text(Context c,String s,float size,int color) {
        TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setFontFeatureSettings("kern");t.setIncludeFontPadding(false);
        return t;
    }
    public static TextView bold(Context c,String s,float size,int color){
        TextView t=text(c,s,size,color);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;
    }
    public static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    public static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    public static GradientDrawable bg(int color,float radius,Context c) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;
    }
    public static GradientDrawable border(int color,int stroke,float radius,Context c) {
        GradientDrawable d=bg(color,radius,c);d.setStroke(dp(c,1),stroke);return d;
    }
    public static RippleDrawable ripple(Context c,int color,float radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x30B497FF),bg(color,radius,c),bg(Color.WHITE,radius,c));
    }
    public static ImageView icon(Context c,String name,int color,int size) {
        ImageView v=new ImageView(c);v.setImageDrawable(new Icons(name,color));v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));return v;
    }
    public static ImageView iconButton(Context c,String name,String desc,Runnable action){
        ImageView v=icon(c,name,TEXT,48);v.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));
        v.setBackground(ripple(c,Color.TRANSPARENT,24));v.setContentDescription(desc);
        v.setOnClickListener(w->action.run());v.setFocusable(true);return v;
    }
    public static TextView button(Context c,String label,Runnable action){
        TextView t=bold(c,label,15,BG);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(c,50));
        t.setPadding(dp(c,20),dp(c,14),dp(c,20),dp(c,14));t.setBackground(ripple(c,ACCENT,16));
        t.setOnClickListener(v->action.run());t.setFocusable(true);return t;
    }
    public static void toast(Context c,String s){Toast.makeText(c,s,Toast.LENGTH_SHORT).show();}
    public static String duration(long ms) {
        long seconds=Math.max(0,ms/1000);long h=seconds/3600;
        return h>0?String.format(Locale.US,"%d:%02d:%02d",h,(seconds/60)%60,seconds%60):
            String.format(Locale.US,"%d:%02d",seconds/60,seconds%60);
    }
    public static String bytes(long n){
        if(n<1024)return n+" B";if(n<1024*1024)return String.format(Locale.forLanguageTag("pt-BR"),"%.1f KB",n/1024f);
        if(n<1024L*1024*1024)return String.format(Locale.forLanguageTag("pt-BR"),"%.1f MB",n/(1024f*1024));
        return String.format(Locale.forLanguageTag("pt-BR"),"%.2f GB",n/(1024f*1024*1024));
    }
    private static final java.util.regex.Pattern DIACRITICS=java.util.regex.Pattern.compile("\\p{M}");
    private static final ThreadLocal<java.util.Map<String,SimpleDateFormat>> DATE_FORMATS=ThreadLocal.withInitial(java.util.HashMap::new);
    public static String normalize(String s) {
        return DIACRITICS.matcher(Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }
    public static String date(long ms,String pattern){java.util.Map<String,SimpleDateFormat> formats=DATE_FORMATS.get();SimpleDateFormat f=formats.get(pattern);if(f==null){f=new SimpleDateFormat(pattern,Locale.forLanguageTag("pt-BR"));formats.put(pattern,f);}f.setTimeZone(java.util.TimeZone.getDefault());return f.format(new Date(ms));}
    public static void bars(Activity a,int color) {
        a.getWindow().setStatusBarColor(color);a.getWindow().setNavigationBarColor(color);
        a.getWindow().getDecorView().setSystemUiVisibility(0);
    }
    public static class Sheet {
        public final Dialog dialog;
        public final LinearLayout content;
        private final Context c;
        public Sheet(Activity a,String title,String subtitle) {
            c=a;dialog=new Dialog(a);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            content=column(a);content.setPadding(dp(a,20),dp(a,12),dp(a,20),dp(a,24));content.setBackground(bg(SURFACE,26,a));
            View grip=new View(a);grip.setBackground(bg(DIM,2,a));
            LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(a,36),dp(a,4));gp.gravity=Gravity.CENTER;gp.bottomMargin=dp(a,18);content.addView(grip,gp);
            LinearLayout heading=row(a);
            TextView h=bold(a,title,22,TEXT);heading.addView(h,new LinearLayout.LayoutParams(0,dp(a,48),1));
            heading.addView(iconButton(a,"close","Fechar",this::close));content.addView(heading);
            if(subtitle!=null && !subtitle.isEmpty()){TextView sub=text(a,subtitle,13,MUTED);sub.setPadding(0,0,0,dp(a,12));content.addView(sub);}
            ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);scroll.addView(content);dialog.setContentView(scroll);
            Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.setDimAmount(.5f);}
        }
        public Sheet item(String icon,String title,String subtitle,Runnable action){
            LinearLayout r=row(c);r.setPadding(dp(c,4),dp(c,12),dp(c,4),dp(c,12));r.setMinimumHeight(dp(c,58));r.setBackground(ripple(c,Color.TRANSPARENT,12));
            ImageView i=Ui.icon(c,icon,ACCENT,24);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(c,24),dp(c,24));ip.rightMargin=dp(c,18);r.addView(i,ip);
            LinearLayout col=column(c);col.addView(bold(c,title,15,TEXT));if(subtitle!=null){TextView s=text(c,subtitle,12,MUTED);s.setPadding(0,dp(c,5),0,0);col.addView(s);}
            r.addView(col,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            r.addView(Ui.icon(c,"chevron",DIM,18));r.setOnClickListener(v->{close();action.run();});content.addView(r);return this;
        }
        public void show(){dialog.show();Window w=dialog.getWindow();if(w!=null)w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
        public void close(){dialog.dismiss();}
    }
}
