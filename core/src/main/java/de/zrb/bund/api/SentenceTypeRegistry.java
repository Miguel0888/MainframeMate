package de.zrb.bund.api;

import de.zrb.bund.newApi.sentence.SentenceTypeSpec;

public interface SentenceTypeRegistry {
    SentenceTypeSpec getSentenceTypeSpec();

    void reload();

    void save();
}
