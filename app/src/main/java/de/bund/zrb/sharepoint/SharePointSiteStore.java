package de.bund.zrb.sharepoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Serialises / deserialises the SharePoint site list to/from the JSON
 * string stored in {@link de.bund.zrb.model.Settings#sharepointSitesJson}.
 */
public final class SharePointSiteStore {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type LIST_TYPE = new TypeToken<List<SharePointSite>>() {}.getType();

    private SharePointSiteStore() {}

    /** Deserialise a JSON array string to a list of sites. Never returns null. */
    public static List<SharePointSite> fromJson(String json) {
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            return new ArrayList<SharePointSite>();
        }
        try {
            List<SharePointSite> list = GSON.fromJson(json, LIST_TYPE);
            return list != null ? list : new ArrayList<SharePointSite>();
        } catch (Exception e) {
            return new ArrayList<SharePointSite>();
        }
    }

    /** Serialise a list of sites to a JSON array string. */
    public static String toJson(List<SharePointSite> sites) {
        if (sites == null || sites.isEmpty()) return "[]";
        return GSON.toJson(sites, LIST_TYPE);
    }

    /** Return only the sites where {@link SharePointSite#isSelected()} is true. */
    public static List<SharePointSite> selectedSites(List<SharePointSite> all) {
        if (all == null) return Collections.emptyList();
        List<SharePointSite> result = new ArrayList<SharePointSite>();
        for (SharePointSite s : all) {
            if (s.isSelected()) result.add(s);
        }
        return result;
    }
}

