package com.example.starfall;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tiny "run this in N ticks" helper. This is what lets particle effects animate over time. */
public final class Scheduler {
    private static final class Task {
        int ticks;
        final Runnable action;
        Task(int ticks, Runnable action) {
            this.ticks = ticks;
            this.action = action;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();

    private Scheduler() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (TASKS.isEmpty()) return;
            List<Task> due = new ArrayList<>();
            for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
                Task t = it.next();
                if (--t.ticks <= 0) {
                    due.add(t);
                    it.remove();
                }
            }
            for (Task t : due) {
                try {
                    t.action.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void later(int ticks, Runnable action) {
        if (ticks <= 0) {
            action.run();
        } else {
            TASKS.add(new Task(ticks, action));
        }
    }
}
