package de.zrb.bund.api;

import javax.annotation.Nullable;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceTypeSpec;

import java.util.Optional;

public interface SentenceTypeRegistry {
    SentenceTypeSpec getSentenceTypeSpec();

    void reload();

    void save();

    Optional<SentenceDefinition> findDefinition(@Nullable String sentenceType);
}
