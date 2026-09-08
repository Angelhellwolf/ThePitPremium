package cn.charlotte.pit.events;

import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.events.impl.major.SpireEvent;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EventQueueSelectorTest {
    private IEvent event(String name, int required) {
        IEvent event = mock(IEvent.class);
        when(event.getEventInternalName()).thenReturn(name);
        when(event.requireOnline()).thenReturn(required);
        return event;
    }

    @Test
    public void fivePlayersCanDrawSpireBehindAnUnavailableMajorEvent() {
        Queue<String> queue = new LinkedList<>(Arrays.asList("squads", "spire", "ham"));
        List<IEvent> events = Arrays.asList(event("squads", 999), event("spire", 5), event("ham", 5));
        assertEquals("spire", EventQueueSelector.pollEligible(queue, events, 5));
        assertEquals(Arrays.asList("ham", "squads"), new ArrayList<>(queue));
    }

    @Test
    public void insufficientPopulationKeepsFutureEventsWithoutLooping() {
        Queue<String> queue = new LinkedList<>(Arrays.asList("spire", "squads"));
        List<IEvent> events = Arrays.asList(event("spire", 5), event("squads", 999));
        assertNull(EventQueueSelector.pollEligible(queue, events, 4));
        assertEquals(Arrays.asList("spire", "squads"), new ArrayList<>(queue));
        assertEquals("spire", EventQueueSelector.pollEligible(queue, events, 5));
    }

    @Test
    public void removedEventsAreDiscardedAndEmptyRegistriesAreSafe() {
        Queue<String> queue = new LinkedList<>(Arrays.asList("removed", "spire"));
        assertEquals("spire", EventQueueSelector.pollEligible(queue, Collections.singletonList(event("spire", 5)), 5));
        assertTrue(queue.isEmpty());
        queue.add("removed");
        assertNull(EventQueueSelector.pollEligible(queue, Collections.emptyList(), 20));
        assertTrue(queue.isEmpty());
    }

    @Test
    public void queueFilledWithDeferredLargeEventsStillDrawsSpireAtFivePlayers() {
        Queue<String> queue = new LinkedList<>(Collections.nCopies(50, "squads"));
        List<IEvent> events = Arrays.asList(event("squads", 999), event("spire", 5));
        for (int draw = 0; draw < 10; draw++) {
            assertEquals("spire", EventQueueSelector.pollEligible(queue, events, 5));
            assertEquals(50, queue.size());
        }
    }

    @Test
    public void spireDefaultsToFiveWhileRespectingExplicitConfiguration() {
        Map<String, Integer> requirements = NewConfiguration.INSTANCE.getEventOnlineRequired();
        Integer previous = requirements.remove("spire");
        try {
            assertEquals(5, new SpireEvent().requireOnline());
            requirements.put("spire", 10);
            assertEquals(10, new SpireEvent().requireOnline());
        } finally {
            if (previous == null) requirements.remove("spire");
            else requirements.put("spire", previous);
        }
    }
}
