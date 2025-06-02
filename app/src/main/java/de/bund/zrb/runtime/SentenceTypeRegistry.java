package de.bund.zrb.runtime;

import de.bund.zrb.helper.SentenceTypeSettingsHelper;
import de.zrb.bund.newApi.sentence.SentenceTypeSpec;

public class SentenceTypeRegistry {

    private static SentenceTypeRegistry instance;

    private SentenceTypeSpec loadedSpec;

    private SentenceTypeRegistry() {
        loadedSpec = SentenceTypeSettingsHelper.loadSentenceTypes();
    }

    public static synchronized SentenceTypeRegistry getInstance() {
        if (instance == null) {
            instance = new SentenceTypeRegistry();
        }
        return instance;
    }

    public SentenceTypeSpec getSentenceTypeSpec() {
        return loadedSpec;
    }

    public void reload() {
        loadedSpec = SentenceTypeSettingsHelper.loadSentenceTypes();
    }

    public void save() {
        SentenceTypeSettingsHelper.saveSentenceTypes(loadedSpec);
    }
}
