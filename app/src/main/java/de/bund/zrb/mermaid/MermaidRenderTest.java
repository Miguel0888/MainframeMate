package de.bund.zrb.mermaid;

import de.bund.zrb.wiki.ui.SvgRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Interactive test tool for Mermaid → SVG → BufferedImage rendering.
 * <p>
 * Shows the rendered diagram in a Swing window and asks the user whether
 * it looks correct via Yes/No/Partial buttons.  The program exits after
 * one click and prints the answer to stdout so the calling terminal can
 * pick it up.
 */
public final class MermaidRenderTest {

    private MermaidRenderTest() {}

    public static void main(String[] args) throws Exception {
        String diagramCode = args.length > 0 ? args[0] :
                "graph TD\n" +
                "    A[Start] --> B{Entscheidung?}\n" +
                "    B -->|Ja| C[Aktion 1]\n" +
                "    B -->|Nein| D[Aktion 2]\n" +
                "    C --> E[Ende]\n" +
                "    D --> E";

        System.out.println("[MermaidRenderTest] Rendering diagram...");
        long t0 = System.currentTimeMillis();

        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            System.out.println("RESULT:ERROR mermaid.min.js not found on classpath");
            System.exit(1);
            return;
        }

        String svg = renderer.renderToSvg(diagramCode);
        long t1 = System.currentTimeMillis();

        if (svg == null || !svg.contains("<svg")) {
            System.out.println("RESULT:ERROR SVG rendering returned null");
            System.exit(1);
            return;
        }

        System.out.println("[MermaidRenderTest] SVG generated: " + svg.length() + " chars in " + (t1 - t0) + " ms");

        // Save SVG to file for inspection
        File svgFile = new File(System.getProperty("user.dir"), "mermaid-test-output.svg");
        Writer w = new OutputStreamWriter(new FileOutputStream(svgFile), "UTF-8");
        try {
            w.write(svg);
        } finally {
            w.close();
        }
        System.out.println("[MermaidRenderTest] SVG saved to: " + svgFile.getAbsolutePath());

        // --- SVG → BufferedImage via Batik (from wiki-integration) ---
        byte[] svgBytes = svg.getBytes("UTF-8");
        BufferedImage image = SvgRenderer.renderToBufferedImage(svgBytes, 800f, 600f);
        long t2 = System.currentTimeMillis();

        if (image == null) {
            System.out.println("[MermaidRenderTest] Batik returned null — showing raw SVG text instead");
        } else {
            System.out.println("[MermaidRenderTest] Image rasterised: "
                    + image.getWidth() + "x" + image.getHeight() + " in " + (t2 - t1) + " ms");
        }

        // --- Show in Swing dialog ---
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        final BufferedImage finalImage = image;
        final String finalSvg = svg;
        final String svgFilePath = svgFile.getAbsolutePath();

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                showDialog(finalImage, finalSvg, svgFilePath);
            }
        });
    }

    private static void showDialog(final BufferedImage image, final String svg, String svgFilePath) {
        final JDialog dialog = new JDialog((Frame) null, "Mermaid Render Test", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8, 8));

        // --- Left: image, Right: SVG source (split pane) ---
        // Image panel
        JPanel imagePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (image != null) {
                    int pw = getWidth();
                    int ph = getHeight();
                    int iw = image.getWidth();
                    int ih = image.getHeight();
                    double scale = Math.min((double) pw / iw, (double) ph / ih);
                    int dw = (int) (iw * scale);
                    int dh = (int) (ih * scale);
                    int x = (pw - dw) / 2;
                    int y = (ph - dh) / 2;
                    g.drawImage(image, x, y, dw, dh, null);
                } else {
                    g.setColor(Color.RED);
                    g.drawString("Batik konnte SVG nicht rendern", 20, 30);
                }
            }
        };
        imagePanel.setPreferredSize(new Dimension(500, 500));
        imagePanel.setBackground(Color.WHITE);

        // SVG source panel
        JTextArea svgTextArea = new JTextArea(svg);
        svgTextArea.setEditable(false);
        svgTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        svgTextArea.setLineWrap(true);
        JScrollPane svgScroll = new JScrollPane(svgTextArea);
        svgScroll.setPreferredSize(new Dimension(400, 500));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                imagePanel, svgScroll);
        splitPane.setDividerLocation(500);
        dialog.add(splitPane, BorderLayout.CENTER);

        // --- Info label ---
        String info = (image != null
                ? "Bild: " + image.getWidth() + "x" + image.getHeight() + " px"
                : "Batik: FEHLER")
                + "  |  SVG: " + svg.length() + " chars"
                + "  |  Datei: " + svgFilePath;
        JLabel infoLabel = new JLabel(info, SwingConstants.CENTER);
        infoLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));
        dialog.add(infoLabel, BorderLayout.NORTH);

        // --- Button panel ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        final String[] answer = {""};

        JButton yesBtn = new JButton("\u2705 Korrekt gerendert");
        yesBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                answer[0] = "YES";
                dialog.dispose();
            }
        });

        JButton partialBtn = new JButton("\u26A0 Teilweise");
        partialBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                answer[0] = "PARTIAL";
                dialog.dispose();
            }
        });

        JButton noBtn = new JButton("\u274C Nicht gerendert");
        noBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                answer[0] = "NO";
                dialog.dispose();
            }
        });

        buttonPanel.add(yesBtn);
        buttonPanel.add(partialBtn);
        buttonPanel.add(noBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);

        System.out.println("RESULT:" + answer[0]);
    }
}

