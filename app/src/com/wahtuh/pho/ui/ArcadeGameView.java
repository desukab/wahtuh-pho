package com.wahtuh.pho.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

public final class ArcadeGameView extends View {
    public interface Listener {
        void onState(int score, int aux, boolean over);
    }

    public static final int SNAKE = 0;
    public static final int BREAKOUT = 1;
    public static final int PONG = 2;

    private static final int SNAKE_COLS = 15;
    private static final int SNAKE_ROWS = 20;

    private final Paint fill = new Paint();
    private final Paint stroke = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scan = new Paint();
    private final Random random = new Random();
    private final RectF field = new RectF();
    private final RectF paddle = new RectF();
    private final RectF brick = new RectF();
    private final Deque<int[]> snake = new ArrayDeque<>();

    private Listener listener;
    private int mode = SNAKE;
    private int score;
    private int aux;
    private boolean running;
    private boolean over;
    private boolean won;
    private long lastFrame;
    private long lastStep;
    private long serveAt;

    private float boardLeft;
    private float boardTop;
    private float cell;
    private int dirX = 1;
    private int dirY;
    private int nextX = 1;
    private int nextY;
    private int foodX;
    private int foodY;
    private long stepMs = 135;

    private float paddleX;
    private float paddleW;
    private float paddleH;
    private float paddleY;
    private float ballX;
    private float ballY;
    private float ballDX;
    private float ballDY;
    private float ballR;
    private int lives;
    private boolean[][] bricks;
    private final int brickCols = 7;
    private final int brickRows = 5;
    private float brickW;
    private float brickH;

    private float playerY;
    private float cpuY;
    private float pongPaddleH;
    private float pongPaddleW;
    private int playerScore;
    private int cpuScore;
    private float downX;
    private float downY;
    private boolean swiped;

    public ArcadeGameView(Context context) {
        this(context, null);
    }

