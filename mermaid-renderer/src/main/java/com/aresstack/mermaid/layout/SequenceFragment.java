package com.aresstack.mermaid.layout;

/**
 * A sequence diagram combined fragment (loop, alt, opt, par, critical, break).
 *
 * <h3>Mermaid syntax examples</h3>
 * <pre>
 *   loop Diskussion
 *       Alice->>Bob: Vorschlag
 *       Bob-->>Alice: Feedback
 *   end
 *
 *   alt Erfolg
 *       Alice->>Bob: OK
 *   else Fehler
 *       Alice->>Bob: Retry
 *   end
 * </pre>
 */
public class SequenceFragment extends DiagramNode {

    /** Fragment type in a sequence diagram. */
    public enum FragmentType {
        LOOP("loop", "Schleife"),
        ALT("alt", "Alternative"),
        ELSE_FRAG("else", "Sonst"),
        OPT("opt", "Optional"),
        PAR("par", "Parallel"),
        CRITICAL("critical", "Kritisch"),
        BREAK_FRAG("break", "Abbruch"),
        RECT("rect", "Highlight");

        private final String keyword;
        private final String displayName;

        FragmentType(String keyword, String displayName) {
            this.keyword = keyword;
            this.displayName = displayName;
        }

        /** Mermaid keyword for this fragment type. */
        public String getKeyword() { return keyword; }

        /** Human-readable display name. */
        public String getDisplayName() { return displayName; }

        /**
         * Parse a fragment type from a Mermaid keyword.
         *
         * @param keyword e.g. "loop", "alt", "opt"
         * @return the matching type, or {@code LOOP} as fallback
         */
        public static FragmentType fromKeyword(String keyword) {
            if (keyword == null) return LOOP;
            String lower = keyword.trim().toLowerCase();
            for (FragmentType ft : values()) {
                if (ft.keyword.equals(lower)) return ft;
            }
            return LOOP;
        }
    }

    private final FragmentType fragmentType;
    private final String condition;

    public SequenceFragment(String id, String label,
                            double x, double y, double width, double height,
                            String svgId,
                            FragmentType fragmentType, String condition) {
        super(id, label, "fragment", x, y, width, height, svgId);
        this.fragmentType = fragmentType != null ? fragmentType : FragmentType.LOOP;
        this.condition = condition != null ? condition : "";
    }

    /** The fragment type (loop, alt, opt, etc.). */
    public FragmentType getFragmentType() { return fragmentType; }

    /** The guard condition text, e.g. "Diskussion" in {@code loop Diskussion}. */
    public String getCondition() { return condition; }

    /**
     * Generate Mermaid syntax for this fragment header.
     *
     * @return e.g. "loop Diskussion"
     */
    public String toMermaidHeader() {
        if (condition.isEmpty()) {
            return fragmentType.keyword;
        }
        return fragmentType.keyword + " " + condition;
    }

    @Override
    public String toString() {
        return "SequenceFragment{type=" + fragmentType
                + (condition.isEmpty() ? "" : ", condition='" + condition + "'")
                + ", bounds=[" + fmt(getX()) + "," + fmt(getY()) + " "
                + fmt(getWidth()) + "x" + fmt(getHeight()) + "]}";
    }
}

