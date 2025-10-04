package com.fernan2529;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class RubrikActivity extends AppCompatActivity {

    private RubikGLView glView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rubrik);

        FrameLayout host = findViewById(R.id.glHost);
        glView = new RubikGLView(this);
        host.addView(glView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        Button btnReset = findViewById(R.id.btnReset);
        Button btnScramble = findViewById(R.id.btnScramble);

        btnReset.setOnClickListener(v -> glView.resetCube());
        btnScramble.setOnClickListener(v -> glView.scramble(25));

        Toast.makeText(this,
                "Toca afuera del cubo para orbitar • Toca una cara y arrastra para girar una fila/columna • Pellizca para zoom • Doble toque: Home",
                Toast.LENGTH_LONG).show();
    }

    // ==================== GLSurfaceView con controles táctiles ====================
    public static class RubikGLView extends GLSurfaceView {
        private final RubikRenderer renderer;
        private final GestureDetector gesture;
        private final ScaleGestureDetector scaleGesture;

        // Estado gesto
        private boolean isScaling = false;
        private boolean hasPick = false;
        private boolean isOrbitMode = false; // Solo orbitar si el toque empezó fuera del cubo
        private RubikRenderer.PickResult pick; // cara + u,v locales
        private float downX, downY;

        // Base 2D local normalizada de la cara tocada
        private float ux, uy, vx, vy;
        private static final float SWIPE_MIN_PX = 22f;

        public RubikGLView(Context context) {
            super(context);
            renderer = new RubikRenderer();
            setEGLContextClientVersion(2);
            setRenderer(renderer);
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

            gesture = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @Override public boolean onDoubleTap(MotionEvent e) { renderer.autoHome(); return true; }
            });

            scaleGesture = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    isScaling = true;
                    renderer.zoom(1f / detector.getScaleFactor());
                    return true;
                }
                @Override public void onScaleEnd(ScaleGestureDetector detector) { isScaling = false; }
            });
        }

        public RubikGLView(Context context, AttributeSet attrs) { this(context); }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            scaleGesture.onTouchEvent(ev);
            gesture.onTouchEvent(ev);
            if (isScaling) return true;

            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    downX = ev.getX(); downY = ev.getY();
                    pick = renderer.pickWithUV(downX, downY, getWidth(), getHeight());
                    hasPick = (pick != null && pick.face != '\0');
                    isOrbitMode = !hasPick; // Solo orbit si se empezó FUERA del cubo

                    if (hasPick) {
                        float[] basis = renderer.faceBasis2D(pick.face, getWidth(), getHeight());
                        if (basis != null) {
                            ux = basis[0]; uy = basis[1];
                            vx = basis[2]; vy = basis[3];
                        } else {
                            hasPick = false;
                            isOrbitMode = true;
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (isOrbitMode) {
                        float dx = ev.getX() - downX;
                        float dy = ev.getY() - downY;
                        renderer.orbit(dx * 0.25f, dy * 0.25f);
                        downX = ev.getX(); downY = ev.getY();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;

                    if (!isOrbitMode && hasPick) {
                        if (Math.hypot(dx, dy) < SWIPE_MIN_PX) {
                            RubikRenderer.AxisLayer al = defaultAxisForTap(pick.face, pick.u, pick.v);
                            if (al != null) renderer.enqueueAxis(al.axis, al.layerIdx, 1);
                        } else {
                            float du = dx * ux + dy * uy;
                            float dv = dx * vx + dy * vy;
                            RubikRenderer.AxisLayerDir m = renderer.mapDragToAxisLayer(pick.face, pick.u, pick.v, du, dv);
                            if (m != null) {
                                renderer.enqueueAxis(m.axis, m.layerIdx, m.clockwise ? 1 : 3);
                            }
                        }
                    }
                    hasPick = false;
                    isOrbitMode = false;
                    pick = null;
                    return true;
                }
            }
            return true;
        }

        // Tap sin arrastre: capa más externa coherente a la cara tocada
        private RubikRenderer.AxisLayer defaultAxisForTap(char face, float u, float v) {
            switch (face) {
                case 'z': return new RubikRenderer.AxisLayer('Z', +1);
                case 'Z': return new RubikRenderer.AxisLayer('Z', -1);
                case 'y': return new RubikRenderer.AxisLayer('Y', +1);
                case 'Y': return new RubikRenderer.AxisLayer('Y', -1);
                case 'x': return new RubikRenderer.AxisLayer('X', +1);
                case 'X': return new RubikRenderer.AxisLayer('X', -1);
            }
            return null;
        }

        // ---- Pasarelas usadas por los botones ----
        public void resetCube() { renderer.reset(); }
        public void scramble(int n) { renderer.scramble(n); }
        public void enqueue(String move) { /* opcional si quieres inyectar moves en notación */ }
    }

    // ============================ Renderer y lógica del cubo ============================
    static class RubikRenderer implements GLSurfaceView.Renderer {

        // Colores estándar
        private static final float[] U_WHITE  = {1f, 1f, 1f, 1f};
        private static final float[] D_YELLOW = {1f, 1f, 0f, 1f};
        private static final float[] F_GREEN  = {0f, 0.6f, 0f, 1f};
        private static final float[] B_BLUE   = {0f, 0f, 1f, 1f};
        private static final float[] L_ORANGE = {1f, 0.5f, 0f, 1f};
        private static final float[] R_RED    = {1f, 0f, 0f, 1f};
        private static final float[] N_GRAY   = {0.15f, 0.15f, 0.15f, 1f};

        // Matrices
        private final float[] proj = new float[16];
        private final float[] view = new float[16];
        private final float[] vp   = new float[16];
        private final float[] m    = new float[16];
        private final float[] mvp  = new float[16];
        private final float[] invVP = new float[16];

        // Cámara orbital
        private float yaw = -35f, pitch = -30f, dist = 12.5f;

        // GL
        private int program, aPos, uMVP;
        private FloatBuffer vb;
        private final int vertexStride = 3;

        // Cubo
        private final List<Cubelet> cubelets = new ArrayList<>(27);
        private final Queue<AxisMove> queue = new ArrayDeque<>();
        private AxisMove currentMove = null;
        private long animStartMs = 0L;
        private static final long ANIM_MS = 170L;
        private final Random rng = new Random();

        // Picking/geom
        private static final float BIG = 1.575f;
        private static final float HALF = 0.48f;
        private static final float LAYER_STEP = 1.05f;

        // ------------- Tipos auxiliares -------------
        static class Cubelet {
            int ix, iy, iz;                 // -1,0,1
            final float[] tempRot = new float[16];
            boolean inRotate = false;
            float[][] faceColors = new float[6][]; // U,D,F,B,L,R
            Cubelet(int ix,int iy,int iz){ this.ix=ix; this.iy=iy; this.iz=iz; Matrix.setIdentityM(tempRot,0); }
        }
        static class AxisMove { final char axis; final int layerIdx; final int turns; AxisMove(char a,int l,int t){axis=a;layerIdx=l;turns=t;} }
        static class FloatHit { float t,u,v; char tag; FloatHit(float t,float u,float v,char tag){this.t=t;this.u=u;this.v=v;this.tag=tag;} }
        static class PickResult { char face; float u,v; PickResult(char f,float u,float v){this.face=f; this.u=u; this.v=v;} }
        static class AxisLayer { char axis; int layerIdx; AxisLayer(char a,int l){axis=a; layerIdx=l;} }
        static class AxisLayerDir { char axis; int layerIdx; boolean clockwise; AxisLayerDir(char a,int l,boolean cw){axis=a;layerIdx=l;clockwise=cw;} }

        // ---------- Renderer ----------
        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glClearColor(0.1f,0.1f,0.12f,1f);
            program = buildProgram(VS,FS);
            aPos = GLES20.glGetAttribLocation(program,"aPos");
            uMVP = GLES20.glGetUniformLocation(program,"uMVP");
            buildUnitCube();
            buildCubelets();
            autoHome();
        }
        @Override public void onSurfaceChanged(GL10 gl, int w, int h) {
            GLES20.glViewport(0,0,w,h);
            float aspect = (float) w / Math.max(1,h);
            Matrix.perspectiveM(proj,0,45f,aspect,0.1f,100f);
            updateView();
        }
        @Override public void onDrawFrame(GL10 gl) {
            long now = System.currentTimeMillis();
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);

            if (currentMove==null && !queue.isEmpty()) {
                currentMove = queue.poll();
                animStartMs = now;
                markRotatingLayer(currentMove.axis, currentMove.layerIdx, true);
            }
            if (currentMove!=null) {
                float t = Math.min(1f,(now-animStartMs)/(float)ANIM_MS);
                float angle = (currentMove.turns==3 ? -90f : 90f*currentMove.turns) * easeInOutQuad(t);
                applyTempRotation(currentMove.axis, currentMove.layerIdx, angle);
                if (t>=1f) {
                    finalizeRotation(currentMove.axis, currentMove.layerIdx, currentMove.turns);
                    markRotatingLayer(currentMove.axis, currentMove.layerIdx, false);
                    currentMove = null;
                }
            }

            Matrix.multiplyMM(vp,0,proj,0,view,0);
            Matrix.invertM(invVP,0,vp,0);

            GLES20.glUseProgram(program);
            for (Cubelet c : cubelets) {
                Matrix.setIdentityM(m,0);
                Matrix.translateM(m,0, c.ix*LAYER_STEP, c.iy*LAYER_STEP, c.iz*LAYER_STEP);

                if (c.inRotate) {
                    float[] tM = new float[16];
                    Matrix.multiplyMM(tM, 0, c.tempRot, 0, m, 0); // Slice * Tcube
                    System.arraycopy(tM, 0, m, 0, 16);
                }

                Matrix.multiplyMM(mvp,0,vp,0,m,0);
                GLES20.glUniformMatrix4fv(uMVP,1,false,mvp,0);
                drawCubelet(c);
            }
        }

        // ---------- Cámara ----------
        void orbit(float dx, float dy) {
            yaw -= dx; pitch -= dy;
            pitch = Math.max(-89f, Math.min(89f, pitch));
            updateView();
        }
        void zoom(float mul) { dist = Math.max(4.5f, Math.min(18f, dist*mul)); updateView(); }
        void autoHome(){ yaw=-35f; pitch=-30f; dist=12.5f; updateView(); }
        private void updateView() {
            float cx = (float)(dist*Math.cos(Math.toRadians(pitch))*Math.cos(Math.toRadians(yaw)));
            float cy = (float)(dist*Math.sin(Math.toRadians(pitch)));
            float cz = (float)(dist*Math.cos(Math.toRadians(pitch))*Math.sin(Math.toRadians(yaw)));
            Matrix.setLookAtM(view,0, cx,cy,cz, 0,0,0, 0,1,0);
        }

        // ---------- Picking ----------
        PickResult pickWithUV(float sx, float sy, int w, int h) {
            float[] ndc0 = { (2f*sx)/w - 1f, 1f - (2f*sy)/h, -1f, 1f };
            float[] ndc1 = { (2f*sx)/w - 1f, 1f - (2f*sy)/h, +1f, 1f };
            float[] p0 = new float[4], p1 = new float[4];
            Matrix.multiplyMV(p0,0,invVP,0,ndc0,0);
            Matrix.multiplyMV(p1,0,invVP,0,ndc1,0);
            for (int i=0;i<3;i++){ p0[i]/=p0[3]; p1[i]/=p1[3]; }
            float rx = p1[0]-p0[0], ry = p1[1]-p0[1], rz = p1[2]-p0[2];

            FloatHit hit = null;
            hit = nearest(hit, intersectPlane(p0,rx,ry,rz, +BIG, 'x'));
            hit = nearest(hit, intersectPlane(p0,rx,ry,rz, -BIG, 'X'));
            hit = nearest(hit, intersectPlane(p0,rx,ry,rz, +BIG, 'y'));
            hit = nearest(hit, intersectPlane(p0,rx,ry,rz, -BIG, 'Y'));
            hit = nearest(hit, intersectPlane(p0,rx,ry,rz, +BIG, 'z'));
            hit = nearest(hit, intersectPlane(p0,rx,ry,rz, -BIG, 'Z'));
            if (hit == null) return null;

            if (Math.abs(hit.u) <= BIG && Math.abs(hit.v) <= BIG) {
                return new PickResult(hit.tag, hit.u, hit.v);
            }
            return null;
        }
        char pickFace(float sx, float sy, int w, int h) {
            PickResult pr = pickWithUV(sx,sy,w,h);
            return pr==null?'\0':pr.face;
        }
        static FloatHit nearest(FloatHit a, FloatHit b) { if (b==null) return a; if (a==null) return b; return (b.t < a.t ? b : a); }
        private static FloatHit intersectPlane(float[] p0,float rx,float ry,float rz,float k,char plane){
            float t,x,y,z;
            switch (plane) {
                case 'x': if (rx==0) return null; t=(k-p0[0])/rx; if (t<=0) return null; y=p0[1]+t*ry; z=p0[2]+t*rz; return new FloatHit(t,y,z,'x');
                case 'X': if (rx==0) return null; t=(-k-p0[0])/rx; if (t<=0) return null; y=p0[1]+t*ry; z=p0[2]+t*rz; return new FloatHit(t,y,z,'X');
                case 'y': if (ry==0) return null; t=(k-p0[1])/ry; if (t<=0) return null; x=p0[0]+t*rx; z=p0[2]+t*rz; return new FloatHit(t,x,z,'y');
                case 'Y': if (ry==0) return null; t=(-k-p0[1])/ry; if (t<=0) return null; x=p0[0]+t*rx; z=p0[2]+t*rz; return new FloatHit(t,x,z,'Y');
                case 'z': if (rz==0) return null; t=(k-p0[2])/rz; if (t<=0) return null; x=p0[0]+t*rx; y=p0[1]+t*ry; return new FloatHit(t,x,y,'z');
                case 'Z': if (rz==0) return null; t=(-k-p0[2])/rz; if (t<=0) return null; x=p0[0]+t*rx; y=p0[1]+t*ry; return new FloatHit(t,x,y,'Z');
            }
            return null;
        }

        // ---------- Mapeo gesto -> (eje, capa, sentido) ----------
        // Importante: pick.u/pick.v SIEMPRE son coords del mundo (no dependen del signo que usamos en faceBasis2D):
        //  z/Z: u=x, v=y   |  y/Y: u=x, v=z   |  x/X: u=y, v=z
        AxisLayerDir mapDragToAxisLayer(char face, float u, float v, float du, float dv) {
            boolean alongU = Math.abs(du) >= Math.abs(dv);
            switch (face) {
                case 'z': // frente (n=+Z)  in-plane: x(u), y(v)
                    if (alongU) { // mover a la derecha/izquierda -> eje Y, capa por y
                        boolean cw = du > 0;
                        return new AxisLayerDir('Y', quantToIdx(v), cw);
                    } else {       // mover arriba/abajo -> eje X, capa por x
                        boolean cw = dv > 0;
                        return new AxisLayerDir('X', quantToIdx(u), cw);
                    }
                case 'Z': // atrás (n=-Z)  in-plane: x(u), y(v)
                    if (alongU) { // derecha/izquierda -> eje Y (misma capa por y)
                        boolean cw = du > 0;
                        return new AxisLayerDir('Y', quantToIdx(v), cw);
                    } else {       // arriba/abajo -> eje X (capa por x)
                        boolean cw = dv < 0; // invertido respecto al frente
                        return new AxisLayerDir('X', quantToIdx(u), cw);
                    }
                case 'y': // arriba (n=+Y) in-plane: x(u), z(v)
                    if (alongU) { // izq/der en techo -> eje Z, capa por z
                        boolean cw = du > 0;
                        return new AxisLayerDir('Z', quantToIdx(v), cw);
                    } else {       // adelante/atrás -> eje X, capa por x
                        boolean cw = dv > 0;
                        return new AxisLayerDir('X', quantToIdx(u), cw);
                    }
                case 'Y': // abajo (n=-Y)  in-plane: x(u), z(v)
                    if (alongU) { // izq/der en base -> eje Z, capa por z
                        boolean cw = du < 0; // invertido
                        return new AxisLayerDir('Z', quantToIdx(v), cw);
                    } else {       // adelante/atrás -> eje X, capa por x
                        boolean cw = dv > 0;
                        return new AxisLayerDir('X', quantToIdx(u), cw);
                    }
                case 'x': // derecha (n=+X) in-plane: y(u), z(v)
                    if (alongU) { // arriba/abajo en cara derecha -> eje Z, capa por z
                        boolean cw = du < 0; // invertido
                        return new AxisLayerDir('Z', quantToIdx(v), cw);
                    } else {       // adelante/atrás -> eje Y, capa por y
                        boolean cw = dv > 0;
                        return new AxisLayerDir('Y', quantToIdx(u), cw);
                    }
                case 'X': // izquierda (n=-X) in-plane: y(u), z(v)
                    if (alongU) { // arriba/abajo en cara izquierda -> eje Z, capa por z
                        boolean cw = du > 0; // invertido respecto a 'x'
                        return new AxisLayerDir('Z', quantToIdx(v), cw);
                    } else {       // adelante/atrás -> eje Y, capa por y
                        boolean cw = dv > 0;
                        return new AxisLayerDir('Y', quantToIdx(u), cw);
                    }
            }
            return null;
        }

        private int quantToIdx(float val) {
            // Cuantiza al centro de capa más cercano entre {-LAYER_STEP, 0, +LAYER_STEP}
            float dNeg = Math.abs(val - (-LAYER_STEP));
            float dZero = Math.abs(val - 0f);
            float dPos = Math.abs(val - (+LAYER_STEP));
            if (dNeg <= dZero && dNeg <= dPos) return -1;
            if (dPos <= dZero && dPos <= dNeg) return +1;
            return 0;
        }

        // ---------- Enqueue / Animación ----------
        void enqueueAxis(char axis, int layerIdx, int turns) {
            if (layerIdx < -1 || layerIdx > 1) return;
            queue.offer(new AxisMove(axis, layerIdx, turns));
        }
        void scramble(int n) {
            char[] axes={'X','Y','Z'};
            int[] layers={-1,0,1};
            for (int i=0;i<n;i++){
                char a=axes[rng.nextInt(axes.length)];
                int l=layers[rng.nextInt(layers.length)];
                int r=rng.nextInt(3);
                int t=(r==0?1:(r==1?3:2));
                queue.offer(new AxisMove(a,l,t));
            }
        }
        void reset() { queue.clear(); currentMove=null; buildCubelets(); }

        private void markRotatingLayer(char axis, int layerIdx, boolean rotating) {
            for (Cubelet c : cubelets) if (belongsToLayer(c,axis,layerIdx)) {
                c.inRotate = rotating; Matrix.setIdentityM(c.tempRot,0);
            }
        }
        private boolean belongsToLayer(Cubelet c, char axis, int idx) {
            switch (axis) {
                case 'X': return c.ix==idx;
                case 'Y': return c.iy==idx;
                case 'Z': return c.iz==idx;
            }
            return false;
        }

        private void applyTempRotation(char axis, int layerIdx, float angleDeg) {
            float ax=0, ay=0, az=0;
            float px=0, py=0, pz=0;
            switch (axis) {
                case 'X': ax=1; px=LAYER_STEP*layerIdx; break;
                case 'Y': ay=1; py=LAYER_STEP*layerIdx; break;
                case 'Z': az=1; pz=LAYER_STEP*layerIdx; break;
            }
            float[] slice = new float[16];
            float[] tmp   = new float[16];
            Matrix.setIdentityM(slice, 0);
            Matrix.translateM(slice, 0, px, py, pz);
            Matrix.setIdentityM(tmp, 0);
            Matrix.rotateM(tmp, 0, angleDeg, ax, ay, az);
            Matrix.multiplyMM(slice, 0, slice, 0, tmp, 0);
            Matrix.translateM(slice, 0, -px, -py, -pz);

            for (Cubelet c : cubelets) if (c.inRotate) System.arraycopy(slice, 0, c.tempRot, 0, 16);
        }
        private void finalizeRotation(char axis, int layerIdx, int turns) {
            int t=turns;
            while (t-- > 0) {
                rotateLayerPos(axis, layerIdx);
                rotateLayerFaces(axis, true);
            }
        }
        private void rotateLayerPos(char axis, int layerIdx) {
            for (Cubelet c : cubelets) {
                if (!belongsToLayer(c,axis,layerIdx)) continue;
                int x=c.ix, y=c.iy, z=c.iz;
                switch (axis) {
                    case 'X': c.iy=z;  c.iz=-y; break;
                    case 'Y': c.ix=-z; c.iz= x; break;
                    case 'Z': c.ix=y;  c.iy=-x; break;
                }
            }
        }
        // order: U(0),D(1),F(2),B(3),L(4),R(5)
        private void rotateLayerFaces(char axis, boolean cw) {
            for (Cubelet c : cubelets) {
                if (!c.inRotate) continue;
                float[][] fc=c.faceColors; float[] t;
                switch (axis) {
                    case 'X':
                        if (cw) { t=fc[0]; fc[0]=fc[2]; fc[2]=fc[1]; fc[1]=fc[3]; fc[3]=t; }
                        else     { t=fc[0]; fc[0]=fc[3]; fc[3]=fc[1]; fc[1]=fc[2]; fc[2]=t; }
                        break;
                    case 'Y':
                        if (cw) { t=fc[2]; fc[2]=fc[5]; fc[5]=fc[3]; fc[3]=fc[4]; fc[4]=t; }
                        else     { t=fc[2]; fc[2]=fc[4]; fc[4]=fc[3]; fc[3]=fc[5]; fc[5]=t; }
                        break;
                    case 'Z':
                        if (cw) { t=fc[0]; fc[0]=fc[4]; fc[4]=fc[1]; fc[1]=fc[5]; fc[5]=t; }
                        else     { t=fc[0]; fc[0]=fc[5]; fc[5]=fc[1]; fc[1]=fc[4]; fc[4]=t; }
                        break;
                }
            }
        }

        // ---------- Base 2D de la cara ----------
        float[] faceBasis2D(char face, int w, int h) {
            float[] u = new float[3], v = new float[3], c = new float[3];
            switch (face) {
                case 'y': // Arriba: u=x, v=-z
                    u[0]=+1;u[1]=0;u[2]=0;  v[0]=0;v[1]=0;v[2]=-1; c[0]=0;c[1]=+LAYER_STEP;c[2]=0;
                    break;
                case 'Y': // Abajo: u=x, v=+z
                    u[0]=+1;u[1]=0;u[2]=0;  v[0]=0;v[1]=0;v[2]=+1; c[0]=0;c[1]=-LAYER_STEP;c[2]=0;
                    break;
                case 'z': // Frente: u=x, v=y
                    u[0]=+1;u[1]=0;u[2]=0;  v[0]=0;v[1]=+1;v[2]=0; c[0]=0;c[1]=0;c[2]=+LAYER_STEP;
                    break;
                case 'Z': // Atrás: u=-x, v=y
                    u[0]=-1;u[1]=0;u[2]=0;  v[0]=0;v[1]=+1;v[2]=0; c[0]=0;c[1]=0;c[2]=-LAYER_STEP;
                    break;
                case 'x': // Derecha: u=y, v=z
                    u[0]=0;u[1]=+1;u[2]=0;  v[0]=0;v[1]=0;v[2]=+1; c[0]=+LAYER_STEP;c[1]=0;c[2]=0;
                    break;
                case 'X': // Izquierda: u=y, v=-z
                    u[0]=0;u[1]=+1;u[2]=0;  v[0]=0;v[1]=0;v[2]=-1; c[0]=-LAYER_STEP;c[1]=0;c[2]=0;
                    break;
                default: return null;
            }
            float[] cu = { c[0]+u[0], c[1]+u[1], c[2]+u[2], 1 };
            float[] cv = { c[0]+v[0], c[1]+v[1], c[2]+v[2], 1 };
            float[] cc = { c[0], c[1], c[2], 1 };
            float[] s_c  = projectToScreen(cc, w, h);
            float[] s_u  = projectToScreen(cu, w, h);
            float[] s_v  = projectToScreen(cv, w, h);
            if (s_c == null || s_u == null || s_v == null) return null;

            float ux = s_u[0] - s_c[0], uy = s_u[1] - s_c[1];
            float vx = s_v[0] - s_c[0], vy = s_v[1] - s_c[1];
            float lu = (float)Math.hypot(ux, uy);
            float lv = (float)Math.hypot(vx, vy);
            if (lu < 1e-4 || lv < 1e-4) return null;
            return new float[]{ ux/lu, uy/lu, vx/lv, vy/lv };
        }

        private float[] projectToScreen(float[] p, int w, int h) {
            float[] clip = new float[4];
            Matrix.multiplyMV(clip,0,vp,0,p,0);
            if (Math.abs(clip[3]) < 1e-6) return null;
            float nx = clip[0]/clip[3];
            float ny = clip[1]/clip[3];
            float sx = (nx + 1f) * 0.5f * w;
            float sy = (1f - ny) * 0.5f * h;
            return new float[]{sx, sy};
        }

        // ---------- Geometría y dibujo ----------
        private void buildUnitCube() {
            float s = HALF;
            float[] verts = concat(
                    quad(+s,-s,-s,  +s,+s,-s,  +s,+s,+s,  +s,-s,+s), // +X
                    quad(-s,-s,+s,  -s,+s,+s,  -s,+s,-s,  -s,-s,-s), // -X
                    quad(-s,+s,-s,  -s,+s,+s,  +s,+s,+s,  +s,+s,-s), // +Y
                    quad(-s,-s,+s,  -s,-s,-s,  +s,-s,-s,  +s,-s,+s), // -Y
                    quad(-s,-s,+s,  +s,-s,+s,  +s,+s,+s,  -s,+s,+s), // +Z
                    quad(+s,-s,-s,  -s,-s,-s,  -s,+s,-s,  +s,+s,-s)  // -Z
            );
            vb = fb(verts);
        }
        private void buildCubelets() {
            cubelets.clear();
            for (int ix=-1; ix<=1; ix++)
                for (int iy=-1; iy<=1; iy++)
                    for (int iz=-1; iz<=1; iz++) {
                        Cubelet c = new Cubelet(ix,iy,iz);
                        c.faceColors = new float[][]{
                                iy==+1? U_WHITE: N_GRAY,   // U
                                iy==-1? D_YELLOW: N_GRAY,  // D
                                iz==+1? F_GREEN: N_GRAY,   // F
                                iz==-1? B_BLUE: N_GRAY,    // B
                                ix==-1? L_ORANGE: N_GRAY,  // L
                                ix==+1? R_RED: N_GRAY      // R
                        };
                        cubelets.add(c);
                    }
        }
        private void drawCubelet(Cubelet c) {
            for (int face=0; face<6; face++) {
                float[] color = colorForWorldFace(c, face);
                drawFace(face, color);
            }
        }
        private float[] colorForWorldFace(Cubelet c, int idx) {
            switch (idx) {
                case 0: return c.faceColors[5];
                case 1: return c.faceColors[4];
                case 2: return c.faceColors[0];
                case 3: return c.faceColors[1];
                case 4: return c.faceColors[2];
                case 5: return c.faceColors[3];
            }
            return N_GRAY;
        }
        private void drawFace(int faceIndex, float[] color) {
            int first = faceIndex * 6;
            vb.position(first*3);
            GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,vertexStride*4,vb);
            GLES20.glEnableVertexAttribArray(aPos);

            int uColor = GLES20.glGetUniformLocation(program,"uColor");
            GLES20.glUniform4fv(uColor,1,color,0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,6);
        }

        // ---------- Utils ----------
        private static FloatBuffer fb(float[] arr){ FloatBuffer b= ByteBuffer.allocateDirect(arr.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer(); b.put(arr).position(0); return b; }
        private static float[] quad(float x0,float y0,float z0,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3){
            return new float[]{x0,y0,z0, x1,y1,z1, x2,y2,z2,  x0,y0,z0, x2,y2,z2, x3,y3,z3};
        }
        private static float[] concat(float[]... a){ int n=0; for(float[] v:a) n+=v.length; float[] o=new float[n]; int p=0; for(float[] v:a){ System.arraycopy(v,0,o,p,v.length); p+=v.length;} return o; }
        private static float easeInOutQuad(float t){ return (t<0.5f)?(2*t*t):(1-(float)Math.pow(-2*t+2,2)/2f); }

        private static final String VS = "uniform mat4 uMVP; attribute vec3 aPos; void main(){ gl_Position = uMVP*vec4(aPos,1.0);} ";
        private static final String FS = "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor=uColor; }";

        private static int compile(int type,String src){
            int s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,src); GLES20.glCompileShader(s);
            int[] ok=new int[1]; GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
            if(ok[0]==0) throw new RuntimeException("Shader: "+GLES20.glGetShaderInfoLog(s));
            return s;
        }
        private static int buildProgram(String vs,String fs){
            int p=GLES20.glCreateProgram(); int v=compile(GLES20.GL_VERTEX_SHADER,vs); int f=compile(GLES20.GL_FRAGMENT_SHADER,fs);
            GLES20.glAttachShader(p,v); GLES20.glAttachShader(p,f); GLES20.glLinkProgram(p);
            int[] ok=new int[1]; GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);
            if(ok[0]==0) throw new RuntimeException("Program: "+GLES20.glGetProgramInfoLog(p));
            return p;
        }
    }
}
