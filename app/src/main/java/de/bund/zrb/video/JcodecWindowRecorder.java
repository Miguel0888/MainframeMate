package de.bund.zrb.video;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;
import de.bund.zrb.config.VideoConfig;
import de.bund.zrb.win.WindowCapture;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import javax.sound.sampled.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Pure Java window recorder using JCodec (no audio). */
public class JcodecWindowRecorder implements Closeable {
    private final WinDef.HWND hWnd;
    private final Path outFileRequested;
    private final int fpsArg;
    private final boolean audioEnabled; // NEU

    private AWTSequenceEncoder encoder;
    private Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger frameCount = new AtomicInteger(0);

    // Pipeline: parallele Frame-Vorbereitung
    private int numThreads;
    private ExecutorService preparePool;
    private Thread encoderThread;
    private final AtomicInteger captureSeq = new AtomicInteger(0);
    private final ConcurrentSkipListMap<Integer, Picture> preparedMap = new ConcurrentSkipListMap<>();
    /** Max Frames im Puffer bevor Backpressure einsetzt (verhindert Speicherüberlauf). */
    private static final int MAX_PREPARED_BACKLOG = 120;

    private Path outFileEffective;

    /** Segment-Dauer in Sekunden – der Encoder flusht alle N Sekunden in eine neue Datei. */
    private static final int SEGMENT_DURATION_SEC = 30;
    private final List<Path> segmentFiles = new ArrayList<>();
    private int segmentIndex;
    private long segmentStartMs;
    private int segmentFrameCount;
    private int effectiveFps;

    // Robot-based fallback for Swing windows (Win32 capture often yields black)
    private Robot robot;
    private boolean useRobotCapture;

    // Audio (optional)
    private Thread audioThread; // NEU
    private TargetDataLine audioLine; // NEU
    private Path audioOutPath; // NEU

    // Overlays – kompatible API zu WindowRecorder
    private final CaptionOverlay caption = new CaptionOverlay();
    private final SubtitleOverlay subtitle = new SubtitleOverlay();
    private final ActionOverlay action = new ActionOverlay();

    public JcodecWindowRecorder(WinDef.HWND hWnd, Path outFile, int fps) {
        this(hWnd, outFile, fps, false);
    }
    public JcodecWindowRecorder(WinDef.HWND hWnd, Path outFile, int fps, boolean audioEnabled) {
        this.hWnd = hWnd;
        this.outFileRequested = outFile;
        this.fpsArg = fps;
        this.audioEnabled = audioEnabled;
    }

    private static volatile JcodecWindowRecorder CURRENT;
    public static JcodecWindowRecorder getCurrentActive() { return CURRENT; }

    // Overlay-API
    public void setCaptionText(String text) { caption.setText(text); }
    public void setCaptionVisible(boolean visible) { caption.setVisible(visible); }
    public void setCaptionStyle(WindowRecorder.OverlayStyle style) { caption.setStyle(style); }
    public void setSubtitleText(String text) { subtitle.setText(text); }
    public void setSubtitleVisible(boolean visible) { subtitle.setVisible(visible); }
    public void setSubtitleStyle(WindowRecorder.OverlayStyle style) { subtitle.setStyle(style); }
    public void setActionText(String text) { action.setText(text); }
    public void setActionVisible(boolean visible) { action.setVisible(visible); }
    public void setActionStyle(WindowRecorder.OverlayStyle style) { action.setStyle(style); }

