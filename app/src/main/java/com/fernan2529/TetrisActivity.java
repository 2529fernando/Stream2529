package com.fernan2529;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class TetrisActivity extends AppCompatActivity {

    private TetrisView tetrisView;
    private Button btnPause;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insets =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insets.hide(WindowInsetsCompat.Type.systemBars());
        insets.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FirebaseApp.initializeApp(this);

        setContentView(R.layout.activity_tetris);

        FrameLayout container = findViewById(R.id.game_container);
        tetrisView = new TetrisView(this);
        container.addView(tetrisView);

        btnPause = findViewById(R.id.btn_pause);
        btnPause.setOnClickListener(v -> {
            if (tetrisView.isPaused()) {
                tetrisView.resumeGame();
                btnPause.setText("⏸");
            } else {
                tetrisView.pauseGame();
                btnPause.setText("▶");
            }
        });
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            WindowInsetsControllerCompat insets =
                    new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
            insets.hide(WindowInsetsCompat.Type.systemBars());
            insets.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (tetrisView != null && !tetrisView.isPaused()) tetrisView.resumeGame();
    }

    @Override protected void onPause()  {
        super.onPause();
        if (tetrisView != null) tetrisView.pauseGame();
        if (btnPause != null) btnPause.setText("▶");
    }

    private static class TetrisView extends View {
        private static final int COLS = 10, PREVIEW_COLS = 4, SWIPE_THRESHOLD = 40;
        private int rows = 20;
        private int[][] board;

        private float cell, boardW, boardH, offsetX, offsetY, previewX, previewW;

        private int speedMs = 550;
        private boolean gameOver = false, paused = false;
        private int score = 0, highScore = 0;

        private int level = 1, levelStep = 500, nextLevelScore = 500;

        private static final int[][][] SHAPES = {
                { {0,1, 1,1, 2,1, 3,1}, {2,0, 2,1, 2,2, 2,3}, {0,2, 1,2, 2,2, 3,2}, {1,0, 1,1, 1,2, 1,3} },
                { {0,0, 0,1, 1,1, 2,1}, {1,0, 2,0, 1,1, 1,2}, {0,1, 1,1, 2,1, 2,2}, {1,0, 1,1, 0,2, 1,2} },
                { {2,0, 0,1, 1,1, 2,1}, {1,0, 1,1, 1,2, 2,2}, {0,1, 1,1, 2,1, 0,2}, {0,0, 1,0, 1,1, 1,2} },
                { {1,0, 2,0, 1,1, 2,1}, {1,0, 2,0, 1,1, 2,1}, {1,0, 2,0, 1,1, 2,1}, {1,0, 2,0, 1,1, 2,1} },
                { {1,0, 2,0, 0,1, 1,1}, {1,0, 1,1, 2,1, 2,2}, {1,1, 2,1, 0,2, 1,2}, {0,0, 0,1, 1,1, 1,2} },
                { {1,0, 0,1, 1,1, 2,1}, {1,0, 1,1, 2,1, 1,2}, {0,1, 1,1, 2,1, 1,2}, {1,0, 0,1, 1,1, 1,2} },
                { {0,0, 1,0, 1,1, 2,1}, {2,0, 1,1, 2,1, 1,2}, {0,1, 1,1, 1,2, 2,2}, {1,0, 0,1, 1,1, 0,2} },
        };
        private static final int[] COLORS = {
                Color.CYAN, Color.rgb(0,102,204), Color.rgb(255,153,0),
                Color.YELLOW, Color.GREEN, Color.MAGENTA, Color.RED
        };
        private int curType, nextType, rotation, curX, curY;
        private final Random rng = new Random();

        private static final String PREFS="tetris_prefs", KEY_HIGH="high_score",
                KEY_PLAYER_NAME="player_name", KEY_LB="leaderboard_json";

        private static class Entry { String name; int sc; Entry(String n,int s){name=n;sc=s;} }
        private final ArrayList<Entry> leaderboard = new ArrayList<>();

        // Colores Top-5
        private static final int GOLD = Color.rgb(212,175,55);
        private static final int[] LB_COLORS = new int[]{
                GOLD, Color.CYAN, Color.MAGENTA, Color.GREEN, Color.rgb(255,153,0)
        };

        private final Paint pCell = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pPanelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pPanelBorder = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float touchStartX, touchStartY; private long touchStartT;

        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!gameOver && !paused) { step(); invalidate(); postDelayed(this, speedMs); }
            }
        };

        private DatabaseReference leaderboardRef;
        public static class ScoreEntry { public String name; public int score; public long timestamp;
            public ScoreEntry(){} public ScoreEntry(String n,int s,long t){name=n;score=s;timestamp=t;}}

        public TetrisView(Context ctx) {
            super(ctx);
            setFocusable(true); setFocusableInTouchMode(true);

            pGrid.setColor(Color.argb(60,255,255,255)); pGrid.setStrokeWidth(2f);
            pText.setColor(Color.WHITE); pText.setTextSize(48f);
            pPanelBg.setColor(Color.argb(24,255,255,255));
            pPanelBorder.setColor(Color.argb(120,255,255,255));
            pPanelBorder.setStyle(Paint.Style.STROKE); pPanelBorder.setStrokeWidth(3f);

            highScore = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_HIGH, 0);
            loadLeaderboard();
            nextType = rng.nextInt(7);

            FirebaseDatabase db = FirebaseDatabase.getInstance();
            leaderboardRef = db.getReference("tetris").child("leaderboard");
            fetchTopFromFirebase();
        }

        private void fetchTopFromFirebase() {
            leaderboardRef.orderByChild("score").limitToLast(5)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(DataSnapshot snapshot) {
                            List<Entry> temp = new ArrayList<>();
                            int max = highScore;
                            for (DataSnapshot child: snapshot.getChildren()){
                                ScoreEntry se = child.getValue(ScoreEntry.class);
                                if (se!=null){ temp.add(new Entry(se.name==null?"Jugador":se.name,se.score));
                                    if (se.score>max) max=se.score; }
                            }
                            Collections.sort(temp,(a,b)->Integer.compare(b.sc,a.sc));
                            leaderboard.clear(); leaderboard.addAll(temp);
                            highScore = Math.max(highScore,max);
                            saveLeaderboardLocal();
                            getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                    .edit().putInt(KEY_HIGH, highScore).apply();
                            invalidate();
                        }
                        @Override public void onCancelled(DatabaseError error) {
                            Toast.makeText(getContext(),"Firebase: "+error.getMessage(),Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        private void pushScoreToFirebase(String name,int sc){
            leaderboardRef.push().setValue(new ScoreEntry(name,sc,System.currentTimeMillis()),
                    (error,ref)->{ if(error!=null) Toast.makeText(getContext(),"No se pudo guardar",Toast.LENGTH_SHORT).show();
                    else fetchTopFromFirebase(); });
        }

        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
            super.onSizeChanged(w,h,oldw,oldh);
            cell = (float) w / (COLS + PREVIEW_COLS);
            rows = (int)Math.ceil(h / cell) - 1; if (rows<20) rows=20;

            boardW = cell*COLS; boardH = cell*rows; previewW = w - boardW; previewX = boardW;
            offsetX=0f; offsetY=0f;

            board = new int[rows][COLS];

            score=0; speedMs=550; gameOver=false; paused=false;
            level=1; levelStep=500; nextLevelScore=levelStep;

            rotation=0; curX=3; curY=-2; curType=nextType; nextType=rng.nextInt(7);

            removeCallbacks(tick); postDelayed(tick,speedMs);
        }

        private void maybeLevelUp(){
            boolean leveled=false;
            while(score>=nextLevelScore){ level++; nextLevelScore+=levelStep; speedMs=Math.max(80,speedMs-50); leveled=true; }
            if(leveled && !gameOver && !paused){ removeCallbacks(tick); postDelayed(tick,speedMs); }
        }

        private boolean collides(int nx,int ny,int type,int rot){
            int[] s = SHAPES[type][rot];
            for(int i=0;i<s.length;i+=2){
                int x=nx+s[i], y=ny+s[i+1];
                if(x<0||x>=COLS||y>=rows) return true;
                if(y>=0 && board[y][x]!=0) return true;
            }
            return false;
        }

        private void spawnNewPiece(){ curType=nextType; nextType=rng.nextInt(7); rotation=0; curX=3; curY=-2;
            if(collides(curX,curY,curType,rotation)) triggerGameOver(); }

        private void lockPiece(){
            int[] s = SHAPES[curType][rotation]; boolean above=false;
            for(int i=0;i<s.length;i+=2){
                int x=curX+s[i], y=curY+s[i+1];
                if(y<0){ above=true; continue; }
                board[y][x]=curType+1;
            }
            if(above){ triggerGameOver(); return; }
            clearLines(); spawnNewPiece();
        }

        private void triggerGameOver(){
            gameOver=true; paused=false;
            if(score>highScore){
                highScore=score;
                getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_HIGH, highScore).apply();
            }
            if(qualifiesForLeaderboard(score)) post(this::promptForNameAndSave);
            removeCallbacks(tick); invalidate();
        }

        private void clearLines(){
            int lines=0;
            for(int r=rows-1;r>=0;r--){
                boolean full=true;
                for(int c=0;c<COLS;c++) if(board[r][c]==0){ full=false; break; }
                if(full){
                    lines++;
                    for(int rr=r; rr>0; rr--) System.arraycopy(board[rr-1],0,board[rr],0,COLS);
                    for(int c=0;c<COLS;c++) board[0][c]=0;
                    r++;
                }
            }
            if(lines>0){
                int add = (lines==1)?100:(lines==2)?300:(lines==3)?500:800;
                score += add; maybeLevelUp();
            }
        }

        private void step(){ if(!tryMove(curX,curY+1,rotation)) lockPiece(); }
        private boolean tryMove(int nx,int ny,int rot){ if(collides(nx,ny,curType,rot)) return false; curX=nx;curY=ny;rotation=rot; return true; }
        private void rotate(){ int nr=(rotation+1)&3; if(tryMove(curX,curY,nr))return; if(tryMove(curX-1,curY,nr))return; if(tryMove(curX+1,curY,nr))return; }
        private void hardDrop(){ while(tryMove(curX,curY+1,rotation)){} lockPiece(); invalidate(); }

        private boolean qualifiesForLeaderboard(int sc){
            if(sc<=0) return false;
            if(leaderboard.size()<5) return true;
            return sc > leaderboard.get(leaderboard.size()-1).sc;
        }

        private void promptForNameAndSave(){
            final Context ctx=getContext();
            final EditText input=new EditText(ctx);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            String lastName=ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PLAYER_NAME,"");
            input.setText(lastName);
            new AlertDialog.Builder(ctx)
                    .setTitle("Nuevo récord")
                    .setMessage("¡Entraste al Top 5! Escribe tu nombre:")
                    .setView(input)
                    .setPositiveButton("Guardar",(d,w)->{
                        String name=input.getText().toString().trim();
                        if(name.isEmpty()) name="Jugador";
                        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PLAYER_NAME,name).apply();
                        addToLeaderboard(name,score); saveLeaderboardLocal(); pushScoreToFirebase(name,score); invalidate();
                    })
                    .setNegativeButton("Cancelar",null).show();
        }

        private void addToLeaderboard(String name,int sc){
            leaderboard.add(new Entry(name,sc));
            Collections.sort(leaderboard,(a,b)->Integer.compare(b.sc,a.sc));
            while(leaderboard.size()>5) leaderboard.remove(leaderboard.size()-1);
        }

        private void loadLeaderboard(){
            leaderboard.clear();
            String json=getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LB,"");
            if(json==null||json.isEmpty()) return;
            try{
                JSONArray arr=new JSONArray(json);
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.getJSONObject(i);
                    leaderboard.add(new Entry(o.optString("name","Jugador"), o.optInt("score",0)));
                }
                Collections.sort(leaderboard,(a,b)->Integer.compare(b.sc,a.sc));
                while(leaderboard.size()>5) leaderboard.remove(leaderboard.size()-1);
            }catch(JSONException ignored){}
        }

        private void saveLeaderboardLocal(){
            JSONArray arr=new JSONArray();
            try{
                for(Entry e:leaderboard){
                    JSONObject o=new JSONObject(); o.put("name",e.name); o.put("score",e.sc); arr.put(o);
                }
            }catch(JSONException ignored){}
            getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LB,arr.toString()).apply();
        }

        public boolean isPaused(){ return paused; }
        public void pauseGame(){ paused=true; removeCallbacks(tick); invalidate(); }
        public void resumeGame(){ if(!gameOver){ paused=false; removeCallbacks(tick); postDelayed(tick,speedMs); invalidate(); } }

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            canvas.drawColor(Color.BLACK);

            for(int r=0;r<rows;r++) for(int c=0;c<COLS;c++)
                if(board[r][c]!=0){ pCell.setColor(COLORS[board[r][c]-1]); drawCell(canvas,c,r,pCell); }

            if(!gameOver){
                int[] s=SHAPES[curType][rotation]; pCell.setColor(COLORS[curType]);
                for(int i=0;i<s.length;i+=2){ int x=curX+s[i], y=curY+s[i+1]; if(y>=0) drawCell(canvas,x,y,pCell); }
            }

            for(int c=0;c<=COLS;c++){ float x=offsetX+c*cell; canvas.drawLine(x,offsetY,x,offsetY+boardH,pGrid); }
            for(int r=0;r<=rows;r++){ float y=offsetY+r*cell; canvas.drawLine(offsetX,y,offsetX+boardW,y,pGrid); }

            drawPreviewPanel(canvas);

            pText.setTextSize(Math.max(36f,cell*0.7f));
            canvas.drawText("Puntos: "+score, offsetX, offsetY+pText.getTextSize(), pText);
            String topBar="Nivel: "+level;
            float midX=offsetX+(boardW-pText.measureText(topBar))/2f;
            canvas.drawText(topBar, midX, offsetY+pText.getTextSize(), pText);
            String best="Mejor: "+highScore;
            canvas.drawText(best, offsetX+boardW-pText.measureText(best), offsetY+pText.getTextSize(), pText);

            if(paused){
                pText.setTextSize(Math.max(42f,cell));
                String text="PAUSA"; float x=(getWidth()-pText.measureText(text))/2f; float y=offsetY+boardH/2f;
                canvas.drawText(text,x,y,pText);
            } else if(gameOver){
                pText.setTextSize(Math.max(42f,cell));
                String go1="GAME OVER", go2="Puntos: "+score+"   Mejor: "+highScore,
                        go3="Nivel alcanzado: "+level, go4="Toca para reiniciar";
                float y=offsetY+boardH/2f;
                canvas.drawText(go1,(getWidth()-pText.measureText(go1))/2f,y-pText.getTextSize(),pText);
                canvas.drawText(go2,(getWidth()-pText.measureText(go2))/2f,y+4,pText);
                canvas.drawText(go3,(getWidth()-pText.measureText(go3))/2f,y+pText.getTextSize()+8,pText);
                canvas.drawText(go4,(getWidth()-pText.measureText(go4))/2f,y+pText.getTextSize()*2+14,pText);
            }
        }

        private void drawPreviewPanel(Canvas canvas){
            float pad=Math.max(6f,cell*0.35f);
            float left=previewX+pad*0.5f, top=offsetY, right=previewX+previewW-pad*0.5f, bottom=offsetY+boardH;

            canvas.drawRect(left,top,right,bottom,pPanelBg);
            canvas.drawRect(left,top,right,bottom,pPanelBorder);

            float titleMax=Math.max(26f,cell*0.6f);
            pText.setTextSize(titleMax);
            String title="Siguiente";
            canvas.drawText(title, left+(right-left-pText.measureText(title))/2f, top+pText.getTextSize()+pad*0.5f, pText);

            float boxTop=top+pText.getTextSize()+pad*1.2f;
            float boxH=Math.min(boardH*0.35f, cell*7f);
            float boxBottom=Math.min(bottom-pad, boxTop+boxH);
            float boxLeft=left+pad, boxRight=right-pad;
            canvas.drawRect(boxLeft,boxTop,boxRight,boxBottom,pPanelBorder);

            int[] shape=SHAPES[nextType][0];
            int minX=99,maxX=-99,minY=99,maxY=-99;
            for(int i=0;i<shape.length;i+=2){
                minX=Math.min(minX,shape[i]); maxX=Math.max(maxX,shape[i]);
                minY=Math.min(minY,shape[i+1]); maxY=Math.max(maxY,shape[i+1]);
            }
            int wBlocks=(maxX-minX+1), hBlocks=(maxY-minY+1);
            float cellPrev=Math.min((boxRight-boxLeft)/(wBlocks+1f), (boxBottom-boxTop)/(hBlocks+1f));
            float startX=boxLeft+((boxRight-boxLeft)-(wBlocks*cellPrev))/2f-(minX*cellPrev);
            float startY=boxTop+((boxBottom-boxTop)-(hBlocks*cellPrev))/2f-(minY*cellPrev);

            pCell.setColor(COLORS[nextType]);
            for(int i=0;i<shape.length;i+=2){
                float x=startX+shape[i]*cellPrev, y=startY+shape[i+1]*cellPrev;
                canvas.drawRect(x+1,y+1,x+cellPrev-1,y+cellPrev-1,pCell);
                canvas.drawRect(x,y,x+cellPrev,y+cellPrev,pPanelBorder);
            }

            String lvlText="Nivel: "+level;
            float lvlY=boxBottom+pText.getTextSize()+pad*0.8f;
            drawCenteredText(canvas,lvlText,boxLeft,boxRight,lvlY,Math.max(24f,cell*0.55f));

            float y=lvlY+pText.getTextSize()+pad*0.8f;
            float baseSize=Math.max(22f,cell*0.5f);
            float lineSpacing=Math.max(4f,cell*0.1f);
            float bottomLimit = bottom - pad;

            for(int i=0;i<Math.min(5,leaderboard.size());i++){
                Entry e=leaderboard.get(i);
                String line = "#"+(i+1)+" "+e.name+" — "+e.sc;
                int color = LB_COLORS[Math.min(i, LB_COLORS.length-1)];
                float scale = (i==0)?1.12f:1.0f;
                boolean bold = (i==0);

                y = drawWrappedMultiLineStyled(canvas, line, boxLeft, boxRight, y,
                        baseSize, lineSpacing, color, scale, bold, bottomLimit);

                if(y>bottomLimit) break;
            }
            pText.setColor(Color.WHITE); pText.setFakeBoldText(false);
        }

        private void drawCenteredText(Canvas canvas,String text,float left,float right,float baselineY,float size){
            pText.setTextSize(size);
            float tw=pText.measureText(text);
            canvas.drawText(text, left+(right-left-tw)/2f, baselineY, pText);
        }

        /**
         * Envoltura por PALABRAS en tantas líneas como se necesiten (sin "…").
         * Respeta el límite inferior del panel; si no hay espacio, deja de dibujar.
         * Devuelve la Y de inicio del siguiente item (o bottomLimit si ya no hay espacio).
         */
        private float drawWrappedMultiLineStyled(Canvas canvas, String text,
                                                 float left, float right, float startY,
                                                 float baseTextSize, float lineSpacing,
                                                 int color, float sizeScale, boolean bold,
                                                 float bottomLimit) {
            float textSize = baseTextSize * sizeScale;
            pText.setTextSize(textSize);
            pText.setColor(color);
            pText.setFakeBoldText(bold);

            final float maxW = right - left;
            final float lineH = pText.getTextSize();

            // Si cabe en una sola
            if (pText.measureText(text) <= maxW) {
                if (startY + lineH > bottomLimit) return bottomLimit + 1;
                canvas.drawText(text, left, startY, pText);
                float nextY = startY + lineH + lineSpacing;
                return Math.min(nextY, bottomLimit + 1);
            }

            String[] words = text.trim().split("\\s+");
            StringBuilder line = new StringBuilder();
            float y = startY;

            for (int i = 0; i < words.length; i++) {
                String candidate = (line.length()==0) ? words[i] : line + " " + words[i];
                if (pText.measureText(candidate) <= maxW) {
                    line.setLength(0); line.append(candidate);
                } else {
                    // dibujar línea actual
                    if (y + lineH > bottomLimit) return bottomLimit + 1;
                    canvas.drawText(line.toString(), left, y, pText);
                    y += lineH + lineSpacing / 2f;
                    // empezar nueva con la palabra que no cupo
                    line.setLength(0);
                    line.append(words[i]);
                }
            }

            // última línea
            if (line.length() > 0) {
                if (y + lineH > bottomLimit) return bottomLimit + 1;
                canvas.drawText(line.toString(), left, y, pText);
                y += lineH + lineSpacing;
            }

            return Math.min(y, bottomLimit + 1);
        }

        private void drawCell(Canvas canvas,int cx,int cy,Paint paint){
            float x=offsetX+cx*cell, y=offsetY+cy*cell;
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(x+1,y+1,x+cell-1,y+cell-1,paint);
            canvas.drawRect(x,y,x+cell,y+cell,pGrid);
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            if(paused) return false;
            if(gameOver && e.getAction()==MotionEvent.ACTION_DOWN){ resetGame(); return true; }
            switch(e.getAction()){
                case MotionEvent.ACTION_DOWN:
                    touchStartX=e.getX(); touchStartY=e.getY(); touchStartT=System.currentTimeMillis(); return true;
                case MotionEvent.ACTION_UP:
                    float dx=e.getX()-touchStartX, dy=e.getY()-touchStartY;
                    long dt=System.currentTimeMillis()-touchStartT;
                    if(Math.abs(dx)<SWIPE_THRESHOLD && Math.abs(dy)<SWIPE_THRESHOLD && dt<250){ rotate(); invalidate(); return true; }
                    if(Math.abs(dx)>Math.abs(dy)){
                        if(dx>SWIPE_THRESHOLD) tryMove(curX+1,curY,rotation);
                        else if(dx<-SWIPE_THRESHOLD) tryMove(curX-1,curY,rotation);
                    } else if(dy>SWIPE_THRESHOLD){
                        hardDrop();
                    }
                    invalidate(); return true;
            }
            return super.onTouchEvent(e);
        }

        private void resetGame(){
            for(int r=0;r<rows;r++) for(int c=0;c<COLS;c++) board[r][c]=0;
            score=0; speedMs=550; gameOver=false; paused=false;
            level=1; levelStep=500; nextLevelScore=levelStep;
            nextType=rng.nextInt(7); spawnNewPiece();
            removeCallbacks(tick); postDelayed(tick,speedMs); invalidate();
        }

        @Override protected void onDetachedFromWindow(){ super.onDetachedFromWindow(); removeCallbacks(tick); }
    }
}
