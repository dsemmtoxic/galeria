package app.galeria;

import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class SearchActivity extends SecureActivity implements MediaGridAdapter.Listener {
    private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private MediaRepo repo;private SearchIndex index;private EditText input;private ImageView clear;private ListView list;private TextView status;private Suggestions suggestions;private MediaGridAdapter grid;
    private SearchIndex.Suggestion selection;private List<MediaItem> result=Collections.emptyList();private int revision;private Future<?> searching;private boolean ready,resultsMode;
    private final Runnable queryWork=()->search();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Ui.theme(this);Ui.bars(this,Ui.BG);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);setContentView(root);
        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,16),Ui.dp(this,15),Ui.dp(this,10),Ui.dp(this,12));
        LinearLayout pill=Ui.row(this);pill.setBackground(Ui.bg(Ui.SURFACE,27,this));pill.setPadding(Ui.dp(this,14),0,Ui.dp(this,4),0);pill.addView(Ui.icon(this,"search",Ui.MUTED,20));
        input=new EditText(this);input.setSingleLine(true);input.setTextSize(16);input.setTextColor(Ui.TEXT);input.setHintTextColor(Ui.DIM);input.setHint("Pesquisar por arquivos ou álbuns");input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(Ui.dp(this,10),0,0,0);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setInputType(InputType.TYPE_CLASS_TEXT);
        pill.addView(input,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));clear=Ui.iconButton(this,"close","Limpar pesquisa",()->input.setText(""));clear.setPadding(Ui.dp(this,15),Ui.dp(this,15),Ui.dp(this,15),Ui.dp(this,15));pill.addView(clear);clear.setVisibility(View.GONE);top.addView(pill,new LinearLayout.LayoutParams(0,-2,1));
        TextView cancel=Ui.text(this,"Cancelar",15,Ui.ACCENT);cancel.setGravity(Gravity.CENTER);cancel.setPadding(Ui.dp(this,12),0,Ui.dp(this,6),0);cancel.setBackground(Ui.ripple(this,Color.TRANSPARENT,20));cancel.setOnClickListener(v->finish());top.addView(cancel,new LinearLayout.LayoutParams(-2,Ui.dp(this,48)));root.addView(top);
        status=Ui.text(this,"",12,Ui.MUTED);status.setPadding(Ui.dp(this,20),Ui.dp(this,6),Ui.dp(this,20),Ui.dp(this,10));status.setVisibility(View.GONE);root.addView(status);
        list=new ListView(this);list.setDivider(null);list.setCacheColorHint(Color.TRANSPARENT);list.setVerticalScrollBarEnabled(false);list.setClipToPadding(false);list.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,16));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));suggestions=new Suggestions();list.setAdapter(suggestions);list.setOnItemClickListener((p,v,pos,id)->{Object row=suggestions.getItem(pos);if(row instanceof SearchIndex.Suggestion)choose((SearchIndex.Suggestion)row);});
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int n){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int n){clear.setVisibility(s.length()==0?View.GONE:View.VISIBLE);resultsMode=false;selection=null;revision++;list.setPadding(0,Ui.dp(SearchActivity.this,12),0,Ui.dp(SearchActivity.this,16));list.setAdapter(suggestions);suggestions.replace(Collections.emptyList(),s.toString());status.setVisibility(View.GONE);main.removeCallbacks(queryWork);main.postDelayed(queryWork,150);}});
        input.setOnEditorActionListener((v,action,e)->{if(action==EditorInfo.IME_ACTION_SEARCH){showFiles();return true;}return false;});
        repo=new MediaRepo(this,(items,error)->{if(error!=null){status.setText(error);status.setVisibility(View.VISIBLE);return;}index(items);});
        List<MediaItem> cached=GalleryCache.items;if(cached!=null)index(cached);
        if(state!=null)input.setText(state.getString("query",""));
        input.requestFocus();input.postDelayed(()->{if(!isFinishing()&&AppLock.canAccess(this))((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);},220);
    }
    private void index(List<MediaItem> items){revision++;worker.execute(()->{SearchIndex next=new SearchIndex(this,items);main.post(()->{if(isFinishing()||isDestroyed())return;index=next;ready=true;if(resultsMode&&selection!=null)showResult(selection,false);else search();});});}
    private void search(){
        if(!ready)return;String q=input.getText().toString();int token=++revision;SearchIndex current=index;if(searching!=null)searching.cancel(true);
        if(q.trim().isEmpty()){suggestions.replace(Collections.emptyList(),q);status.setVisibility(View.GONE);return;}
        searching=worker.submit(()->{List<SearchIndex.Suggestion> rows=current.suggest(q);main.post(()->{if(token!=revision||isFinishing()||isDestroyed()||resultsMode)return;suggestions.replace(rows,q);status.setText(rows.isEmpty()?"Nenhum resultado":"");status.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);});});
    }
    private void keyboardOff(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();}
    private void choose(SearchIndex.Suggestion s){
        keyboardOff();showResult(s,s.kind==SearchIndex.FILE);
    }
    private void showFiles(){if(!ready||input.getText().toString().trim().isEmpty())return;keyboardOff();choose(new SearchIndex.Suggestion(SearchIndex.QUERY,input.getText().toString().trim(),"",null,false));}
    private void showResult(SearchIndex.Suggestion s,boolean open){
        resultsMode=true;selection=s;String q=input.getText().toString();int token=++revision;SearchIndex current=index;
        worker.execute(()->{List<MediaItem> files=current.results(s,q);ViewerSession.sort(files,new Prefs(this).integer("sort",0));main.post(()->{
            if(token!=revision||isFinishing()||isDestroyed())return;result=files;grid=new MediaGridAdapter(this,this);list.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),Ui.dp(this,16));list.setAdapter(grid);grid.media(files,false);status.setText((s.kind==SearchIndex.APP?s.title+" · ":"")+files.size()+" "+(files.size()==1?"arquivo":"arquivos"));status.setVisibility(View.VISIBLE);list.setSelection(0);
            if(open&&s.cover!=null)open(s.cover);
        });});
    }
    @Override public void open(MediaItem m){String id=ViewerSession.start(this,result);Bundle scope=new Bundle();scope.putInt("sort",new Prefs(this).integer("sort",0));if(selection!=null&&selection.kind==SearchIndex.APP)scope.putString("screenshot_package",selection.key);else{scope.putBoolean("file_names",true);scope.putString("query",input.getText().toString().trim());}
        startActivity(new Intent(this,ViewerActivity.class).setData(m.uri).putExtra("video",m.video).putExtra("session",id).putExtra("scope",scope));}
    @Override public void select(MediaItem m){open(m);}@Override public void album(MediaItem.Album a){}@Override public void albumMenu(MediaItem.Album a){}
    @Override protected void onResume(){super.onResume();repo.start();}
    @Override protected void onPause(){super.onPause();repo.stop();}
    @Override protected void onDestroy(){super.onDestroy();repo.close();main.removeCallbacksAndMessages(null);worker.shutdownNow();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("query",input.getText().toString());}
    @Override public void onBackPressed(){if(resultsMode){resultsMode=false;selection=null;list.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,16));list.setAdapter(suggestions);search();return;}super.onBackPressed();}
    static CharSequence highlight(String title,String query){
        SpannableString text=new SpannableString(title);String normalized=Ui.normalize(title),q=Ui.normalize(query.trim());if(q.isEmpty())return text;
        List<Integer> positions=new ArrayList<>();for(int i=0;i<title.length();i++){String part=Ui.normalize(title.substring(i,i+1));for(int n=0;n<part.length();n++)positions.add(i);}
        boolean matched=false;for(int from=0;(from=normalized.indexOf(q,from))>=0;from+=q.length())if(from+q.length()-1<positions.size()){int start=positions.get(from),end=positions.get(from+q.length()-1)+1;text.setSpan(new ForegroundColorSpan(Ui.ACCENT),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);matched=true;}
        if(!matched){List<Integer> found=new ArrayList<>();int n=0;for(int i=0;i<normalized.length()&&n<q.length();i++)if(normalized.charAt(i)==q.charAt(n)){found.add(i);n++;}if(n==q.length())for(int at:found)if(at<positions.size())text.setSpan(new ForegroundColorSpan(Ui.ACCENT),positions.get(at),positions.get(at)+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        return text;
    }
    private final class Suggestions extends BaseAdapter {
        private final List<Object> rows=new ArrayList<>();private String q="";
        void replace(List<SearchIndex.Suggestion> source,String query){q=query;rows.clear();boolean fuzzy=false;for(SearchIndex.Suggestion s:source){if(s.approximate&&!fuzzy){rows.add("Sugestões");fuzzy=true;}rows.add(s);}notifyDataSetChanged();}
        public int getCount(){return rows.size();}public Object getItem(int p){return rows.get(p);}public long getItemId(int p){return p;}public int getViewTypeCount(){return 2;}public int getItemViewType(int p){return rows.get(p) instanceof String?1:0;}public boolean isEnabled(int p){return getItemViewType(p)==0;}
        public View getView(int pos,View convert,ViewGroup parent){
            Object item=rows.get(pos);if(item instanceof String){TextView t=convert instanceof TextView?(TextView)convert:Ui.text(SearchActivity.this,"",12,Ui.MUTED);t.setText((String)item);t.setPadding(Ui.dp(SearchActivity.this,16),Ui.dp(SearchActivity.this,18),0,Ui.dp(SearchActivity.this,7));return t;}
            Row holder;if(convert!=null&&convert.getTag() instanceof Row)holder=(Row)convert.getTag();else{holder=new Row();holder.root=Ui.row(SearchActivity.this);holder.root.setTag(holder);holder.root.setPadding(Ui.dp(SearchActivity.this,16),0,Ui.dp(SearchActivity.this,16),0);holder.root.setMinimumHeight(Ui.dp(SearchActivity.this,68));
                holder.image=new ImageView(SearchActivity.this);holder.image.setScaleType(ImageView.ScaleType.CENTER_CROP);holder.image.setBackground(Ui.bg(Ui.CARD,20,SearchActivity.this));holder.image.setClipToOutline(true);holder.root.addView(holder.image,new LinearLayout.LayoutParams(Ui.dp(SearchActivity.this,40),Ui.dp(SearchActivity.this,40)));
                holder.name=Ui.bold(SearchActivity.this,"",16,Ui.TEXT);holder.name.setMaxLines(1);holder.name.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams name=new LinearLayout.LayoutParams(0,-2,1);name.leftMargin=Ui.dp(SearchActivity.this,12);name.rightMargin=Ui.dp(SearchActivity.this,8);holder.root.addView(holder.name,name);
                holder.kind=Ui.text(SearchActivity.this,"",12,Ui.MUTED);holder.kind.setMaxLines(1);holder.kind.setMaxWidth(Ui.dp(SearchActivity.this,145));holder.kind.setEllipsize(TextUtils.TruncateAt.END);holder.root.addView(holder.kind);
            }
            SearchIndex.Suggestion s=(SearchIndex.Suggestion)item;holder.name.setText(highlight(s.title,q));holder.kind.setText(s.category());if(s.cover!=null)ThumbLoader.get(SearchActivity.this).bind(holder.image,s.cover,128);else{ThumbLoader.get(SearchActivity.this).clear(holder.image);holder.image.setImageDrawable(new Icons("folder",Ui.ACCENT));}holder.root.setContentDescription(s.title+", "+s.category());return holder.root;
        }
    }
    private static final class Row{LinearLayout root;ImageView image;TextView name,kind;}
}
