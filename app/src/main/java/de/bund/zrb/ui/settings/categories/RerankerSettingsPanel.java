package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.rag.config.RerankerSettings;
import de.bund.zrb.rag.infrastructure.HttpRerankerClient;
import de.bund.zrb.rag.port.RerankerClient;
import de.bund.zrb.rag.service.RagService;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Settings panel for the optional cross-encoder reranker stage.
 *
 * <p>A reranker dramatically improves RAG search quality by re-scoring
 * the initial candidate set with a cross-encoder model that processes
 * query and passage jointly — unlike bi-encoder embeddings which encode
 * them independently.
 *
 * <p>Supported backends: HuggingFace TEI, Jina Reranker, Cohere, or any
 * compatible API endpoint.
 */
public class RerankerSettingsPanel extends AbstractSettingsPanel {

    private final JCheckBox enabledBox;
    private final JTextField apiUrlField;
    private final JComboBox<String> modelCombo;
    private final JTextField apiKeyField;
    private final JSpinner topNSpinner;
    private final JSpinner candidatePoolSpinner;
    private final JSpinner timeoutSpinner;
    private final JTextField scoreThresholdField;
    private final JCheckBox useProxyBox;
    private final JLabel statusLabel;

    public RerankerSettingsPanel() {
        super("reranker", "Reranker");

        FormBuilder fb = new FormBuilder();

        // ── Header ──────────────────────────────────────────────────
        fb.addInfo("<html><b>Cross-Encoder Reranker</b> — Stufe 3 der RAG-Pipeline<br><br>"
                + "<b>Pipeline-Architektur:</b><br>"
                + "① <b>BM25 (Lucene)</b> — schnelles Keyword-Matching → ~50 Kandidaten<br>"
                + "② <b>Vektoren (Embeddings)</b> — <i>optional</i>, semantischer \"Magnet\":<br>"
                + "&nbsp;&nbsp;&nbsp;&nbsp;\"Auto\" findet auch \"KFZ\"/\"Wagen\". Erweitert den Kandidaten-Pool.<br>"
                + "③ <b>Reranker</b> — liest den <i>rohen Text</i> (nicht Vektoren!) der Kandidaten<br>"
                + "&nbsp;&nbsp;&nbsp;&nbsp;zusammen mit der Frage und erkennt, welche 3–5 wirklich relevant sind.<br>"
                + "&nbsp;&nbsp;&nbsp;&nbsp;<b>Ersetzt das BM25-Scoring</b> durch präzisere Cross-Encoder-Scores.<br><br>"
                + "<b>Embeddings sind NICHT Pflicht für den Reranker!</b><br>"
                + "• BM25 allein → Basis-Keyword-Suche<br>"
                + "• BM25 + Reranker → präzises Rescoring der Keyword-Treffer<br>"
                + "• BM25 + Embeddings → breitere Treffer (Keyword + Semantik)<br>"
                + "• BM25 + Embeddings + Reranker → beste Qualität<br><br>"
                + "<b>Empfohlene Modelle:</b><br>"
                + "• <code>BAAI/bge-reranker-v2-m3</code> — multilingual, schnell, exzellente Qualität<br>"
                + "• <code>BAAI/bge-reranker-v2-gemma</code> — größer, höchste Qualität<br>"
                + "• <code>cross-encoder/ms-marco-MiniLM-L-6-v2</code> — English-only, sehr schnell<br><br>"
                + "<b>Server-Optionen:</b><br>"
                + "• <code>HuggingFace TEI</code>: "
                + "<code>docker run -p 8082:80 ghcr.io/huggingface/text-embeddings-inference "
                + "--model-id BAAI/bge-reranker-v2-m3</code><br>"
                + "• <code>Jina API</code>: <code>https://api.jina.ai/v1/rerank</code> (API-Key erforderlich)<br>"
                + "• <code>Ollama</code> mit kompatiblem Modell</html>");

        // ── Enabled ─────────────────────────────────────────────────
        enabledBox = new JCheckBox("Reranker aktivieren");
        fb.addWide(enabledBox);

        fb.addSection("Verbindung");

        // ── API URL ─────────────────────────────────────────────────
        apiUrlField = new JTextField(30);
        fb.addRow("API-URL:", apiUrlField);

        // ── Model ───────────────────────────────────────────────────
        modelCombo = new JComboBox<>(new String[]{
                "BAAI/bge-reranker-v2-m3",
                "BAAI/bge-reranker-v2-gemma",
                "BAAI/bge-reranker-base",
                "BAAI/bge-reranker-large",
                "cross-encoder/ms-marco-MiniLM-L-6-v2",
                "jinaai/jina-reranker-v2-base-multilingual"
        });
        modelCombo.setEditable(true);
        fb.addRow("Modell:", modelCombo);

        // ── API Key ─────────────────────────────────────────────────
        apiKeyField = new JTextField(30);
        fb.addRow("API-Key (optional):", apiKeyField);

        // ── Proxy ───────────────────────────────────────────────────
        useProxyBox = new JCheckBox("Proxy verwenden");
        fb.addWide(useProxyBox);

        fb.addSection("Retrieval-Parameter");

        // ── Top N ───────────────────────────────────────────────────
        topNSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        fb.addRow("Top-N (finale Ergebnisse):", topNSpinner);

        // ── Candidate Pool ──────────────────────────────────────────
        candidatePoolSpinner = new JSpinner(new SpinnerNumberModel(50, 5, 200, 5));
        fb.addRow("Kandidaten-Pool:", candidatePoolSpinner);

        fb.addInfo("<html><small>Der Kandidaten-Pool bestimmt, wie viele Treffer aus Stufe 1+2 (BM25 + Vektoren)<br>"
                + "an den Reranker gesendet werden. Der Reranker liest den <b>rohen Text</b> dieser Chunks<br>"
                + "(nicht die Vektoren!) und bestimmt, welche davon wirklich zur Frage passen.<br>"
                + "Empfohlen: 3–10× größer als Top-N. Standard: 50.</small></html>");

        // ── Score Threshold ─────────────────────────────────────────
        scoreThresholdField = new JTextField("0.0", 8);
        fb.addRow("Score-Schwellwert:", scoreThresholdField);

        fb.addInfo("<html><small>Minimaler Relevanz-Score (0.0–1.0). Chunks unter diesem Wert werden<br>"
                + "verworfen, selbst wenn sie im Top-N-Limit liegen. 0.0 = kein Filter.</small></html>");

        // ── Timeout ─────────────────────────────────────────────────
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(30, 5, 120, 5));
        fb.addRow("Timeout (Sekunden):", timeoutSpinner);

