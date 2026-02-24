package de.bund.zrb.mcpserver.browser;

import de.bund.zrb.type.script.WDRemoteReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maps short NodeRef IDs ("n1", "n2", …) to WD4J SharedReferences.
 * Populated by snapshots, consumed by actions.
 * Invalidated on navigation.
 */
public class NodeRefRegistry {

    private final AtomicInteger counter = new AtomicInteger(0);
    private final Map<String, Entry> refs = new LinkedHashMap<String, Entry>();
    private int snapshotVersion = 0;

    public static class Entry {
        public final NodeRef nodeRef;
        public final WDRemoteReference.SharedReference sharedRef;
        public final int snapshotVersion;

        Entry(NodeRef nodeRef, WDRemoteReference.SharedReference sharedRef, int snapshotVersion) {
            this.nodeRef = nodeRef;
            this.sharedRef = sharedRef;
            this.snapshotVersion = snapshotVersion;
        }
    }

    /**
     * Register a new node and return its NodeRef.
     */
    public NodeRef register(String tag, String text, String role, String name,
                            boolean interactive, WDRemoteReference.SharedReference sharedRef) {
        String id = "n" + counter.incrementAndGet();
        NodeRef ref = new NodeRef(id, tag, text, role, name, interactive);
        refs.put(id, new Entry(ref, sharedRef, snapshotVersion));
        return ref;
    }

    /**
     * Resolve a NodeRef ID to its SharedReference for actions.
     * @throws IllegalArgumentException if the ref is unknown or expired.
     */
    public Entry resolve(String nodeRefId) {
        Entry entry = refs.get(nodeRefId);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "NodeRef '" + nodeRefId + "' nicht gefunden. "
                  + "Bitte zuerst browse_snapshot aufrufen, um aktuelle Referenzen zu erhalten.");
        }
        return entry;
    }

    /**
     * Get all registered NodeRefs (for listing).
     */
    public List<NodeRef> getAll() {
        List<NodeRef> result = new ArrayList<NodeRef>();
        for (Entry e : refs.values()) {
            result.add(e.nodeRef);
        }
        return result;
    }

    /**
     * Clear all refs (e.g. on navigation).
     */
    public void invalidateAll() {
        refs.clear();
        snapshotVersion++;
    }

    /**
     * Increment snapshot version (existing refs remain valid but marked as older).
     */
    public int nextSnapshotVersion() {
        return ++snapshotVersion;
    }

    public int getSnapshotVersion() {
        return snapshotVersion;
    }

    public int size() {
        return refs.size();
    }
}

