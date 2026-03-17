package de.bund.zrb.service;

import com.google.gson.Gson;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin adapter that provides the video-recording module's SettingsService API
 * on top of MainframeMate's SettingsHelper infrastructure.
 *
 * All video-related settings are stored under Settings.videoSettings (Map<String,Object>)
 * in ~/.mainframemate/settings.json so they persist with the rest of the app settings.
 */
public final class SettingsService {

    private static final SettingsService INSTANCE = new SettingsService();
    private final Gson gson = new Gson();

    private SettingsService() {}

    public static SettingsService getInstance() {
        return INSTANCE;
    }

    /**
     * Get a video setting by key, converting through Gson for type safety.
     * Returns null if the key is not set.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Settings settings = SettingsHelper.load();
        Map<String, Object> map = settings.videoSettings;
        if (map == null) return null;
        Object value = map.get(key);
        if (value == null) return null;
        // Use Gson round-trip for safe type coercion (handles Number -> Integer etc.)
        try {
            return gson.fromJson(gson.toJson(value), type);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Persist a video setting by key.
     * Null removes the key.
     */
    public void set(String key, Object value) {
        Settings settings = SettingsHelper.load();
        if (settings.videoSettings == null) {
            settings.videoSettings = new LinkedHashMap<String, Object>();
        }
        if (value == null) {
            settings.videoSettings.remove(key);
        } else {
            settings.videoSettings.put(key, value);
        }
        SettingsHelper.save(settings);
    }

    /**
     * Applies all persisted video settings to VideoConfig.
     * Call this once on application startup.
     */
    public static synchronized void initAdapter() {
        SettingsService s = getInstance();
        de.bund.zrb.config.VideoConfig cfg = null; // only used for static calls

        Boolean vEnabled = s.get("video.enabled", Boolean.class);
        Integer vFps     = s.get("video.fps", Integer.class);
        String  vDir     = s.get("video.reportsDir", String.class);

        if (vEnabled != null) de.bund.zrb.config.VideoConfig.setEnabled(vEnabled);
        if (vFps != null)     de.bund.zrb.config.VideoConfig.setFps(vFps);
        if (vDir != null && !vDir.trim().isEmpty()) de.bund.zrb.config.VideoConfig.setReportsDir(vDir);

        String vContainer = s.get("video.container", String.class);
        String vCodec     = s.get("video.codec", String.class);
        String vPixFmt    = s.get("video.pixfmt", String.class);
        Boolean vInter    = s.get("video.interleaved", Boolean.class);
        String vQuality   = s.get("video.quality", String.class);
        Integer vQscale   = s.get("video.qscale", Integer.class);
        Integer vCrf      = s.get("video.crf", Integer.class);
        Integer vBr       = s.get("video.bitrateKbps", Integer.class);
        String cRange     = s.get("video.color.range", String.class);
        String cSpace     = s.get("video.color.space", String.class);
        String cTrc       = s.get("video.color.trc", String.class);
        String cPrim      = s.get("video.color.primaries", String.class);
        String vVf        = s.get("video.vf", String.class);
        Integer vThreads  = s.get("video.threads", Integer.class);
        Boolean vEven     = s.get("video.enforceEvenDims", Boolean.class);
        java.util.List<String> vFBs = s.get("video.container.fallbacks", java.util.List.class);
        String vPreset    = s.get("video.preset", String.class);
        String vTune      = s.get("video.tune", String.class);
        String vProfile   = s.get("video.profile", String.class);
        String vLevel     = s.get("video.level", String.class);

        try { if (vContainer != null) de.bund.zrb.config.VideoConfig.setContainer(vContainer); } catch (Exception ignore) {}
        try { if (vCodec     != null) de.bund.zrb.config.VideoConfig.setCodec(vCodec); } catch (Exception ignore) {}
        try { if (vPixFmt    != null) de.bund.zrb.config.VideoConfig.setPixelFmt(vPixFmt); } catch (Exception ignore) {}
        if (vInter    != null) de.bund.zrb.config.VideoConfig.setInterleaved(vInter);
        try { if (vQuality   != null) de.bund.zrb.config.VideoConfig.setQualityMode(vQuality); } catch (Exception ignore) {}
        try { if (vQscale    != null) de.bund.zrb.config.VideoConfig.setQscale(vQscale); } catch (Exception ignore) {}
        try { if (vCrf       != null) de.bund.zrb.config.VideoConfig.setCrf(vCrf); } catch (Exception ignore) {}
        try { if (vBr        != null) de.bund.zrb.config.VideoConfig.setBitrateKbps(vBr); } catch (Exception ignore) {}
        try { if (cRange     != null) de.bund.zrb.config.VideoConfig.setColorRange(cRange); } catch (Exception ignore) {}
        try { if (cSpace     != null) de.bund.zrb.config.VideoConfig.setColorspace(cSpace); } catch (Exception ignore) {}
        try { if (cTrc       != null) de.bund.zrb.config.VideoConfig.setColorTrc(cTrc); } catch (Exception ignore) {}
        try { if (cPrim      != null) de.bund.zrb.config.VideoConfig.setColorPrimaries(cPrim); } catch (Exception ignore) {}
        try { if (vVf        != null) de.bund.zrb.config.VideoConfig.setVf(vVf); } catch (Exception ignore) {}
        try { if (vThreads   != null) de.bund.zrb.config.VideoConfig.setThreads(vThreads); } catch (Exception ignore) {}
        if (vEven     != null) de.bund.zrb.config.VideoConfig.setEnforceEvenDims(vEven);
        if (vFBs      != null) de.bund.zrb.config.VideoConfig.setContainerFallbacks(vFBs);
        try { if (vPreset    != null) de.bund.zrb.config.VideoConfig.setPreset(vPreset); } catch (Exception ignore) {}
        try { if (vTune      != null) de.bund.zrb.config.VideoConfig.setTune(vTune); } catch (Exception ignore) {}
        try { if (vProfile   != null) de.bund.zrb.config.VideoConfig.setProfile(vProfile); } catch (Exception ignore) {}
        try { if (vLevel     != null) de.bund.zrb.config.VideoConfig.setLevel(vLevel); } catch (Exception ignore) {}
    }
}
