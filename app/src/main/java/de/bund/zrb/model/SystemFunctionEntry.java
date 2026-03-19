package de.bund.zrb.model;

/**
 * A single system function entry (e.g. IDCAMS, IEFBR14, SORT).
 * When such a function appears in JCL (EXEC PGM=...), it is linked to a Wikipedia article.
 */
public class SystemFunctionEntry {

    /** Program name as it appears in JCL (EXEC PGM=...). Always stored uppercase. */
    private String name;

    /** Wikipedia article title (German). E.g. "IDCAMS" or "Access_Method_Services". */
    private String wikiTitleDe;

    /** Wikipedia article title (English). E.g. "IDCAMS" or "Access_Method_Services". */
    private String wikiTitleEn;

    /** Optional description for the user. */
    private String description;

    public SystemFunctionEntry() {
    }

    public SystemFunctionEntry(String name, String wikiTitleDe, String wikiTitleEn, String description) {
        this.name = name != null ? name.toUpperCase() : "";
        this.wikiTitleDe = wikiTitleDe != null ? wikiTitleDe : "";
        this.wikiTitleEn = wikiTitleEn != null ? wikiTitleEn : "";
        this.description = description != null ? description : "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.toUpperCase() : "";
    }

    public String getWikiTitleDe() {
        return wikiTitleDe;
    }

    public void setWikiTitleDe(String wikiTitleDe) {
        this.wikiTitleDe = wikiTitleDe;
    }

    public String getWikiTitleEn() {
        return wikiTitleEn;
    }

    public void setWikiTitleEn(String wikiTitleEn) {
        this.wikiTitleEn = wikiTitleEn;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return name;
    }
}