    public void start() throws Exception {
        CURRENT = this;
        if (running.get()) return;

        // Try Robot-based capture first (reliable for Swing), then Win32 WindowCapture
        try { robot = new Robot(); } catch (Throwable t) { robot = null; }

        BufferedImage probe = captureFrame();
        if (probe == null) throw new IllegalStateException("WindowCapture liefert null – weder Robot noch Win32 konnten ein Bild liefern");

        System.out.println("[JcodecRec] Probe OK: " + probe.getWidth() + "x" + probe.getHeight()
                + " (useRobot=" + useRobotCapture + ")");

        int w = probe.getWidth();
        int h = probe.getHeight();
        if (VideoConfig.isEnforceEvenDims()) { w = makeEven(w); h = makeEven(h); }

        effectiveFps = (fpsArg > 0) ? fpsArg : VideoConfig.getFps();
        if (effectiveFps <= 0) effectiveFps = 15;

        outFileEffective = outFileRequested;

        // Segment-basiertes Recording: alle SEGMENT_DURATION_SEC Sekunden wird ein neues Segment auf die Platte geschrieben
        segmentIndex = 0;
        segmentFiles.clear();
        startNewSegment();

        final int frameIntervalMs = (int) Math.max(1, Math.round(1000.0 / Math.max(1, effectiveFps)));

        // Optionales Audio vorbereiten (separate WAV)
        if (audioEnabled) {
            audioOutPath = ensureSiblingWithExt(outFileEffective, ".wav");
            startAudioCapture(audioOutPath);
        }

        running.set(true);
        frameCount.set(0);

        numThreads = VideoConfig.getJcodecThreads();
        if (numThreads < 1) numThreads = 1;

        if (numThreads <= 1) {
            startSingleThread(frameIntervalMs);
        } else {
            startPipeline(frameIntervalMs, numThreads);
        }
    }

    // ===== Frame-Vorbereitung (extrahiert für Wiederverwendung) =====

    /**
     * Bereitet ein rohes Capture-Bild für den Encoder vor:
     * Even-Dims erzwingen, Farbtyp auf TYPE_3BYTE_BGR konvertieren, Overlays zeichnen.
     */
    private BufferedImage prepareFrame(BufferedImage img) {
        int iw = img.getWidth(), ih = img.getHeight();
        int tw = iw, th = ih;
        if (VideoConfig.isEnforceEvenDims()) { tw = makeEven(iw); th = makeEven(ih); }
        BufferedImage frame;
        if (tw != iw || th != ih) {
            frame = new BufferedImage(tw, th, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = frame.createGraphics();
            try { g.drawImage(img, 0, 0, tw, th, null); applyOverlays(frame); } finally { g.dispose(); }
        } else {
            if (img.getType() != BufferedImage.TYPE_3BYTE_BGR) {
                frame = new BufferedImage(iw, ih, BufferedImage.TYPE_3BYTE_BGR);
                Graphics2D g = frame.createGraphics();
                try { g.drawImage(img, 0, 0, null); } finally { g.dispose(); }
            } else {
                frame = img;
            }
            applyOverlays(frame);
        }
        return frame;
    }

    // ===== Single-Thread Modus (Threads=1, kompatibel mit bisherigem Verhalten) =====

    private void startSingleThread(final int frameIntervalMs) {
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                int nullCount = 0;
                while (running.get()) {
                    try {
                        BufferedImage img = captureFrame();
                        if (img != null) {
                            BufferedImage frame = prepareFrame(img);
                            encoder.encodeImage(frame);
                            segmentFrameCount++;
                            int fc = frameCount.incrementAndGet();
                            if (fc == 1) {
                                System.out.println("[JcodecRec] Erster Frame encoded: " + frame.getWidth() + "x" + frame.getHeight());
                            } else if (fc % 200 == 0) {
                                System.out.println("[JcodecRec] " + fc + " Frames (Segment " + segmentIndex + ")");
                            }
                            nullCount = 0;

                            // Segment-Rotation
                            if (segmentFrameCount > 0
                                    && System.currentTimeMillis() - segmentStartMs >= SEGMENT_DURATION_SEC * 1000L) {
                                try {
                                    rotateSegment();
                                } catch (Exception e) {
                                    System.err.println("[JcodecRec] Segment-Rotation fehlgeschlagen: " + e.getMessage());
                                    running.set(false);
                                }
                            }
                        } else {
                            nullCount++;
                            if (nullCount <= 3 || nullCount % 50 == 0) {
                                System.err.println("[JcodecRec] Capture liefert null (nullCount=" + nullCount + ")");
                            }
                        }
                        Thread.sleep(frameIntervalMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        running.set(false);
                    } catch (Throwable t) {
                        System.err.println("[JcodecRec] Fehler in Capture-Loop nach " + frameCount.get()
                                + " Frames: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                        t.printStackTrace(System.err);
                        running.set(false);
                    }
                }
                System.out.println("[JcodecRec] Worker beendet – " + frameCount.get() + " Frames aufgenommen");
            }
        }, "jcodec-window-recorder");
        worker.setDaemon(true);
        worker.start();
    }