        fb.addSection("Test");

        // ── Connection test ─────────────────────────────────────────
        JButton testButton = new JButton("🔍 Verbindung testen");
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));

        testButton.addActionListener(e -> testConnection());
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        testPanel.add(testButton);
        testPanel.add(statusLabel);
        fb.addWide(testPanel);

        // ── Toggle field state ──────────────────────────────────────
        java.util.List<Component> configFields = Arrays.<Component>asList(
                apiUrlField, modelCombo, apiKeyField, useProxyBox,
                topNSpinner, candidatePoolSpinner, scoreThresholdField,
                timeoutSpinner, testButton
        );
        enabledBox.addActionListener(e -> {
            boolean on = enabledBox.isSelected();
            for (Component c : configFields) c.setEnabled(on);
        });

        installPanel(fb);
        loadFromSettings();

        // Set initial field state
        boolean on = enabledBox.isSelected();
        for (Component c : configFields) c.setEnabled(on);
    }

    private void loadFromSettings() {
        RerankerSettings rs = RerankerSettings.fromStoredConfig();
        enabledBox.setSelected(rs.isEnabled());
        apiUrlField.setText(rs.getApiUrl());
        modelCombo.setSelectedItem(rs.getModel());
        apiKeyField.setText(rs.getApiKey());
        topNSpinner.setValue(rs.getTopN());
        candidatePoolSpinner.setValue(rs.getCandidatePoolSize());
        timeoutSpinner.setValue(rs.getTimeoutSeconds());
        scoreThresholdField.setText(String.valueOf(rs.getScoreThreshold()));
        useProxyBox.setSelected(rs.isUseProxy());
    }

    @Override
    protected void applyToSettings(Settings s) {
        if (s.rerankerConfig == null) {
            s.rerankerConfig = new HashMap<>();
        }

        s.rerankerConfig.put("enabled", String.valueOf(enabledBox.isSelected()));
        s.rerankerConfig.put("apiUrl", apiUrlField.getText().trim());
        s.rerankerConfig.put("model", String.valueOf(modelCombo.getSelectedItem()).trim());
        s.rerankerConfig.put("apiKey", apiKeyField.getText().trim());
        s.rerankerConfig.put("topN", topNSpinner.getValue().toString());
        s.rerankerConfig.put("candidatePoolSize", candidatePoolSpinner.getValue().toString());
        s.rerankerConfig.put("timeout", timeoutSpinner.getValue().toString());
        s.rerankerConfig.put("scoreThreshold", scoreThresholdField.getText().trim());
        s.rerankerConfig.put("useProxy", String.valueOf(useProxyBox.isSelected()));
    }

    @Override
    protected void afterApply(Settings s) {
        // Live-update the RagService reranker client
        try {
            RerankerSettings rs = RerankerSettings.fromStoredConfig();
            RagService.getInstance().updateRerankerSettings(rs);
        } catch (Exception e) {
            // Ignore — RagService may not be initialized yet
        }
    }

    /**
     * Test the reranker connection with a simple request.
     */
    private void testConnection() {
        statusLabel.setText("⏳ Teste Verbindung...");
        statusLabel.setForeground(Color.GRAY);

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                RerankerSettings testSettings = new RerankerSettings()
                        .setEnabled(true)
                        .setApiUrl(apiUrlField.getText().trim())
                        .setModel(String.valueOf(modelCombo.getSelectedItem()).trim())
                        .setApiKey(apiKeyField.getText().trim())
                        .setTimeoutSeconds((Integer) timeoutSpinner.getValue())
                        .setUseProxy(useProxyBox.isSelected());

                HttpRerankerClient client = new HttpRerankerClient(testSettings);

                try {
                    float[] scores = client.rerank(
                            "Was ist maschinelles Lernen?",
                            Arrays.asList(
                                    "Maschinelles Lernen ist ein Teilgebiet der künstlichen Intelligenz.",
                                    "Das Wetter in Berlin ist heute sonnig."
                            )
                    );
                    if (scores.length >= 2 && scores[0] > scores[1]) {
                        return String.format("✅ Funktioniert! Score relevant=%.3f, irrelevant=%.3f",
                                scores[0], scores[1]);
                    } else if (scores.length >= 2) {
                        return String.format("⚠️ Antwort erhalten, aber Ranking unerwartet: [%.3f, %.3f]",
                                scores[0], scores[1]);
                    } else {
                        return "⚠️ Unerwartete Antwort: " + scores.length + " Scores";
                    }
                } catch (RerankerClient.RerankerException e) {
                    return "❌ Fehler: " + e.getMessage();
                } catch (Exception e) {
                    return "❌ " + e.getClass().getSimpleName() + ": " + e.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    statusLabel.setText(result);
                    statusLabel.setForeground(result.startsWith("✅") ? new Color(0, 128, 0)
                            : result.startsWith("⚠") ? new Color(200, 150, 0) : Color.RED);
                } catch (Exception e) {
                    statusLabel.setText("❌ " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                }
            }
        };
        worker.execute();
    }
}
