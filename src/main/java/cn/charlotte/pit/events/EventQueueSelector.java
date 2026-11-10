package cn.charlotte.pit.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/** Select a runnable event without consuming a future event's place in the rotation. */
public final class EventQueueSelector {
    private EventQueueSelector() {
    }

    public static String pollEligible(Queue<String> queue, Collection<? extends IEvent> events, int online) {
        Map<String, IEvent> registered = new HashMap<>();
        for (IEvent event : events) registered.put(event.getEventInternalName(), event);
        int candidates = queue.size();
        for (int i = 0; i < candidates; i++) {
            String name = queue.poll();
            IEvent event = registered.get(name);
            if (event == null) continue;
            if (online >= event.requireOnline()) return name;
            // A large event may become eligible later. Never loop over it twice in one draw.
            queue.add(name);
        }
        // Repeated low-population draws can fill the saved queue with deferred
        // large events. Keep those entries, but draw from the runnable registry.
        List<String> available = new ArrayList<>();
        for (IEvent event : events) {
            if (online >= event.requireOnline()) available.add(event.getEventInternalName());
        }
        return available.isEmpty() ? null : available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }
}
