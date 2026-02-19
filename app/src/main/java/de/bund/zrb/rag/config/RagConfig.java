package de.bund.zrb.rag.config;

/**
 * Configuration for RAG retrieval.
 */
public class RagConfig {

    // Chunking
    private int chunkSizeChars = 1200;
    private int overlapChars = 150;

    // Lucene (lexical)
    private int luceneTopN = 200;

    // HNSW (semantic)
    private int hnswTopM = 50;
    private int hnswEfConstruction = 200;
    private int hnswM = 16;

    // Hybrid retrieval
    private int finalTopK = 12;
    private float weightLexical = 0.35f;
    private float weightSemantic = 0.65f;

    // Context limits
    private int maxContextCharsTotal = 40000;
    private int maxContextCharsPerChunk = 2000;

    // Fallback mode
    private FallbackMode fallbackMode = FallbackMode.HYBRID;

    public enum FallbackMode {
        LEXICAL_ONLY,
        SEMANTIC_ONLY,
        HYBRID
    }

    // Getters and setters

    public int getChunkSizeChars() {
        return chunkSizeChars;
    }

    public RagConfig setChunkSizeChars(int chunkSizeChars) {
        this.chunkSizeChars = chunkSizeChars;
        return this;
    }

    public int getOverlapChars() {
        return overlapChars;
    }

    public RagConfig setOverlapChars(int overlapChars) {
        this.overlapChars = overlapChars;
        return this;
    }

    public int getLuceneTopN() {
        return luceneTopN;
    }

    public RagConfig setLuceneTopN(int luceneTopN) {
        this.luceneTopN = luceneTopN;
        return this;
    }

    public int getHnswTopM() {
        return hnswTopM;
    }

    public RagConfig setHnswTopM(int hnswTopM) {
        this.hnswTopM = hnswTopM;
        return this;
    }

    public int getHnswEfConstruction() {
        return hnswEfConstruction;
    }

    public RagConfig setHnswEfConstruction(int hnswEfConstruction) {
        this.hnswEfConstruction = hnswEfConstruction;
        return this;
    }

    public int getHnswM() {
        return hnswM;
    }

    public RagConfig setHnswM(int hnswM) {
        this.hnswM = hnswM;
        return this;
    }

    public int getFinalTopK() {
        return finalTopK;
    }

    public RagConfig setFinalTopK(int finalTopK) {
        this.finalTopK = finalTopK;
        return this;
    }

    public float getWeightLexical() {
        return weightLexical;
    }

    public RagConfig setWeightLexical(float weightLexical) {
        this.weightLexical = weightLexical;
        return this;
    }

    public float getWeightSemantic() {
        return weightSemantic;
    }

    public RagConfig setWeightSemantic(float weightSemantic) {
        this.weightSemantic = weightSemantic;
        return this;
    }

    public int getMaxContextCharsTotal() {
        return maxContextCharsTotal;
    }

    public RagConfig setMaxContextCharsTotal(int maxContextCharsTotal) {
        this.maxContextCharsTotal = maxContextCharsTotal;
        return this;
    }

    public int getMaxContextCharsPerChunk() {
        return maxContextCharsPerChunk;
    }

    public RagConfig setMaxContextCharsPerChunk(int maxContextCharsPerChunk) {
        this.maxContextCharsPerChunk = maxContextCharsPerChunk;
        return this;
    }

    public FallbackMode getFallbackMode() {
        return fallbackMode;
    }

    public RagConfig setFallbackMode(FallbackMode fallbackMode) {
        this.fallbackMode = fallbackMode;
        return this;
    }

    public static RagConfig defaults() {
        return new RagConfig();
    }
}

