package cn.charlotte.pit.util.music;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class SongPlayer {

    protected Song song;
    protected boolean playing = false;
    protected short tick = -1;
    protected ArrayList<String> playerList = new ArrayList<String>();
    protected boolean loop;
    protected boolean autoDestroy = false;
    protected boolean destroyed = false;
    protected BukkitTask playerTask;
    private double elapsedSongTicks;
    protected byte fadeTarget = 100;
    protected byte volume = 100;
    protected byte fadeStart = volume;
    protected int fadeDuration = 60;
    protected int fadeDone = 0;
    protected FadeType fadeType = FadeType.FADE_LINEAR;

    public SongPlayer(Song song) {
        this.song = song;
        createTask();
    }

    public FadeType getFadeType() {
        return fadeType;
    }

    public void setFadeType(FadeType fadeType) {
        this.fadeType = fadeType;
    }

    public byte getFadeTarget() {
        return fadeTarget;
    }

    public void setFadeTarget(byte fadeTarget) {
        this.fadeTarget = fadeTarget;
    }

    public byte getFadeStart() {
        return fadeStart;
    }

    public void setFadeStart(byte fadeStart) {
        this.fadeStart = fadeStart;
    }

    public int getFadeDuration() {
        return fadeDuration;
    }

    public void setFadeDuration(int fadeDuration) {
        this.fadeDuration = fadeDuration;
    }

    public int getFadeDone() {
        return fadeDone;
    }

    public void setFadeDone(int fadeDone) {
        this.fadeDone = fadeDone;
    }

    protected void calculateFade() {
        if (fadeDone == fadeDuration) {
            return; // no fade today
        }
        double targetVolume = Interpolator.interpLinear(new double[]{0, fadeStart, fadeDuration, fadeTarget}, fadeDone);
        setVolume((byte) targetVolume);
        fadeDone++;
    }

    protected void createTask() {
        // The old implementation created one MAX_PRIORITY native thread per song,
        // kept it alive after playback stopped, and called Bukkit from that thread.
        // A server-tick task preserves the NBS cadence without leaking threads or
        // racing player/network state.
        playerTask = Bukkit.getScheduler().runTaskTimer(
                ThePit.getInstance(), this::advanceOneServerTick, 1L, 1L
        );
    }

    private synchronized void advanceOneServerTick() {
        if (destroyed) {
            cancelTask();
            return;
        }
        if (!playing) {
            elapsedSongTicks = 0.0D;
            return;
        }

        elapsedSongTicks += 1.0D;
        final double noteDelayTicks = Math.max(1.0D, song.getDelay());
        if (elapsedSongTicks < noteDelayTicks) {
            return;
        }
        elapsedSongTicks -= noteDelayTicks;

        calculateFade();
        tick++;
        if (tick > song.getLength()) {
            if (loop) {
                tick = 0;
            } else {
                playing = false;
                tick = -1;
                SongEndEvent event = new SongEndEvent(this);
                Bukkit.getPluginManager().callEvent(event);
                if (autoDestroy) {
                    destroy();
                }
                return;
            }
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            playTick(onlinePlayer, tick);
        }
    }

    private void cancelTask() {
        if (playerTask != null) {
            playerTask.cancel();
            playerTask = null;
        }
    }

    public List<String> getPlayerList() {
        return Collections.unmodifiableList(playerList);
    }

    public void addPlayer(Player p) {
        synchronized (this) {
            if (!playerList.contains(p.getName())) {
                playerList.add(p.getName());
                ArrayList<SongPlayer> songs = NoteBlockPlayerMain.plugin.playingSongs
                        .get(p.getName());
                if (songs == null) {
                    songs = new ArrayList<>();
                }
                songs.add(this);
                NoteBlockPlayerMain.plugin.playingSongs.put(p.getName(), songs);
            }
        }
    }

    public boolean isLoop() {
        synchronized (this) {
            return this.loop;
        }
    }

    public void setLoop(boolean loop) {
        synchronized (this) {
            this.loop = loop;
        }
    }

    public boolean getAutoDestroy() {
        synchronized (this) {
            return autoDestroy;
        }
    }

    public void setAutoDestroy(boolean value) {
        synchronized (this) {
            autoDestroy = value;
        }
    }

    public abstract void playTick(Player p, int tick);

    public void destroy() {
        synchronized (this) {
            SongDestroyingEvent event = new SongDestroyingEvent(this);
            Bukkit.getPluginManager().callEvent(event);
            //Bukkit.getScheduler().cancelTask(threadId);
            if (event.isCancelled()) {
                return;
            }
            destroyed = true;
            playing = false;
            setTick((short) -1);
            cancelTask();
        }
    }

    public synchronized boolean isPlaying() {
        return playing;
    }

    public synchronized void setPlaying(boolean playing) {
        this.playing = playing;
        if (!playing) {
            SongStoppedEvent event = new SongStoppedEvent(this);
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    public short getTick() {
        return tick;
    }

    public void setTick(short tick) {
        this.tick = tick;
    }

    public void removePlayer(Player p) {
        synchronized (this) {
            playerList.remove(p.getName());
            if (NoteBlockPlayerMain.plugin.playingSongs.get(p.getName()) == null) {
                return;
            }
            ArrayList<SongPlayer> songs = new ArrayList<SongPlayer>(
                    NoteBlockPlayerMain.plugin.playingSongs.get(p.getName()));
            songs.remove(this);
            NoteBlockPlayerMain.plugin.playingSongs.put(p.getName(), songs);
            if (playerList.isEmpty() && autoDestroy) {
                SongEndEvent event = new SongEndEvent(this);
                Bukkit.getPluginManager().callEvent(event);
                destroy();
            }
        }
    }

    public byte getVolume() {
        return volume;
    }

    public void setVolume(byte volume) {
        this.volume = volume;
    }

    public Song getSong() {
        return song;
    }
}
