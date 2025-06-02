package de.bund.zrb.runtime;

import de.bund.zrb.helper.SentenceTypeSettingsHelper;
import de.zrb.bund.api.SentenceTypeRegistry;
import de.zrb.bund.newApi.sentence.SentenceTypeSpec;

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
}
