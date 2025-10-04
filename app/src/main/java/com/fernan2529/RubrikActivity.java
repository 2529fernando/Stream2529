package com.fernan2529;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Juego simple de Cubo de Rubik (3x3) en una sola Activity.
 *
 * Características:
 * - Render en 2D tipo "net" (desplegado) con stickers.
 * - Toca una cara y desliza (↑ ↓ ← →) para girar capas de esa cara.
 * - Botones flotantes minimalistas (Reset, Mezclar, Deshacer, Rehacer, Ayuda).
 * - Guarda/recupera estado en rotación de pantalla (onSaveInstanceState).
 * - Lógica de cubo con notación de movimientos estándar (U, D, L, R, F, B y primas).
 *
 * Nota: Es intencionalmente auto-contenido (sin layouts XML), ideal para pegar como una sola clase.
 */
public class RubrikActivity extends Activity {

    private CubeView cubeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cubeView = new CubeView(this);
        if (savedInstanceState != null) {
            String state = savedInstanceState.getString("cube_state");
            String history = savedInstanceState.getString("move_history");
            String redo = savedInstanceState.getString("redo_stack");
            if (state != null) cubeView.cube.fromCompact(state);
            if (history != null) cubeView.history.fromString(history);
            if (redo != null) cubeView.redo.fromString(redo);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0b0f14"));
        root.addView(cubeView);
        root.addView(new HUD(this, cubeView));
        setContentView(root);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("cube_state", cubeView.cube.toCompact());
        outState.putString("move_history", cubeView.history.toString());
        outState.putString("redo_stack", cubeView.redo.toString());
    }

    // ------------------------- Modelo de Cubo -------------------------

    static class Cube {
        // 6 caras * 9 stickers (3x3). Orden de caras: U, R, F, D, L, B (estándar Singmaster)
        // Colores por defecto (estándar): U=blanco, R=rojo, F=verde, D=amarillo, L=naranja, B=azul
        int[][] faces = new int[6][9];

        // Paleta de colores para render
        static final int[] PALETTE = new int[]{
                Color.WHITE,                    // U
                Color.rgb(220, 40, 40), // R
                Color.rgb(40, 160, 60), // F
                Color.rgb(250, 210, 0), // D
                Color.rgb(255, 120, 0), // L
                Color.rgb(0, 90, 200)   // B
        };

        Cube() { reset(); }

        void reset() {
            for (int f = 0; f < 6; f++) {
                for (int i = 0; i < 9; i++) faces[f][i] = f;
            }
        }

        String toCompact() {
            StringBuilder sb = new StringBuilder(54);
            for (int f = 0; f < 6; f++) for (int i = 0; i < 9; i++) sb.append((char) ('0' + faces[f][i]));
            return sb.toString();
        }

        void fromCompact(String s) {
            if (s == null || s.length() != 54) return;
            int k = 0;
            for (int f = 0; f < 6; f++) for (int i = 0; i < 9; i++) faces[f][i] = s.charAt(k++) - '0';
        }

        // Rotación de una cara 3x3 (índices dentro de la cara)
        private void rotateFaceCW(int f) {
            int[] a = faces[f].clone();
            faces[f][0] = a[6]; faces[f][1] = a[3]; faces[f][2] = a[0];
            faces[f][3] = a[7]; faces[f][4] = a[4]; faces[f][5] = a[1];
            faces[f][6] = a[8]; faces[f][7] = a[5]; faces[f][8] = a[2];
        }

        private void rotateFaceCCW(int f) { rotateFaceCW(f); rotateFaceCW(f); rotateFaceCW(f); }

        // Movimientos básicos U, D, L, R, F, B (+ primos/apostrofe, doble)
        void move(String m) {
            // Normaliza notación: e.g., "U", "U'", "U2"
            boolean prime = m.endsWith("'");
            boolean dbl = m.endsWith("2");
            char face = m.charAt(0);

            int times = dbl ? 2 : 1;
            if (prime) times = 3; // 3 CW = 1 CCW

            for (int t = 0; t < times; t++) {
                switch (face) {
                    case 'U': U(); break;
                    case 'D': D(); break;
                    case 'L': L(); break;
                    case 'R': R(); break;
                    case 'F': F(); break;
                    case 'B': B(); break;
                }
            }
        }

        // Definiciones de movimientos según Singmaster, con caras: 0=U,1=R,2=F,3=D,4=L,5=B
        void U() {
            // Rotar cara U
            rotateFaceCW(0);
            // Bordes adyacentes: F,U -> indices [0..2] de F? No, U afecta filas superiores de F, R, B, L
            int[] f = faces[2], r = faces[1], b = faces[5], l = faces[4];
            int a0=f[0], a1=f[1], a2=f[2];
            f[0]=r[0]; f[1]=r[1]; f[2]=r[2];
            r[0]=b[0]; r[1]=b[1]; r[2]=b[2];
            b[0]=l[0]; b[1]=l[1]; b[2]=l[2];
            l[0]=a0;  l[1]=a1;  l[2]=a2;
        }

