package org.hachile.tongjitap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class LibraryOps {
    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static List<Track> merge(List<Track> existing, List<Track> incoming) {
        List<Track> result = new ArrayList<>(existing);
        for (Track track : incoming) {
            boolean found = false;
            for (Track current : result) if (current.id.equals(track.id)) { found = true; break; }
            if (!found) result.add(track);
        }
        for (int i = 0; i < result.size(); i++) {
            Track song = result.get(i);
            if (!song.netease || song.isLocal()) continue;
            Track candidate = null;
            int matches = 0;
            for (Track local : result) {
                if (!local.netease && local.isLocal() && !normalize(song.title).isEmpty() && normalize(local.title).equals(normalize(song.title))) {
                    candidate = local; matches++;
                }
            }
            if (matches == 1) result.set(i, song.withLocal(candidate));
        }
        return result;
    }
}
