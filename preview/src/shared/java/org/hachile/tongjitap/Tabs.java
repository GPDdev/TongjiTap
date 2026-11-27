package org.hachile.tongjitap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Saved state, incoming media intents and tutorial results use these stable IDs. */
final class Tabs {
    static final int LOOP = 0, STRIDE = 1, RELAY = 2, MORE = 3, ROAM = 4;
    static final List<Integer> ORDER = Collections.unmodifiableList(Arrays.asList(LOOP, RELAY, ROAM, STRIDE, MORE));

    static int restore(int id) { return ORDER.contains(id) ? id : LOOP; }

    private Tabs() {}
}