        void D() {
            rotateFaceCW(3);
            int[] f = faces[2], r = faces[1], b = faces[5], l = faces[4];
            int a0=f[6], a1=f[7], a2=f[8];
            f[6]=l[6]; f[7]=l[7]; f[8]=l[8];
            l[6]=b[6]; l[7]=b[7]; l[8]=b[8];
            b[6]=r[6]; b[7]=r[7]; b[8]=r[8];
            r[6]=a0;  r[7]=a1;  r[8]=a2;
        }

        void L() {
            rotateFaceCW(4);
            int[] u = faces[0], f = faces[2], d = faces[3], b = faces[5];
            int a0=u[0], a3=u[3], a6=u[6];
            u[0]=f[0]; u[3]=f[3]; u[6]=f[6];
            f[0]=d[0]; f[3]=d[3]; f[6]=d[6];
            d[0]=b[8]; d[3]=b[5]; d[6]=b[2];
            b[8]=a0;  b[5]=a3;  b[2]=a6;
        }

        void R() {
            rotateFaceCW(1);
            int[] u = faces[0], f = faces[2], d = faces[3], b = faces[5];
            int a2=u[2], a5=u[5], a8=u[8];
            u[2]=b[6]; u[5]=b[3]; u[8]=b[0];
            b[6]=d[8]; b[3]=d[5]; b[0]=d[2];
            d[8]=f[8]; d[5]=f[5]; d[2]=f[2];
            f[8]=a8;  f[5]=a5;  f[2]=a2;
        }

        void F() {
            rotateFaceCW(2);
            int[] u = faces[0], r = faces[1], d = faces[3], l = faces[4];
            int a6=u[6], a7=u[7], a8=u[8];
            u[6]=l[8]; u[7]=l[5]; u[8]=l[2];
            l[8]=d[2]; l[5]=d[1]; l[2]=d[0];
            d[2]=r[0]; d[1]=r[3]; d[0]=r[6];
            r[0]=a6;  r[3]=a7;  r[6]=a8;
        }

        void B() {
            rotateFaceCW(5);
            int[] u = faces[0], r = faces[1], d = faces[3], l = faces[4];
            int a0=u[0], a1=u[1], a2=u[2];
            u[0]=r[2]; u[1]=r[5]; u[2]=r[8];
            r[2]=d[8]; r[5]=d[7]; r[8]=d[6];
            d[8]=l[6]; d[7]=l[3]; d[6]=l[0];
            l[6]=a0;  l[3]=a1;  l[0]=a2;
        }

        boolean isSolved() {
            for (int f = 0; f < 6; f++) {
                for (int i = 0; i < 9; i++) if (faces[f][i] != f) return false;
            }
            return true;
        }
    }

    // ------------------------- Historial de movimientos -------------------------

    static class MoveHistory {
        private final Deque<String> stack = new ArrayDeque<>();
        @Override public String toString() { return String.join(",", stack); }
        void fromString(String s) {
            stack.clear();
            if (s == null || s.isEmpty()) return;
            String[] parts = s.split(",");
            Collections.addAll(stack, parts);
        }
        void push(String m) { stack.push(m); }
        String pop() { return stack.isEmpty() ? null : stack.pop(); }
        boolean isEmpty() { return stack.isEmpty(); }
    }

    // ------------------------- Vista y entrada -------------------------

    static class CubeView extends View {
        Cube cube = new Cube();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pad;
        RectF tile = new RectF();
        GestureDetector detector;
        float touchX, touchY;
        long lastSolvedToastAt = 0L;

        MoveHistory history = new MoveHistory();
        MoveHistory redo = new MoveHistory();

        // Orden de caras en la malla:
        //       [ U ]
        // [ L ] [ F ] [ R ] [ B ]
        //       [ D ]
        int[][] gridOrder = new int[][]{
                { -1, 0, -1, -1 }, // fila 0 (U centrado)
                { 4,  2,  1,  5 }, // fila 1 (L F R B)
                { -1, 3, -1, -1 }  // fila 2 (D centrado)
        };

