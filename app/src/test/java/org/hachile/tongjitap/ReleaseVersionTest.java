package org.hachile.tongjitap;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReleaseVersionTest {
    @Test public void comparesNumericComponents() { assertTrue(ReleaseVersion.newer("v1.0.10","1.0.9")); assertTrue(ReleaseVersion.newer("1.10.0","1.9.8")); }
    @Test public void detectsMajorAndPatchUpdates() { assertTrue(ReleaseVersion.newer("2.0.0","1.99.99")); assertTrue(ReleaseVersion.newer("v1.0.2","1.0.1")); }
    @Test public void neverOffersSameVersionOrDowngrade() { assertFalse(ReleaseVersion.newer("v1.0.1","1.0.1")); assertFalse(ReleaseVersion.newer("1.0.0","1.0.1")); assertFalse(ReleaseVersion.newer("0.9.9","1.0.0")); }
    @Test public void skipsPreviewTags() { assertFalse(ReleaseVersion.newer("v2.0.0-beta.1","1.0.1")); assertFalse(ReleaseVersion.newer("v2.0.0-rc1","1.0.1")); }
    @Test public void rejectsUnusableVersionResponses() { for(String s:new String[]{"","latest","1.2","1.2.3.4","v01.2.3","999999999999.0.0",null}) assertFalse(ReleaseVersion.newer(s,"1.0.1")); }
    @Test public void requiresKnownInstalledVersion() { assertFalse(ReleaseVersion.newer("9.0.0",null)); assertFalse(ReleaseVersion.newer("9.0.0","dev")); }
}
