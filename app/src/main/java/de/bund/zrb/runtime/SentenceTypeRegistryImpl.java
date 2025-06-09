package de.bund.zrb.runtime;

import de.bund.zrb.helper.SentenceTypeSettingsHelper;
import de.zrb.bund.api.SentenceTypeRegistry;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceTypeSpec;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

public class SentenceTypeRegistryImpl implements SentenceTypeRegistry {

    private static SentenceTypeRegistryImpl instance;

    private SentenceTypeSpec loadedSpec;

    private SentenceTypeRegistryImpl() {
        loadedSpec = SentenceTypeSettingsHelper.loadSentenceTypes();
    }

    public static synchronized SentenceTypeRegistryImpl getInstance() {
        if (instance == null) {
            instance = new SentenceTypeRegistryImpl();
        }
        return instance;
    }

    @Override
    public SentenceTypeSpec getSentenceTypeSpec() {
        return loadedSpec;
    }

    @Override
    public void reload() {
        loadedSpec = SentenceTypeSettingsHelper.loadSentenceTypes();
    }

    @Override
    public void save() {
        SentenceTypeSettingsHelper.saveSentenceTypes(loadedSpec);
    }

    @Override
    public Optional<SentenceDefinition> findDefinition(@Nullable String sentenceType) {
        if (sentenceType == null || sentenceType.trim().isEmpty()) {
            return Optional.empty();
        }

        return getSentenceTypeSpec().getDefinitions().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(sentenceType))
                .map(Map.Entry::getValue)
                .findFirst();
    }

}