        public CubeView(Activity ctx) {
            super(ctx);
            setFocusable(true);
            setClickable(true);
            detector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { touchX = e.getX(); touchY = e.getY(); return true; }
                @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                    // Ayuda: mostrar qué cara tocas
                    int[] rc = faceAt(e.getX(), e.getY());
                    if (rc != null) {
                        int face = rc[2];
                        Toast.makeText(getContext(), faceName(face) + " (desliza para girar)", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
            });
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawColor(Color.parseColor("#0b0f14"));

            float w = getWidth();
            float h = getHeight();

            int tiles = 12; // 3x4 caras en ancho de la malla virtual
            pad = Math.min(w, h) / 60f;
            float cell = Math.min(w / 12f, h / 9f); // tamaño base para sticker

            // Dibujar cada cara en la malla
            for (int row = 0; row < gridOrder.length; row++) {
                for (int col = 0; col < gridOrder[row].length; col++) {
                    int face = gridOrder[row][col];
                    if (face < 0) continue;
                    float baseX = (col * 3) * cell + pad * 2;
                    float baseY = (row * 3) * cell + pad * 2 + (h - cell * 9) / 2f;

                    // Marco de la cara
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.argb(35, 255, 255, 255));
                    c.drawRoundRect(baseX - pad, baseY - pad, baseX + cell*3 + pad, baseY + cell*3 + pad, pad, pad, p);

                    // Stickers 3x3
                    for (int i = 0; i < 9; i++) {
                        int r = i / 3, k = i % 3;
                        float x = baseX + k * cell;
                        float y = baseY + r * cell;
                        tile.set(x + pad, y + pad, x + cell - pad, y + cell - pad);
                        p.setColor(Cube.PALETTE[cube.faces[face][i]]);
                        c.drawRoundRect(tile, pad, pad, p);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(2);
                        p.setColor(Color.BLACK);
                        c.drawRoundRect(tile, pad, pad, p);
                        p.setStyle(Paint.Style.FILL);
                    }

                    // Nombre de cara
                    p.setColor(Color.LTGRAY);
                    p.setTextSize(cell * 0.5f);
                    p.setFakeBoldText(true);
                    c.drawText(faceName(face), baseX, baseY - pad, p);
                }
            }

            // Instrucciones
            p.setColor(Color.WHITE);
            p.setTextSize(Math.max(28, getWidth() * 0.035f));
            p.setFakeBoldText(false);
            c.drawText("Toca una cara y DESLIZA (↑ ↓ ← →) para girar.", pad * 2, h - pad * 6, p);

            // Mensaje de resuelto (throttle para no spamear)
            if (cube.isSolved() && SystemClock.uptimeMillis() - lastSolvedToastAt > 1500) {
                lastSolvedToastAt = SystemClock.uptimeMillis();
                Toast.makeText(getContext(), "¡Resuelto! ✨", Toast.LENGTH_SHORT).show();
            }
        }

