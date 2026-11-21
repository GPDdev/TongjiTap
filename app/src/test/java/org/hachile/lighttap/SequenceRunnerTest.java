package org.hachile.lighttap;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SequenceRunnerTest {
    static final class Clock implements SequenceRunner.Scheduler {
        record Task(Runnable action, long when) {}
        final List<Task> tasks = new ArrayList<>();
        long now;
        public void post(Runnable r, long delay) { tasks.add(new Task(r, now + delay)); }
        public void cancel(Runnable r) { tasks.removeIf(t -> t.action == r); }
        public void clear() { tasks.clear(); }
        void next() {
            Task t = tasks.stream().min(Comparator.comparingLong(Task::when)).orElseThrow();
            tasks.remove(t); now = t.when; t.action.run();
        }
    }
    final Clock clock = new Clock();
    final List<Integer> clicks = new ArrayList<>();
    final List<Long> times = new ArrayList<>();
    final List<SequenceRunner.Result> callbacks = new ArrayList<>();
    boolean accept = true;
    SequenceRunner runner = new SequenceRunner(clock, (step, callback) -> {
        clicks.add(step.x); times.add(clock.now); callbacks.add(callback); return accept;
    }, (state, cycle, step, message) -> {});

    ClickPlan plan(int loops) {
        return new ClickPlan(loops, 1080, 2400, 0, List.of(
            new ClickStep(100, 200, 50, 500), new ClickStep(300, 400, 80, 700)));
    }
    void countdown() { clock.next(); clock.next(); clock.next(); }

    @Test public void repeatsWholeSequenceAndWaitsAfterCompletion() {
        assertTrue(runner.start(plan(2)));
        assertTrue(clicks.isEmpty()); countdown();
        assertEquals(List.of(100), clicks); assertEquals(3000, clock.now);
        clock.now += 50; callbacks.get(0).complete(true); clock.next();
        assertEquals(List.of(100, 300), clicks); assertEquals(3550, clock.now);
        clock.now += 80; callbacks.get(1).complete(true); clock.next();
        assertEquals(4330, clock.now);
        clock.now += 50; callbacks.get(2).complete(true); clock.next();
        clock.now += 80; callbacks.get(3).complete(true);
        assertEquals(List.of(100, 300, 100, 300), clicks);
        assertFalse(runner.isRunning()); assertEquals(SequenceRunner.State.FINISHED, runner.state());
        assertTrue(clock.tasks.isEmpty());
    }
    @Test public void rejectsDuplicateStartAndStopsCountdown() {
        assertTrue(runner.start(plan(1))); assertFalse(runner.start(plan(1)));
        runner.stop("停止"); assertTrue(clock.tasks.isEmpty()); assertTrue(clicks.isEmpty());
    }
    @Test public void oldCallbacksCannotRestartStoppedOrNewRun() {
        runner.start(plan(2)); countdown(); SequenceRunner.Result old = callbacks.get(0);
        runner.stop("停止"); old.complete(true); assertTrue(clock.tasks.isEmpty());
        runner.start(plan(1)); old.complete(true); countdown();
        assertEquals(List.of(100, 100), clicks);
        callbacks.get(1).complete(true); callbacks.get(1).complete(true); clock.next();
        assertEquals(List.of(100, 100, 300), clicks);
    }
    @Test public void stopsOnCancelledOrRejectedGesture() {
        runner.start(plan(1)); countdown(); callbacks.get(0).complete(false);
        assertEquals(SequenceRunner.State.ERROR, runner.state()); assertTrue(clock.tasks.isEmpty());
        accept = false; runner.start(plan(1)); countdown();
        assertEquals(SequenceRunner.State.ERROR, runner.state()); assertTrue(clock.tasks.isEmpty());
    }
    @Test public void missingCallbackTimesOutInsteadOfHanging() {
        runner.start(plan(1)); countdown(); clock.next();
        assertEquals(SequenceRunner.State.ERROR, runner.state()); assertEquals(1, clicks.size());
    }
    @Test public void stopDuringWaitCancelsNextClick() {
        runner.start(plan(1)); countdown(); callbacks.get(0).complete(true); runner.stop("停止");
        assertTrue(clock.tasks.isEmpty()); assertEquals(1, clicks.size());
    }
    @Test public void synchronousCompletionDoesNotLeaveTimeoutOrDuplicateWork() {
        SequenceRunner immediate = new SequenceRunner(clock, (step, result) -> { result.complete(true); return true; }, (s,c,i,m) -> {});
        immediate.start(new ClickPlan(1, 1080, 2400, 0, List.of(new ClickStep(1, 2, 50, 0))));
        countdown(); assertEquals(SequenceRunner.State.FINISHED, immediate.state()); assertTrue(clock.tasks.isEmpty());
    }
    @Test public void driverExceptionStopsAndClearsWatchdog() {
        SequenceRunner broken = new SequenceRunner(clock, (step, result) -> { throw new SecurityException(); }, (s,c,i,m) -> {});
        broken.start(plan(1)); countdown();
        assertEquals(SequenceRunner.State.ERROR, broken.state()); assertTrue(clock.tasks.isEmpty());
    }
    @Test public void takesImmutableSnapshot() {
        ArrayList<ClickStep> steps = new ArrayList<>(plan(1).steps);
        ClickPlan p = new ClickPlan(1, 1080, 2400, 0, steps); steps.clear();
        assertEquals(2, p.steps.size());
        assertThrows(UnsupportedOperationException.class, () -> p.steps.clear());
    }
    @Test public void validatesBoundaries() {
        assertThrows(IllegalArgumentException.class, () -> new ClickStep(-1, 0, 50, 0));
        assertThrows(IllegalArgumentException.class, () -> new ClickStep(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ClickStep(0, 0, 10001, 0));
        assertThrows(IllegalArgumentException.class, () -> new ClickStep(0, 0, 50, -1));
        assertThrows(IllegalArgumentException.class, () -> new ClickStep(0, 0, 50, 3600001));
        assertThrows(IllegalArgumentException.class, () -> new ClickPlan(0, 1080, 2400, 0, plan(1).steps));
        assertThrows(IllegalArgumentException.class, () -> new ClickPlan(100001, 1080, 2400, 0, plan(1).steps));
        assertThrows(IllegalArgumentException.class, () -> new ClickPlan(1, 1080, 2400, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ClickPlan(1, 100, 200, 0, plan(1).steps));
        new ClickPlan(100000, 1080, 2400, 0, List.of(new ClickStep(0, 2399, 10000, 3600000)));
    }
}