    public ArcadeGameView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ArcadeGameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setFocusable(true);
        setClickable(true);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        fill.setAntiAlias(false);
        stroke.setAntiAlias(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        text.setTypeface(Ui.pixel(context));
        text.setTextAlign(Paint.Align.CENTER);
        scan.setColor(0x22000000);
        scan.setStrokeWidth(dp(1));
        reset();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setMode(int value) {
        mode = value;
        reset();
    }

    public int mode() {
        return mode;
    }

    public int score() {
        return score;
    }

    public int aux() {
        return aux;
    }

    public boolean won() {
        return won;
    }

    public void reset() {
        over = false;
        won = false;
        running = true;
        lastFrame = 0;
        lastStep = 0;
        serveAt = 0;
        if (mode == SNAKE) {
            resetSnake();
        } else if (mode == BREAKOUT) {
            resetBreakout();
        } else {
            resetPong();
        }
        notifyState();
        invalidate();
    }

    private void resetSnake() {
        score = 0;
        aux = 0;
        dirX = 1;
        dirY = 0;
        nextX = 1;
        nextY = 0;
        stepMs = 135;
        snake.clear();
        int cx = SNAKE_COLS / 2;
        int cy = SNAKE_ROWS / 2;
        for (int i = 0; i < 4; i++) snake.addLast(new int[]{cx - i, cy});
        aux = snake.size();
        spawnFood();
        layoutSnake();
    }

    private void spawnFood() {
        for (int tries = 0; tries < 500; tries++) {
            int x = random.nextInt(SNAKE_COLS);
            int y = random.nextInt(SNAKE_ROWS);
            if (!snakeHas(x, y)) {
                foodX = x;
                foodY = y;
                return;
            }
        }
        foodX = 0;
        foodY = 0;
    }

    private boolean snakeHas(int x, int y) {
        for (int[] part : snake) {
            if (part[0] == x && part[1] == y) return true;
        }
        return false;
    }

    private void resetBreakout() {
        score = 0;
        aux = 3;
        lives = 3;
        bricks = new boolean[brickRows][brickCols];
        for (int r = 0; r < brickRows; r++) {
            for (int c = 0; c < brickCols; c++) bricks[r][c] = true;
        }
        layoutBreakout();
        paddleX = field.centerX();
        resetBall();
    }

    private void resetBall() {
        ballR = dp(5);
        ballX = paddleX;
        ballY = paddleY - dp(20);
        float speed = dp(320);
        ballDX = (random.nextBoolean() ? 1 : -1) * speed * 0.62f;
        ballDY = -speed;
        serveAt = SystemClock.uptimeMillis() + 450;
    }

    private void resetPong() {
        score = 0;
        aux = 0;
        playerScore = 0;
        cpuScore = 0;
        layoutPong();
        playerY = field.centerY();
        cpuY = field.centerY();
        servePong(random.nextBoolean());
    }

    private void servePong(boolean toPlayer) {
        ballR = dp(6);
        ballX = field.centerX();
        ballY = field.centerY();
        float speed = dp(330);
        ballDX = (toPlayer ? 1 : -1) * speed;
        ballDY = (random.nextBoolean() ? 1 : -1) * speed * 0.7f;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void layoutSnake() {
        float pad = dp(12);
        float w = getWidth() - pad * 2;
        float h = getHeight() - pad * 2;
        if (w <= 0 || h <= 0) return;
        cell = Math.min(w / SNAKE_COLS, h / SNAKE_ROWS);
        boardLeft = (getWidth() - cell * SNAKE_COLS) / 2f;
        boardTop = (getHeight() - cell * SNAKE_ROWS) / 2f;
        field.set(boardLeft, boardTop, boardLeft + cell * SNAKE_COLS,
                boardTop + cell * SNAKE_ROWS);
    }

    private void layoutBreakout() {
        float pad = dp(12);
        field.set(pad, pad, getWidth() - pad, getHeight() - pad);
        paddleW = Math.max(dp(74), field.width() * 0.27f);
        paddleH = dp(11);
        paddleY = field.bottom - dp(23);
        ballR = dp(5);
        float gap = dp(3);
        brickW = (field.width() - gap * (brickCols - 1)) / brickCols;
        brickH = Math.max(dp(13), field.height() * 0.045f);
    }

    private void layoutPong() {
        float pad = dp(12);
        field.set(pad, pad, getWidth() - pad, getHeight() - pad);
        pongPaddleW = dp(9);
        pongPaddleH = Math.max(dp(64), field.height() * 0.18f);
        ballR = dp(6);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutSnake();
        layoutBreakout();
        layoutPong();
        if (mode == BREAKOUT) resetBreakout();
        if (mode == PONG) resetPong();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        lastFrame = 0;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (running) step(SystemClock.uptimeMillis());
        if (mode == SNAKE) {
            drawSnake(canvas);
        } else if (mode == BREAKOUT) {
            drawBreakout(canvas);
        } else {
            drawPong(canvas);
        }
        drawScanlines(canvas);
        if (over) drawGameOver(canvas);
        if (running && !over) postInvalidateOnAnimation();
    }

    private void drawField(Canvas canvas) {
        fill.setColor(Ui.BG);
        canvas.drawRect(0, 0, getWidth(), getHeight(), fill);
        fill.setColor(Ui.BG2);
        canvas.drawRect(field, fill);
        stroke.setColor(Ui.LINE);
        canvas.drawRect(field, stroke);
    }

    private void drawSnake(Canvas canvas) {
        drawField(canvas);
        stroke.setColor(Ui.LINE);
        stroke.setAlpha(55);
        for (int x = 1; x < SNAKE_COLS; x++) {
            float px = boardLeft + x * cell;
            canvas.drawLine(px, boardTop, px, field.bottom, stroke);
        }
        for (int y = 1; y < SNAKE_ROWS; y++) {
            float py = boardTop + y * cell;
            canvas.drawLine(boardLeft, py, field.right, py, stroke);
        }
        stroke.setAlpha(255);

        fill.setColor(Ui.MAGENTA);
        canvas.drawRect(boardLeft + foodX * cell + cell * 0.2f,
                boardTop + foodY * cell + cell * 0.2f,
                boardLeft + (foodX + 1) * cell - cell * 0.2f,
                boardTop + (foodY + 1) * cell - cell * 0.2f, fill);

        int index = 0;
        for (int[] part : snake) {
            fill.setColor(index == 0 ? Ui.ACCENT : (index % 2 == 0 ? Ui.ACCENT2 : Ui.TEXT));
            float inset = index == 0 ? cell * 0.08f : cell * 0.16f;
            canvas.drawRect(boardLeft + part[0] * cell + inset,
                    boardTop + part[1] * cell + inset,
                    boardLeft + (part[0] + 1) * cell - inset,
                    boardTop + (part[1] + 1) * cell - inset, fill);
            index++;
        }
    }

    private void drawBreakout(Canvas canvas) {
        drawField(canvas);
        stroke.setColor(Ui.LINE);
        stroke.setAlpha(45);
        canvas.drawLine(field.left, paddleY - dp(7), field.right, paddleY - dp(7), stroke);
        stroke.setAlpha(255);

        float gap = dp(3);
        float startY = field.top + dp(12);
        for (int r = 0; r < brickRows; r++) {
            if (bricks == null) break;
            for (int c = 0; c < brickCols; c++) {
                if (!bricks[r][c]) continue;
                float left = field.left + c * (brickW + gap);
                float top = startY + r * (brickH + gap);
                fill.setColor(r == 0 ? Ui.MAGENTA : r == 1 ? Ui.ACCENT2
                        : r == 2 ? Ui.ACCENT : r == 3 ? Ui.TEXT : Ui.MUTED);
                canvas.drawRect(left, top, left + brickW, top + brickH, fill);
                fill.setColor(Ui.alpha(Ui.BG, 0.32f));
                canvas.drawRect(left + dp(2), top + brickH - dp(2),
                        left + brickW - dp(2), top + brickH, fill);
            }
        }

        paddle.set(paddleX - paddleW / 2, paddleY, paddleX + paddleW / 2,
                paddleY + paddleH);
        fill.setColor(Ui.ACCENT);
        canvas.drawRect(paddle, fill);
        fill.setColor(Ui.MAGENTA);
        canvas.drawRect(paddle.left + dp(2), paddle.bottom - dp(3),
                paddle.right - dp(2), paddle.bottom, fill);

        fill.setColor(Ui.TEXT);
        canvas.drawCircle(ballX, ballY, ballR, fill);
        fill.setColor(Ui.ACCENT2);
        canvas.drawCircle(ballX, ballY, ballR * 0.48f, fill);

        for (int i = 0; i < lives; i++) {
            fill.setColor(Ui.ACCENT);
            canvas.drawRect(field.left + dp(6) + i * dp(10), field.bottom - dp(8),
                    field.left + dp(11) + i * dp(10), field.bottom - dp(5), fill);
        }
    }

    private void drawPong(Canvas canvas) {
        drawField(canvas);
        stroke.setColor(Ui.LINE);
        stroke.setAlpha(130);
        for (float y = field.top + dp(8); y < field.bottom; y += dp(18)) {
            canvas.drawLine(field.centerX(), y, field.centerX(), y + dp(8), stroke);
        }
        stroke.setAlpha(255);

        float left = field.left + dp(12);
        float right = field.right - dp(12);
        fill.setColor(Ui.ACCENT);
        canvas.drawRect(left, playerY - pongPaddleH / 2, left + pongPaddleW,
                playerY + pongPaddleH / 2, fill);
        fill.setColor(Ui.MAGENTA);
        canvas.drawRect(right - pongPaddleW, cpuY - pongPaddleH / 2, right,
                cpuY + pongPaddleH / 2, fill);
        fill.setColor(Ui.TEXT);
        canvas.drawCircle(ballX, ballY, ballR, fill);
        fill.setColor(Ui.ACCENT2);
        canvas.drawCircle(ballX, ballY, ballR * 0.45f, fill);

        text.setColor(Ui.MUTED);
        text.setTextSize(dp(18));
        canvas.drawText(Integer.toString(playerScore), field.centerX() - dp(34),
                field.top + dp(24), text);
        canvas.drawText(Integer.toString(cpuScore), field.centerX() + dp(34),
                field.top + dp(24), text);
    }

    private void drawScanlines(Canvas canvas) {
        for (float y = 0; y < getHeight(); y += dp(4)) {
            canvas.drawLine(0, y, getWidth(), y, scan);
        }
    }

    private void drawGameOver(Canvas canvas) {
        fill.setColor(Ui.alpha(Ui.BG, 0.86f));
        canvas.drawRect(field, fill);
        stroke.setColor(Ui.ACCENT);
        canvas.drawRect(field, stroke);
        text.setColor(won ? Ui.ACCENT : Ui.MAGENTA);
        text.setTextSize(dp(17));
        canvas.drawText(won ? "STAGE CLEAR" : "GAME OVER", field.centerX(),
                field.centerY() - dp(8), text);
        text.setColor(Ui.MUTED);
        text.setTextSize(dp(8));
        canvas.drawText("TAP TO PLAY AGAIN", field.centerX(),
                field.centerY() + dp(22), text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!running) return false;
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            swiped = false;
            if (over) {
                reset();
                return true;
            }
            if (mode == BREAKOUT) {
                movePaddle(e.getX());
            } else if (mode == PONG) {
                movePlayer(e.getY());
            }
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (mode == SNAKE) {
                steer(e.getX() - downX, e.getY() - downY);
            } else if (mode == BREAKOUT) {
                movePaddle(e.getX());
            } else if (mode == PONG) {
                movePlayer(e.getY());
            }
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            if (mode == SNAKE && !swiped) {
                steer(e.getX() - downX, e.getY() - downY);
            }
            performClick();
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void steer(float dx, float dy) {
        float limit = dp(16);
        if (Math.abs(dx) < limit && Math.abs(dy) < limit) return;
        if (Math.abs(dx) > Math.abs(dy)) {
            int direction = dx > 0 ? 1 : -1;
            if (direction != -dirX) {
                nextX = direction;
                nextY = 0;
                swiped = true;
            }
        } else {
            int direction = dy > 0 ? 1 : -1;
            if (direction != -dirY) {
                nextY = direction;
                nextX = 0;
                swiped = true;
            }
        }
    }

    private void movePaddle(float x) {
        paddleX = clamp(x, field.left + paddleW / 2, field.right - paddleW / 2);
        if (ballY > paddleY - dp(4) && ballDY > 0) ballX = paddleX;
    }

    private void movePlayer(float y) {
        playerY = clamp(y, field.top + pongPaddleH / 2, field.bottom - pongPaddleH / 2);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void step(long now) {
        if (mode == SNAKE) {
            if (over) return;
            if (lastStep == 0) lastStep = now;
            if (now - lastStep >= stepMs) {
                lastStep = now;
                stepSnake();
            }
        } else if (mode == BREAKOUT) {
            stepBreakout(now);
        } else {
            stepPong(now);
        }
    }

    private void stepSnake() {
        if (over) return;
        dirX = nextX;
        dirY = nextY;
        int[] head = snake.peekFirst();
        if (head == null) return;
        int nx = head[0] + dirX;
        int ny = head[1] + dirY;
        if (nx < 0 || ny < 0 || nx >= SNAKE_COLS || ny >= SNAKE_ROWS || snakeHas(nx, ny)) {
            over = true;
            notifyState();
            invalidate();
            return;
        }
        snake.addFirst(new int[]{nx, ny});
        if (nx == foodX && ny == foodY) {
            score += 10;
            stepMs = Math.max(72, stepMs - 3);
            spawnFood();
        } else {
            snake.removeLast();
        }
        aux = snake.size();
        notifyState();
    }

    private void stepBreakout(long now) {
        if (over) return;
        if (now < serveAt) return;
        if (lastFrame == 0) lastFrame = now;
        float dt = Math.min(0.033f, (now - lastFrame) / 1000f);
        lastFrame = now;

        float speed = (float) Math.sqrt(ballDX * ballDX + ballDY * ballDY);
        ballX += ballDX * dt;
        ballY += ballDY * dt;

        if (ballX - ballR < field.left) {
            ballX = field.left + ballR;
            ballDX = Math.abs(ballDX);
        } else if (ballX + ballR > field.right) {
            ballX = field.right - ballR;
            ballDX = -Math.abs(ballDX);
        }
        if (ballY - ballR < field.top) {
            ballY = field.top + ballR;
            ballDY = Math.abs(ballDY);
        }

        paddle.set(paddleX - paddleW / 2, paddleY, paddleX + paddleW / 2,
                paddleY + paddleH);
        if (ballDY > 0 && ballY + ballR >= paddleY && ballY - ballR <= paddleY + paddleH
                && ballX >= paddle.left - ballR && ballX <= paddle.right + ballR) {
            ballY = paddleY - ballR;
            ballDY = -Math.abs(ballDY);
            ballDX += (ballX - paddleX) * 2.2f;
            float total = (float) Math.sqrt(ballDX * ballDX + ballDY * ballDY);
            if (total > 0) {
                ballDX = ballDX / total * speed;
                ballDY = ballDY / total * speed;
            }
        }

        if (bricks != null) {
            float gap = dp(3);
            float startY = field.top + dp(12);
            for (int r = 0; r < brickRows; r++) {
                for (int c = 0; c < brickCols; c++) {
                    if (!bricks[r][c]) continue;
                    float left = field.left + c * (brickW + gap);
                    float top = startY + r * (brickH + gap);
                    brick.set(left, top, left + brickW, top + brickH);
                    if (ballX + ballR < brick.left || ballX - ballR > brick.right
                            || ballY + ballR < brick.top || ballY - ballR > brick.bottom) {
                        continue;
                    }
                    bricks[r][c] = false;
                    score += 10;
                    ballDY = -ballDY;
                    ballY = ballDY > 0 ? brick.bottom + ballR : brick.top - ballR;
                    float boost = 1.02f;
                    ballDX *= boost;
                    ballDY *= boost;
                    notifyState();
                    break;
                }
            }
        }

        if (!hasBricks()) {
            won = true;
            over = true;
            notifyState();
            invalidate();
            return;
        }
        if (ballY - ballR > field.bottom) {
            lives--;
            aux = lives;
            if (lives <= 0) {
                over = true;
                notifyState();
                invalidate();
            } else {
                resetBall();
                notifyState();
            }
        }
    }

    private boolean hasBricks() {
        if (bricks == null) return false;
        for (int r = 0; r < brickRows; r++) {
            for (int c = 0; c < brickCols; c++) {
                if (bricks[r][c]) return true;
            }
        }
        return false;
    }

    private void stepPong(long now) {
        if (over) return;
        if (lastFrame == 0) lastFrame = now;
        float dt = Math.min(0.033f, (now - lastFrame) / 1000f);
        lastFrame = now;

        float cpuSpeed = dp(300) * dt;
        float target = ballY;
        if (cpuY < target) cpuY = Math.min(target, cpuY + cpuSpeed);
        else if (cpuY > target) cpuY = Math.max(target, cpuY - cpuSpeed);
        cpuY = clamp(cpuY, field.top + pongPaddleH / 2, field.bottom - pongPaddleH / 2);

        ballX += ballDX * dt;
        ballY += ballDY * dt;
        if (ballY - ballR < field.top) {
            ballY = field.top + ballR;
            ballDY = Math.abs(ballDY);
        } else if (ballY + ballR > field.bottom) {
            ballY = field.bottom - ballR;
            ballDY = -Math.abs(ballDY);
        }

        float left = field.left + dp(12);
        float right = field.right - dp(12);
        if (ballDX < 0 && ballX - ballR <= left + pongPaddleW
                && ballX + ballR >= left
                && ballY + ballR >= playerY - pongPaddleH / 2
                && ballY - ballR <= playerY + pongPaddleH / 2) {
            ballX = left + pongPaddleW + ballR;
            ballDX = Math.abs(ballDX);
            ballDY += (ballY - playerY) * 2.1f;
            normalizePong();
        }
        if (ballDX > 0 && ballX + ballR >= right - pongPaddleW
                && ballX - ballR <= right
                && ballY + ballR >= cpuY - pongPaddleH / 2
                && ballY - ballR <= cpuY + pongPaddleH / 2) {
            ballX = right - pongPaddleW - ballR;
            ballDX = -Math.abs(ballDX);
            ballDY += (ballY - cpuY) * 1.4f;
            normalizePong();
        }

        if (ballX + ballR < field.left) {
            cpuScore++;
            score = playerScore;
            aux = cpuScore;
            if (cpuScore >= 7) {
                over = true;
                won = false;
                notifyState();
                invalidate();
            } else {
                servePong(false);
                notifyState();
            }
        } else if (ballX - ballR > field.right) {
            playerScore++;
            score = playerScore;
            aux = cpuScore;
            if (playerScore >= 7) {
                over = true;
                won = true;
                notifyState();
                invalidate();
            } else {
                servePong(true);
                notifyState();
            }
        }
    }

    private void normalizePong() {
        float speed = dp(330);
        float total = (float) Math.sqrt(ballDX * ballDX + ballDY * ballDY);
        if (total <= 0) return;
        ballDX = ballDX / total * speed;
        ballDY = ballDY / total * speed;
    }

    private void notifyState() {
        if (listener != null) listener.onState(score, aux, over);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