        private String faceName(int f) {
            switch (f) {
                case 0: return "U"; // Up
                case 1: return "R";
                case 2: return "F";
                case 3: return "D";
                case 4: return "L";
                case 5: return "B";
            }
            return "?";
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (detector.onTouchEvent(event)) return true;

            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - touchX;
                float dy = event.getY() - touchY;
                float absx = Math.abs(dx), absy = Math.abs(dy);
                float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

                int[] rc = faceAt(touchX, touchY);
                if (rc != null && (absx > slop || absy > slop)) {
                    int face = rc[2];
                    int row = rc[3];
                    int col = rc[4];
                    String mv = gestureToMove(face, row, col, dx, dy);
                    if (mv != null) applyMove(mv, true);
                }
            }
            return true;
        }

        void applyMove(String mv, boolean record) {
            cube.move(mv);
            if (record) { history.push(mv); redo = new MoveHistory(); }
            invalidate();
        }

        void undo() {
            String m = history.pop();
            if (m == null) return;
            String inv = invert(m);
            redo.push(m);
            cube.move(inv);
            invalidate();
        }

        void redo() {
            String m = redo.pop();
            if (m == null) return;
            cube.move(m);
            history.push(m);
            invalidate();
        }

        String invert(String m) {
            if (m.endsWith("2")) return m; // doble es su propio inverso
            if (m.endsWith("'")) return String.valueOf(m.charAt(0));
            return m + "'";
        }

        void scramble(int n) {
            String[] base = {"U","D","L","R","F","B"};
            Random r = new Random();
            for (int i=0;i<n;i++) {
                String m = base[r.nextInt(base.length)];
                int t = r.nextInt(3);
                if (t==1) m += "'"; else if (t==2) m += "2";
                applyMove(m, true);
            }
        }

        // Detecta qué cara/pegatina fue tocada. Devuelve {gridRow, gridCol, face, rowInFace, colInFace}
        private int[] faceAt(float x, float y) {
            float w = getWidth(); float h = getHeight();
            float cell = Math.min(w / 12f, h / 9f);
            float offsetY = (h - cell * 9) / 2f;
            for (int gr = 0; gr < gridOrder.length; gr++) {
                for (int gc = 0; gc < gridOrder[gr].length; gc++) {
                    int face = gridOrder[gr][gc]; if (face < 0) continue;
                    float baseX = (gc * 3) * cell + pad * 2;
                    float baseY = (gr * 3) * cell + pad * 2 + offsetY;
                    float maxX = baseX + cell * 3; float maxY = baseY + cell * 3;
                    if (x >= baseX && x <= maxX && y >= baseY && y <= maxY) {
                        int col = (int)((x - baseX) / cell);
                        int row = (int)((y - baseY) / cell);
                        return new int[]{gr, gc, face, row, col};
                    }
                }
            }
            return null;
        }

        // Traduce un gesto de swipe sobre una cara/pegatina a un movimiento estándar.
        private String gestureToMove(int face, int row, int col, float dx, float dy) {
            boolean horizontal = Math.abs(dx) > Math.abs(dy);
            // Reglas simples: swipes horizontales giran filas; verticales, columnas, respecto a la cara.
            switch (face) {
                case 2: // F
                    if (horizontal) return row==0? "U" : row==1? (dx>0?"R":"L'") : "D'"; // heurístico
                    else return col==0? (dy>0?"L":"L'") : col==1? (dy>0?"F":"F'") : (dy>0?"R'":"R");
                case 5: // B (inversa de F)
                    if (horizontal) return row==0? "U'" : row==1? (dx>0?"L":"R'") : "D";
                    else return col==0? (dy>0?"R":"R'") : col==1? (dy>0?"B":"B'") : (dy>0?"L'":"L");
                case 0: // U
                    if (horizontal) return dx>0? "U" : "U'";
                    else return dy>0? "F" : "B'";
                case 3: // D
                    if (horizontal) return dx>0? "D'" : "D";
                    else return dy>0? "B" : "F'";
                case 1: // R
                    if (horizontal) return dy<0? "U'" : "D"; // arrastre izq/der no intuitivo; usar vertical
                    else return dy>0? "R" : "R'";
                case 4: // L
                    if (horizontal) return dy<0? "U" : "D'";
                    else return dy>0? "L'" : "L";
            }
            return null;
        }
    }

    // ------------------------- HUD (botones flotantes) -------------------------

    static class HUD extends View {
        final CubeView cube;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final List<Button> buttons = new ArrayList<>();
        float lastX, lastY;

        static class Button {
            String label; Runnable onClick; RectF r = new RectF();
            Button(String l, Runnable a){label=l; onClick=a;}
        }

        public HUD(Activity ctx, CubeView cubeView) {
            super(ctx);
            this.cube = cubeView;
            setClickable(true);
            // Botones: Mezclar, Reset, Deshacer, Rehacer, Ayuda
            buttons.add(new Button("Mezclar", () -> cube.scramble(25)));
            buttons.add(new Button("Reset", () -> { cube.cube.reset(); cube.history = new MoveHistory(); cube.redo = new MoveHistory(); cube.invalidate(); }));
            buttons.add(new Button("Undo", cube::undo));
            buttons.add(new Button("Redo", cube::redo));
            buttons.add(new Button("Ayuda", () -> Toast.makeText(getContext(), "Desliza sobre una cara para girar. Mantén el ritmo y observa los colores centrales.", Toast.LENGTH_LONG).show()));
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float pad = Math.max(12, getWidth()*0.012f);
            float w = getWidth();
            float y = pad * 1.2f;

            float x = pad;
            for (Button b : buttons) {
                float bw = Math.max(150, getWidth()*0.2f);
                float bh = Math.max(60, getWidth()*0.08f);
                b.r.set(x, y, x + bw, y + bh);

                // Sombra
                p.setColor(Color.argb(80, 0, 0, 0));
                c.drawRoundRect(b.r.left+6, b.r.top+8, b.r.right+6, b.r.bottom+8, 24, 24, p);

                // Botón
                p.setColor(Color.parseColor("#12202f"));
                c.drawRoundRect(b.r, 24, 24, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                p.setColor(Color.parseColor("#2a9df4"));
                c.drawRoundRect(b.r, 24, 24, p);
                p.setStyle(Paint.Style.FILL);

                // Texto
                p.setColor(Color.WHITE);
                p.setTextSize(Math.max(28, getWidth()*0.04f));
                p.setFakeBoldText(true);
                float tx = b.r.left + 24;
                float ty = b.r.centerY() + p.getTextSize()/3f;
                c.drawText(b.label, tx, ty, p);

                x += bw + pad;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                for (Button b : buttons) {
                    if (b.r.contains(e.getX(), e.getY())) {
                        b.onClick.run();
                        invalidate();
                        return true;
                    }
                }
            }
            return true;
        }
    }
}
