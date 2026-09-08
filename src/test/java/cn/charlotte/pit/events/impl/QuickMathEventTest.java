package cn.charlotte.pit.events.impl;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.reward.RewardService;
import cn.charlotte.pit.reward.RewardSource;
import cn.charlotte.pit.util.chat.CC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class QuickMathEventTest {
    private final UUID uuid = UUID.randomUUID();
    private QuickMathEvent round;
    private Player player;
    private PlayerProfile profile;
    private MockedStatic<RewardService> rewards;
    private MockedStatic<CC> chat;

    @Before
    public void setUp() {
        round = new QuickMathEvent();
        round.TheEquation = 42;
        round.setStartTime(System.currentTimeMillis() - 4000);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        profile = mock(PlayerProfile.class);
        when(profile.isLoaded()).thenReturn(true);
        PlayerProfile.getCacheProfile().put(uuid, profile);
        rewards = mockStatic(RewardService.class);
        rewards.when(() -> RewardService.grant(profile, RewardSource.EVENT, 200, 100))
                .thenReturn(new RewardService.Receipt(300, 150));
        chat = mockStatic(CC.class);
        chat.when(() -> CC.translate(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @After
    public void tearDown() {
        if (rewards != null) rewards.close();
        if (chat != null) chat.close();
        PlayerProfile.getCacheProfile().remove(uuid);
    }

    @Test
    public void duplicateCorrectAnswerPaysOnceAndShowsActualReceipt() throws Exception {
        assertEquals("CORRECT", answer("42", round.getStartTime() + 4000));
        assertEquals("ALREADY_ANSWERED", answer("42", round.getStartTime() + 4001));
        assertEquals(1, round.getTop());
        rewards.verify(() -> RewardService.grant(profile, RewardSource.EVENT, 200, 100), times(1));
        verify(player).sendMessage(contains("300.00 硬币 &b+150.00"));
    }

    @Test
    public void wrongAnswerAndPreviousRoundMessageDoNotConsumePlace() throws Exception {
        assertEquals("UNMATCHED", answer("41", round.getStartTime() + 4000));
        assertEquals("UNMATCHED", answer("42", round.getStartTime() - 1));
        assertEquals(0, round.getTop());
        rewards.verifyNoInteractions();
    }

    @Test
    public void sixthWinnerAndBotCannotClaimReward() throws Exception {
        round.setTop(5);
        assertEquals("UNMATCHED", answer("42", round.getStartTime() + 4000));
        round.setTop(0);
        when(profile.isBot()).thenReturn(true);
        assertEquals("UNMATCHED", answer("42", round.getStartTime() + 4000));
        assertEquals(0, round.getTop());
        rewards.verifyNoInteractions();
    }

    @Test
    public void asyncChatDefersRoundStateChangesToScheduledMainThreadWork() throws Exception {
        ThePit plugin = mock(ThePit.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            when(scheduler.callSyncMethod(eq(plugin), any(Callable.class))).thenAnswer(invocation -> {
                assertEquals(0, round.getTop());
                Field answered = QuickMathEvent.class.getDeclaredField("alreadyAnswered");
                answered.setAccessible(true);
                assertTrue(((Set<?>) answered.get(round)).isEmpty());
                Object result = ((Callable<?>) invocation.getArgument(1)).call();
                return CompletableFuture.completedFuture(result);
            });
            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, player, "42", new HashSet<>());

            round.onChat(event);

            assertTrue(event.isCancelled());
            assertEquals(1, round.getTop());
            verify(scheduler).callSyncMethod(eq(plugin), any(Callable.class));
        }
    }

    private String answer(String message, long receivedAt) throws Exception {
        Method method = QuickMathEvent.class.getDeclaredMethod("handleCorrectAnswer", Player.class, String.class, long.class);
        method.setAccessible(true);
        return ((Enum<?>) method.invoke(round, player, message, receivedAt)).name();
    }
}
