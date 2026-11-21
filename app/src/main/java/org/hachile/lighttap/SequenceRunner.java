package org.hachile.lighttap;

/** Main-thread state machine. Android-specific gesture and scheduling code stays at the boundary. */
public final class SequenceRunner {
    public enum State { IDLE, COUNTDOWN, RUNNING, FINISHED, STOPPED, ERROR }
    public interface Scheduler {
        void post(Runnable task, long delayMs);
        void cancel(Runnable task);
        void clear();
    }
    public interface Result { void complete(boolean success); }
    public interface Driver { boolean dispatch(ClickStep step, Result result); }
    public interface Listener { void changed(State state, int cycle, int step, String message); }

    private final Scheduler scheduler;
    private final Driver driver;
    private final Listener listener;
    private State state = State.IDLE;
    private ClickPlan plan;
    private long generation, gesture;
    private int cycle, index;
    private boolean awaiting;

    public SequenceRunner(Scheduler scheduler, Driver driver, Listener listener) {
        this.scheduler = scheduler; this.driver = driver; this.listener = listener;
    }

    public boolean isRunning() { return state == State.COUNTDOWN || state == State.RUNNING; }
    public State state() { return state; }

    public boolean start(ClickPlan plan) {
        if (isRunning()) return false;
        if (plan == null) throw new IllegalArgumentException("点击配置不能为空");
        this.plan = plan; cycle = 1; index = 0; awaiting = false;
        long run = ++generation;
        countdown(run, 3);
        return true;
    }

    private void countdown(long run, int seconds) {
        if (run != generation) return;
        if (seconds == 0) { dispatch(run); return; }
        update(State.COUNTDOWN, "准备开始，剩余 " + seconds + " 秒");
        scheduler.post(() -> countdown(run, seconds - 1), 1000);
    }

    private void dispatch(long run) {
        if (run != generation) return;
        ClickStep step = plan.steps.get(index);
        update(State.RUNNING, "正在点击");
        long attempt = ++gesture;
        awaiting = true;
        Runnable timeout = () -> {
            if (current(run, attempt)) finish(State.ERROR, "手势回调超时，已停止");
        };
        scheduler.post(timeout, step.durationMs + 2000L);
        try {
            boolean accepted = driver.dispatch(step, success -> {
                if (!current(run, attempt)) return;
                awaiting = false; scheduler.cancel(timeout);
                if (!success) { finish(State.ERROR, "手势被取消，已停止"); return; }
                if (index == plan.steps.size() - 1 && cycle == plan.loops) {
                    finish(State.FINISHED, "已完成全部循环"); return;
                }
                update(State.RUNNING, "等待 " + step.waitMs + " 毫秒");
                if (++index == plan.steps.size()) { index = 0; cycle++; }
                scheduler.post(() -> dispatch(run), step.waitMs);
            });
            if (!accepted && current(run, attempt)) finish(State.ERROR, "系统未接受手势，已停止");
        } catch (RuntimeException e) {
            if (current(run, attempt)) finish(State.ERROR, "手势执行失败，已停止");
        }
    }

    private boolean current(long run, long attempt) {
        return run == generation && attempt == gesture && awaiting && isRunning();
    }

    public void stop(String reason) { finish(State.STOPPED, reason); }

    private void finish(State next, String message) {
        ++generation; awaiting = false; scheduler.clear(); update(next, message);
    }

    private void update(State next, String message) {
        state = next; listener.changed(next, cycle, index + 1, message);
    }
}
