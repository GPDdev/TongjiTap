package org.hachile.tongjitap;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StrideCoreTest {
    @Test public void dragIsRelativeClampedAndReversible() {
        SpeedConfig c = new SpeedConfig();
        assertEquals(1.25f, c.drag(1f, 150, 300), .001f);
        assertEquals(c.min, c.drag(1f, -3000, 300), .001f);
        assertEquals(c.max, c.drag(1f, 3000, 300), .001f);
        c.reverse = true;
        assertEquals(.75f, c.drag(1f, 150, 300), .001f);
        assertEquals(1f, c.drag(1f, 50, 0), .001f);
        assertEquals(1f, c.drag(1f, Float.NaN, 100), .001f);
    }

    @Test public void speedIsRoundedAndResetRespectsRange() {
        SpeedConfig c = new SpeedConfig();
        assertEquals(1.23f, c.clamp(1.234f), .001f);
        c.min = 1.1f;
        assertEquals(1.1f, c.clamp(1f), .001f);
        c.min = 1f; c.max = 1.05f; c.validate();
    }

    @Test public void rejectsInvalidSettings() {
        SpeedConfig c = new SpeedConfig();
        c.min = 2f;
        c.max = 1f;
        assertThrows(IllegalArgumentException.class, c::validate);
        c.min = Float.NaN;
        assertThrows(IllegalArgumentException.class, c::validate);
        c.min = .75f;
        c.sensitivity = Float.POSITIVE_INFINITY;
        assertThrows(IllegalArgumentException.class, c::validate);
    }

    @Test public void acceptsPlaylistShareFormats() {
        assertEquals("1234", NeteaseLinks.extract("1234"));
        assertEquals("1234", NeteaseLinks.extract("分享歌单 https://music.163.com/#/playlist?id=1234&userid=9"));
        assertEquals("1234", NeteaseLinks.extract("https://y.music.163.com/m/playlist?id=1234"));
        assertEquals("1234", NeteaseLinks.extract("https://music.163.com/playlist/1234/"));
        assertTrue(NeteaseLinks.shortLink("快来听 https://163cn.tv/abc"));
        assertEquals("https://163cn.tv/abc", NeteaseLinks.firstShortLink("分享 https://163cn.tv/abc"));
    }

    @Test public void rejectsOtherSitesAndNonPlaylistIds() {
        assertThrows(IllegalArgumentException.class, () -> NeteaseLinks.extract("https://music.163.com.evil.test/playlist?id=1234"));
        assertThrows(IllegalArgumentException.class, () -> NeteaseLinks.extract("https://music.163.com/song?id=1234"));
        assertThrows(IllegalArgumentException.class, () -> NeteaseLinks.extract("file:///playlist?id=1234"));
        assertFalse(NeteaseLinks.shortLink("https://163cn.tv.evil.test/abc"));
    }

    @Test public void matchesOnlyUniqueLocalTitlesAndPreservesOrder() {
        Track song = new Track("netease:1", "Run!", "Singer", "https://example.test/song", true);
        Track local = new Track("local:1", "run", "Singer", "content://audio/1", false);
        List<Track> result = LibraryOps.merge(Collections.singletonList(song), Collections.singletonList(local));
        assertEquals(song.id, result.get(0).id);
        assertEquals(local.uri, result.get(0).uri);
        assertEquals(2, LibraryOps.merge(result, Collections.singletonList(song)).size());
        Track other = new Track("local:2", "Run", "Other singer", "content://audio/2", false);
        List<Track> ambiguous = LibraryOps.merge(Arrays.asList(local, other), Collections.singletonList(song));
        assertEquals(song.uri, ambiguous.get(2).uri);
    }
}