    // ===== Pipeline-Modus (Threads>1: Capture → PreparePool → Encoder) =====

    private void startPipeline(final int frameIntervalMs, int threads) {
        captureSeq.set(0);
        preparedMap.clear();

        preparePool = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            private final AtomicInteger idx = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "jcodec-prepare-" + idx.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        System.out.println("[JcodecRec] Pipeline-Modus: " + threads + " Prepare-Threads, "
                + effectiveFps + " FPS Ziel");

        // --- Capture-Thread: nimmt Bilder auf und gibt sie in den Prepare-Pool ---
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                int nullCount = 0;
                while (running.get()) {
                    try {
                        // Backpressure: wenn der Encoder nicht hinterherkommt, Frames überspringen
                        if (preparedMap.size() > MAX_PREPARED_BACKLOG) {
                            Thread.sleep(1);
                            continue;
                        }

                        BufferedImage img = captureFrame();
                        if (img != null) {
                            final int seq = captureSeq.getAndIncrement();
                            final BufferedImage raw = img;
                            preparePool.submit(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        BufferedImage prepared = prepareFrame(raw);
                                        Picture pic = AWTUtil.fromBufferedImage(prepared, ColorSpace.RGB);
                                        preparedMap.put(seq, pic);
                                    } catch (Throwable t) {
                                        System.err.println("[JcodecRec] Prepare-Fehler seq=" + seq
                                                + ": " + t.getMessage());
                                    }
                                }
                            });
                            nullCount = 0;
                        } else {
                            nullCount++;
                            if (nullCount <= 3 || nullCount % 50 == 0) {
                                System.err.println("[JcodecRec] Capture liefert null (nullCount=" + nullCount + ")");
                            }
                        }
                        Thread.sleep(frameIntervalMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Throwable t) {
                        System.err.println("[JcodecRec] Capture-Fehler: " + t.getClass().getSimpleName()
                                + ": " + t.getMessage());
                        t.printStackTrace(System.err);
                        break;
                    }
                }
                System.out.println("[JcodecRec] Capture-Thread beendet – "
                        + captureSeq.get() + " Frames gecaptured");
            }
        }, "jcodec-capture");
        worker.setDaemon(true);

        // --- Encoder-Thread: nimmt vorbereitete Frames IN REIHENFOLGE und encodiert sequentiell ---
        encoderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int nextSeq = 0;
                int idleSpins = 0;
                while (running.get() || nextSeq < captureSeq.get()) {
                    Picture pic = preparedMap.remove(nextSeq);
                    if (pic != null) {
                        try {
                            encoder.encodeNativeFrame(pic);
                            segmentFrameCount++;
                            int fc = frameCount.incrementAndGet();
                            if (fc == 1) {
                                System.out.println("[JcodecRec] Erster Frame encoded: "
                                        + pic.getWidth() + "x" + pic.getHeight()
                                        + " (" + numThreads + " Threads)");
                            } else if (fc % 200 == 0) {
                                System.out.println("[JcodecRec] " + fc + " Frames (Segment "
                                        + segmentIndex + ", Backlog: " + preparedMap.size() + ")");
                            }
                            nextSeq++;
                            idleSpins = 0;

                            // Segment-Rotation
                            if (segmentFrameCount > 0
                                    && System.currentTimeMillis() - segmentStartMs >= SEGMENT_DURATION_SEC * 1000L) {
                                try {
                                    rotateSegment();
                                } catch (Exception e) {
                                    System.err.println("[JcodecRec] Segment-Rotation fehlgeschlagen: " + e.getMessage());
                                    running.set(false);
                                }
                            }
                        } catch (Throwable t) {
                            System.err.println("[JcodecRec] Encode-Fehler nach " + frameCount.get()
                                    + " Frames: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                            t.printStackTrace(System.err);
                            running.set(false);
                        }
                    } else {
                        idleSpins++;
                        // Nach Stop: 500 ms warten, dann aufgeben (fehlende Frames)
                        if (!running.get() && idleSpins > 500) {
                            if (nextSeq < captureSeq.get()) {
                                System.err.println("[JcodecRec] " + (captureSeq.get() - nextSeq)
                                        + " Frames konnten nicht encoded werden (Timeout)");
                            }
                            break;
                        }
                        try { Thread.sleep(1); } catch (InterruptedException ie) { break; }
                    }
                }
                System.out.println("[JcodecRec] Encoder-Thread beendet – "
                        + frameCount.get() + " Frames encoded");
            }
        }, "jcodec-encoder");
        encoderThread.setDaemon(true);

        worker.start();
        encoderThread.start();
    }

    public void stop() {
        running.set(false);

        // 1. Capture-Thread stoppen
        if (worker != null) { try { worker.join(5000); } catch (InterruptedException ignored) {} }

        // 2. Prepare-Pool abschließen (verbleibende Tasks abarbeiten lassen)
        if (preparePool != null) {
            preparePool.shutdown();
            try { preparePool.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }

        // 3. Encoder-Thread warten (verarbeitet verbleibende prepared Frames)
        if (encoderThread != null) { try { encoderThread.join(15000); } catch (InterruptedException ignored) {} }

        // Letztes Segment finalisieren
        try {
            if (encoder != null) {
                encoder.finish();
                encoder = null;
                if (segmentIndex < segmentFiles.size()) {
                    System.out.println("[JcodecRec] Segment " + segmentIndex + " gespeichert ("
                            + segmentFrameCount + " Frames): " + segmentFiles.get(segmentIndex));
                }
            }
        } catch (Exception e) {
            System.err.println("[JcodecRec] encoder.finish() Fehler: " + e.getMessage());
        }

        // Segmente zusammenführen
        mergeSegments();

        int fc = frameCount.get();
        System.out.println("[JcodecRec] stop() – " + fc + " Frames gesamt, Datei: " + outFileEffective);

        // Audio sauber stoppen
        stopAudioCapture();
        if (CURRENT == this) CURRENT = null;
    }

    @Override public void close() { stop(); }

    public Path getEffectiveOutput() { return outFileEffective != null ? outFileEffective : outFileRequested; }

    private static int makeEven(int v) { return (v & 1) == 1 ? v - 1 : v; }

    // ===== Segment Management =====

    /** Erzeugt den Pfad für ein Segment: <stem>_seg000.mp4 */
    private Path segmentPath(int idx) {
        String base = outFileRequested.toString();
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String ext = dot > 0 ? base.substring(dot) : ".mp4";
        return Paths.get(String.format("%s_seg%03d%s", stem, idx, ext));
    }

    /** Startet ein neues Segment (Encoder + Datei). */
    private void startNewSegment() throws Exception {
        Path p = segmentPath(segmentIndex);
        encoder = AWTSequenceEncoder.createSequenceEncoder(p.toFile(), effectiveFps);
        segmentFiles.add(p);
        segmentStartMs = System.currentTimeMillis();
        segmentFrameCount = 0;
    }

    /** Schließt das aktuelle Segment und öffnet das nächste. */
    private void rotateSegment() throws Exception {
        AWTSequenceEncoder old = encoder;
        encoder = null;  // Sicherheit: doppeltes finish() verhindern
        old.finish();
        System.out.println("[JcodecRec] Segment " + segmentIndex + " gespeichert ("
                + segmentFrameCount + " Frames): " + segmentFiles.get(segmentIndex));
        segmentIndex++;
        startNewSegment();
    }

    /**
     * Führt alle Segmentdateien in die finale Ausgabedatei zusammen.
     * Bei einem einzelnen Segment wird es einfach umbenannt.
     * Bei mehreren Segmenten werden die Frames re-encoded (verlustfrei für Screenrecordings).
     * Bei Fehler bleiben die Segmentdateien als Fallback erhalten.
     */
    private void mergeSegments() {
        // Leere / kaputte Segmente aussortieren
        List<Path> valid = new ArrayList<>();
        for (Path seg : segmentFiles) {
            try {
                if (Files.exists(seg) && Files.size(seg) > 1024) {
                    valid.add(seg);
                } else {
                    try { Files.deleteIfExists(seg); } catch (Throwable ignore) {}
                }
            } catch (Exception e) { /* skip */ }
        }

        if (valid.isEmpty()) {
            System.err.println("[JcodecRec] Keine gültigen Segmente vorhanden.");
            return;
        }

        if (valid.size() == 1) {
            // Einzelnes Segment → umbenennen
            try {
                Files.move(valid.get(0), outFileEffective, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                outFileEffective = valid.get(0);
            }
            return;
        }

        // Mehrere Segmente → zusammenführen
        System.out.println("[JcodecRec] Führe " + valid.size() + " Segmente zusammen …");
        long t0 = System.currentTimeMillis();
        try {
            AWTSequenceEncoder out = AWTSequenceEncoder.createSequenceEncoder(
                    outFileEffective.toFile(), effectiveFps);
            int totalFrames = 0;
            for (int i = 0; i < valid.size(); i++) {
                Path seg = valid.get(i);
                FrameGrab grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(seg.toFile()));
                Picture pic;
                while ((pic = grab.getNativeFrame()) != null) {
                    // FrameGrab liefert YUV – über BufferedImage zurück nach RGB für den Encoder
                    BufferedImage img = AWTUtil.toBufferedImage(pic);
                    out.encodeImage(img);
                    totalFrames++;
                }
                System.out.println("[JcodecRec]   Segment " + i + " gelesen (" + totalFrames + " Frames bisher)");
            }
            out.finish();
            long dt = System.currentTimeMillis() - t0;
            System.out.println("[JcodecRec] Zusammenführung abgeschlossen: " + totalFrames
                    + " Frames in " + dt + " ms → " + outFileEffective);

            // Segmentdateien löschen
            for (Path seg : valid) {
                try { Files.deleteIfExists(seg); } catch (Throwable ignore) {}
            }
        } catch (Exception e) {
            System.err.println("[JcodecRec] Zusammenführung fehlgeschlagen: " + e.getMessage());
            e.printStackTrace(System.err);
            System.out.println("[JcodecRec] Segmentdateien bleiben erhalten:");
            for (Path seg : valid) {
                System.out.println("  " + seg);
            }
            outFileEffective = valid.get(valid.size() - 1);
        }
    }

    /**
     * Captures a frame using the best available strategy.
     * First call probes both Win32 and Robot to decide which works;
     * subsequent calls use the chosen strategy for performance.
     */
    private BufferedImage captureFrame() {
        // Once we decided on Robot, stay with it
        if (useRobotCapture) {
            return captureViaRobot();
        }

        // Try Win32 WindowCapture first
        BufferedImage img = null;
        try {
            img = WindowCapture.capture(hWnd);
        } catch (Throwable t) {
            // Win32 capture failed
        }

        // Check if we got a usable (non-black) image
        if (img != null && !isBlackImage(img)) {
            return img;
        }

        // Fallback: Robot-based screen capture of window bounds
        BufferedImage robotImg = captureViaRobot();
        if (robotImg != null) {
            // Win32 failed, Robot works → switch permanently
            if (!useRobotCapture) {
                useRobotCapture = true;
                System.out.println("[JcodecRec] Wechsel zu Robot-Capture (Win32 liefert "
                        + (img == null ? "null" : "schwarzes Bild") + ")");
            }
            return robotImg;
        }

        // Both failed – return whatever we got (may be null or black)
        return img;
    }

    /**
     * Captures the window's client area via java.awt.Robot (screen capture).
     * This is very reliable for Swing windows since it simply reads the screen pixels.
     * Caveat: if other windows overlap, they appear in the capture.
     */
    private BufferedImage captureViaRobot() {
        if (robot == null) return null;
        try {
            // Get client area bounds in screen coordinates via WINDOWINFO
            WinUser.WINDOWINFO wi = new WinUser.WINDOWINFO();
            wi.cbSize = wi.size();
            if (!User32.INSTANCE.GetWindowInfo(hWnd, wi)) return null;
            RECT rc = wi.rcClient;
            int x = rc.left;
            int y = rc.top;
            int w = rc.right - rc.left;
            int h = rc.bottom - rc.top;
            if (w <= 0 || h <= 0) return null;
            return robot.createScreenCapture(new Rectangle(x, y, w, h));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Quick check: sample a few pixels to detect all-black images. */
    private static boolean isBlackImage(BufferedImage img) {
        if (img == null) return true;
        int w = img.getWidth(), h = img.getHeight();
        int stepX = Math.max(1, w / 12);
        int stepY = Math.max(1, h / 12);
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                if ((img.getRGB(x, y) & 0xFFFFFF) != 0) return false;
            }
        }
        return true;
    }

    private void applyOverlays(BufferedImage frame) {
        caption.paint(frame);
        subtitle.paint(frame);
        action.paint(frame);
    }

    // ===== Overlay-Implementation (angepasst) =====

    private static void paintOverlay(BufferedImage frame, String text, WindowRecorder.OverlayStyle st) {
        if (text == null || text.isEmpty() || st == null) return;
        Graphics2D g = (Graphics2D) frame.getGraphics();
        try { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); } catch (Throwable ignore) {}
        try {
            g.setFont(new Font(st.fontName, st.fontStyle, st.fontSizePt));
            FontMetrics fm = g.getFontMetrics();
            int maxW = (int) Math.max(60, frame.getWidth() * st.maxWidthRatio);
            List<String> lines = wrapLines(text, fm, maxW);
            int textW = 0; int textH = 0;
            for (String ln : lines) { textW = Math.max(textW, fm.stringWidth(ln)); textH += fm.getAscent() + fm.getDescent(); }
            textH += st.lineGapPx * Math.max(0, lines.size() - 1);
            int boxW = textW + 2 * st.paddingPx;
            int boxH = textH + 2 * st.paddingPx;
            int x; int y;
            if (st.posXPerc != null && st.posYPerc != null) {
                float px = clamp01(st.posXPerc.floatValue());
                float py = clamp01(st.posYPerc.floatValue());
                x = Math.round((frame.getWidth() - boxW) * px);
                y = Math.round((frame.getHeight() - boxH) * py);
            } else {
                if (st.hAlign == WindowRecorder.OverlayStyle.HAlign.LEFT) x = st.marginX;
                else if (st.hAlign == WindowRecorder.OverlayStyle.HAlign.RIGHT) x = frame.getWidth() - st.marginX - boxW;
                else x = (frame.getWidth() - boxW) / 2;
                y = (st.vAnchor == WindowRecorder.OverlayStyle.VAnchor.TOP)
                        ? st.marginY
                        : frame.getHeight() - st.marginY - boxH;
            }
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp01(st.boxAlpha)));
            g.setColor(st.boxColor);
            g.fillRoundRect(x, y, boxW, boxH, st.cornerRadius, st.cornerRadius);
            g.setComposite(old);
            int tx = x + st.paddingPx;
            int ty = y + st.paddingPx + fm.getAscent();
            if (st.textShadow) {
                g.setColor(new Color(0,0,0,200));
                for (String ln : lines) { g.drawString(ln, tx+1, ty+1); ty += fm.getAscent()+fm.getDescent()+st.lineGapPx; }
                ty = y + st.paddingPx + fm.getAscent();
            }
            g.setColor(st.textColor);
            for (String ln : lines) { g.drawString(ln, tx, ty); ty += fm.getAscent()+fm.getDescent()+st.lineGapPx; }
        } finally { g.dispose(); }
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    private static List<String> wrapLines(String text, FontMetrics fm, int maxWidth) {
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty()) { out.add(""); continue; }
            StringBuilder buf = new StringBuilder();
            for (String word : line.split("\\s+")) {
                if (buf.length() == 0) { buf.append(word); }
                else {
                    String trial = buf + " " + word;
                    if (fm.stringWidth(trial) <= maxWidth) { buf.append(" ").append(word); }
                    else { out.add(buf.toString()); buf.setLength(0); buf.append(word); }
                }
            }
            out.add(buf.toString());
        }
        return out;
    }

    private static final class CaptionOverlay {
        private String text = "";
        private boolean visible = true;
        private WindowRecorder.OverlayStyle style = WindowRecorder.OverlayStyle.defaultCaption();
        void setText(String t) { this.text = t == null ? "" : t; }
        void setVisible(boolean v) { this.visible = v; }
        void setStyle(WindowRecorder.OverlayStyle s) { if (s != null) this.style = s; }
        void paint(BufferedImage frame) { if (visible) paintOverlay(frame, text, style); }
    }
    private static final class SubtitleOverlay {
        private String text = "";
        private boolean visible = false;
        private WindowRecorder.OverlayStyle style = WindowRecorder.OverlayStyle.defaultSubtitle();
        void setText(String t) { this.text = t == null ? "" : t; }
        void setVisible(boolean v) { this.visible = v; }
        void setStyle(WindowRecorder.OverlayStyle s) { if (s != null) this.style = s; }
        void paint(BufferedImage frame) { if (visible) paintOverlay(frame, text, style); }
    }

    // NEU: Action-Overlay
    private static final class ActionOverlay {
        private String text = "";
        private boolean visible = false;
        private WindowRecorder.OverlayStyle style = new WindowRecorder.OverlayStyle.Builder(WindowRecorder.OverlayStyle.defaultSubtitle())
                .positionPercent(0.75, 0.05).build();
        void setText(String t) { this.text = t == null ? "" : t; }
        void setVisible(boolean v) { this.visible = v; }
        void setStyle(WindowRecorder.OverlayStyle s) { if (s != null) this.style = s; }
        void paint(BufferedImage frame) { if (visible) paintOverlay(frame, text, style); }
    }

    private static Path ensureSiblingWithExt(Path p, String ext) {
        String s = p.toString();
        int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        return Paths.get(s + ext);
    }

    private void startAudioCapture(Path wavPath) {
        try {
            AudioFormat fmt = new AudioFormat(44100.0f, 16, 1, true, false); // 44.1kHz, 16-bit, mono, signed LE
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("[Jcodec] Audioformat nicht unterstützt – Audio wird übersprungen");
                return;
            }
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(fmt);
            line.start();
            this.audioLine = line;

            audioThread = new Thread(() -> {
                try (AudioInputStream ais = new AudioInputStream(line)) {
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavPath.toFile());
                } catch (Throwable t) {
                    // beende leise
                }
            }, "jcodec-audio-capture");
            audioThread.setDaemon(true);
            audioThread.start();
        } catch (Throwable t) {
            System.out.println("[Jcodec] Audio-Capture Start fehlgeschlagen: " + t.getMessage());
        }
    }

    private void stopAudioCapture() {
        try {
            if (audioLine != null) {
                try { audioLine.stop(); } catch (Throwable ignore) {}
                try { audioLine.close(); } catch (Throwable ignore) {}
            }
        } finally {
            if (audioThread != null) {
                try { audioThread.join(2000); } catch (InterruptedException ignore) {}
            }
            audioThread = null;
            audioLine = null;
        }
    }
}
