package de.bund.zrb.helper;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.zrb.bund.newApi.sentence.SentenceTypeSpec;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public class SentenceTypeSettingsHelper {

    private static final File SENTENCE_FILE = new File(SettingsHelper.getSettingsFolder(), "sentence_types.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SENTENCE_TYPE = new TypeToken<SentenceTypeSpec>() {}.getType();

    public static SentenceTypeSpec loadSentenceTypes() {
        if (!SENTENCE_FILE.exists()) return new SentenceTypeSpec();
        try (Reader reader = new InputStreamReader(new FileInputStream(SENTENCE_FILE), StandardCharsets.UTF_8)) {
            SentenceTypeSpec result = GSON.fromJson(reader, SENTENCE_TYPE);
            return result != null ? result : new SentenceTypeSpec();
        } catch (IOException e) {
            e.printStackTrace();
            return new SentenceTypeSpec();
        } catch (JsonSyntaxException e) {
            System.err.println("⚠ Fehler beim Parsen von sentence_types.json: " + e.getMessage());
            e.printStackTrace();
            return new SentenceTypeSpec();
        } catch (Exception e) {
            System.err.println("⚠ Unerwarteter Fehler beim Laden von sentence_types.json: " + e.getMessage());
            e.printStackTrace();
            return new SentenceTypeSpec();
        }
    }

    public static void saveSentenceTypes(SentenceTypeSpec types) {
        try {
            SENTENCE_FILE.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(SENTENCE_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(types, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
