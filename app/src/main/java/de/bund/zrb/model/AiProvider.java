package de.bund.zrb.model;

public enum AiProvider {
    DISABLED,
    OLLAMA,
    CLOUD,
    LOCAL_AI,
    LLAMA_CPP_SERVER,
    CUSTOM,             // Selbstgehostete Variante mit erweiterten Einstellungen
    ONNX_RUNTIME        // Lokale Inferenz via ONNX Runtime (Phi-3/Phi-4 etc.)
}
