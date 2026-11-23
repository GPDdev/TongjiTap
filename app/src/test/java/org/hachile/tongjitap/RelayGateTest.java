package org.hachile.tongjitap;
import org.junit.Test;
import static org.junit.Assert.*;
public class RelayGateTest {
    private RelayGate.Frame f(double x, int m, long time) { return new RelayGate.Frame(x, .5, m, time); }
    @Test public void bothConditionsAndConfirmationAreRequired() {
        RelayGate gate = new RelayGate(.15);
        assertFalse(gate.sample(f(.2, 100, 1000)));
        assertFalse(gate.sample(f(.5, 120, 2000))); // exactly .02 km is insufficient
        assertFalse(gate.sample(f(.8, 150, 3000)));
        assertTrue(gate.sample(f(.8, 150, 4000)));
        assertFalse(gate.sample(f(.8, 150, 5000))); // no repeated action
    }
    @Test public void movementAloneAndDistanceAloneNeverTrigger() {
        RelayGate gate = new RelayGate(.15);
        assertFalse(gate.sample(f(.2, 100, 1000)));
        assertFalse(gate.sample(f(.7, 100, 2000)));
        assertFalse(gate.sample(f(.7, 200, 3000)));
        assertFalse(gate.sample(f(.7, 200, 4000)));
    }
    @Test public void confirmedLargerJumpsAreNotSilentlyCapped() {
        RelayGate gate=new RelayGate(.15); gate.sample(f(.2,100,1000));
        assertFalse(gate.sample(f(.8,12100,2000)));
        assertTrue(gate.sample(f(.8,12100,3000)));
    }
    @Test public void occlusionAndStaleFramesCannotBecomeJumps() {
        RelayGate gate = new RelayGate(.15); gate.sample(f(.2, 100, 1000)); gate.sample(null);
        assertFalse(gate.sample(f(.8, 300, 2000)));
        assertFalse(gate.sample(f(.2, 500, 18000)));
    }
    @Test public void unstableOcrDoesNotConfirmAndReturnJumpSurvivesShade() {
        RelayGate gate = new RelayGate(.15); gate.sample(f(.2, 100, 1000)); gate.sample(f(.8, 160, 2000));
        assertFalse(gate.sample(f(.8, 100, 3000)));
        gate.seed(f(.8, 160, 9000));
        assertFalse(gate.sample(f(.2, 200, 10000)));
        assertTrue(gate.sample(f(.2, 200, 11000)));
    }
    @Test public void slowerOcrUsesConsecutiveFrameAgeForConfirmation() {
        RelayGate gate=new RelayGate(.15);gate.sample(f(.2,100,1000));
        assertFalse(gate.sample(f(.8,150,6500)));
        assertTrue(gate.sample(f(.8,150,12000)));
    }
    @Test public void distanceParserRejectsAmbiguityAndDroppedDecimal() {
        assertEquals(Integer.valueOf(120), RelayGate.parseMeters("0.12 km"));
        assertEquals(Integer.valueOf(125), RelayGate.parseMeters("0,125 KM"));
        assertEquals(Integer.valueOf(40), RelayGate.parseMeters("40 m"));
        assertEquals(Integer.valueOf(2300), RelayGate.parseMeters("2.30"));
        assertNull(RelayGate.parseMeters("0.12 km 02:30"));
        assertNull(RelayGate.parseMeters("012"));
        assertNull(RelayGate.parseMeters("-0.12 km"));
        assertNull(RelayGate.parseMeters("loading"));
    }
}
