package de.bund.zrb.rag.model;

/**
 * A chunk with its retrieval score.
 */
public final class ScoredChunk implements Comparable<ScoredChunk> {

    private final Chunk chunk;
    private final float score;
    private final ScoreSource source;

    public enum ScoreSource {
        LEXICAL,
        SEMANTIC,
        HYBRID
    }

    public ScoredChunk(Chunk chunk, float score, ScoreSource source) {
        this.chunk = chunk;
        this.score = score;
        this.source = source;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public float getScore() {
        return score;
    }

    public ScoreSource getSource() {
        return source;
    }

    public String getChunkId() {
        return chunk.getChunkId();
    }

    public String getText() {
        return chunk.getText();
    }

    @Override
    public int compareTo(ScoredChunk other) {
        // Higher score first
        return Float.compare(other.score, this.score);
    }

    @Override
    public String toString() {
        return "ScoredChunk{" +
                "chunkId='" + chunk.getChunkId() + '\'' +
                ", score=" + score +
                ", source=" + source +
                '}';
    }
}

