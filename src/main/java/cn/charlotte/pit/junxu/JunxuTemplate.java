package cn.charlotte.pit.junxu;

import cn.charlotte.pit.ThePit;
import org.bukkit.*;
import org.bukkit.block.Sign;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/** A fully furnished, versioned template shared by the offline schematic and private instances. */
public final class JunxuTemplate {
    public static final String REVISION = "seventh-r1";
    private final ThePit plugin;
    private final World world;
    private final byte[] blocks, data;
    private final int width, height, length, perTick;
    private final List<Label> labels = new ArrayList<>();
    private final Queue<Build> queue = new ArrayDeque<>();
    private BukkitTask task;
    private Build active;
    private int index;

    public JunxuTemplate(ThePit plugin, World world, int perTick) throws IOException {
        this.plugin=plugin; this.world=world; this.perTick=Math.max(500, Math.min(10000, perTick));
        InputStream resource=plugin.getResource("junxu/template.bin.gz");
        if (resource == null) throw new IOException("Missing Junxu template resource");
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(resource))) {
            if (in.readInt()!=0x4a585431) throw new IOException("Invalid template header");
            width=in.readInt(); height=in.readInt(); length=in.readInt();
            if (width!=61 || height!=51 || length!=61) throw new IOException("Unexpected template dimensions");
            blocks=new byte[width*height*length]; data=new byte[blocks.length]; in.readFully(blocks); in.readFully(data);
            int count=in.readInt(); if (count<0 || count>100) throw new IOException("Invalid template signs");
            for (int i=0;i<count;i++) {
                Label label=new Label(in.readInt(), in.readInt(), in.readInt());
                for (int line=0;line<4;line++) {
                    int len=in.readUnsignedShort(); if (len>1024) throw new IOException("Invalid template sign line");
                    byte[] text=new byte[len]; in.readFully(text); label.lines[line]=new String(text,StandardCharsets.UTF_8);
                }
                labels.add(label);
            }
            if (in.read()!=-1) throw new IOException("Unexpected template bytes");
        }
    }
    public void build(int slot, Consumer<Boolean> complete) {
        queue.add(new Build(slot,complete));
        if (task==null) task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    @SuppressWarnings("deprecation") private void tick() {
        if (active==null) { active=queue.poll(); index=0; }
        if (active==null) { task.cancel(); task=null; return; }
        try {
            int work=0, scan=0;
            while (index<blocks.length && work<perTick && scan<perTick*8) {
                int i=index++, b=blocks[i]&255; scan++;
                int x=i%width, z=(i/width)%length, y=i/(width*length);
                org.bukkit.block.Block block=world.getBlockAt(JunxuRoom.originX(active.slot)+x,48+y,JunxuRoom.originZ(active.slot)+z);
                if (block.getTypeId()!=b || block.getData()!=data[i]) { block.setTypeIdAndData(b,data[i],false);work++; }
            }
            if (index<blocks.length) return;
            for (Label label:labels) {
                org.bukkit.block.BlockState state=world.getBlockAt(JunxuRoom.originX(active.slot)+label.x,48+label.y,
                        JunxuRoom.originZ(active.slot)+label.z).getState();
                if (state instanceof Sign) {
                    Sign sign=(Sign)state;for(int i=0;i<4;i++)sign.setLine(i,label.lines[i]);sign.update(true,false);
                }
            }
            world.save();
            Build done=active; active=null; done.complete.accept(true);
            plugin.getLogger().info("JUNXU_TEMPLATE built slot="+done.slot+" revision="+REVISION+" blocks="+nonAir());
        } catch (Exception error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"军需处模板生成失败",error);
            Build failed=active; active=null;
            if (failed!=null) failed.complete.accept(false);
        }
    }
    public int queued() { return queue.size()+(active==null?0:1); }
    public int nonAir() { int count=0;for(byte block:blocks)if(block!=0)count++;return count; }
    public void shutdown() {
        if(task!=null)task.cancel();task=null;
        queue.clear();active=null;
    }
    public static World createWorld(String name) {
        World world=Bukkit.getWorld(name);
        if(world==null)world=new WorldCreator(name).generator(new VoidGenerator()).environment(World.Environment.NORMAL)
                .generateStructures(false).createWorld();
        if(world==null)throw new IllegalStateException("Unable to create Junxu void world");
        world.setKeepSpawnInMemory(false);world.setAutoSave(true);world.setPVP(true);
        world.setGameRuleValue("doMobSpawning","false");world.setGameRuleValue("mobGriefing","false");
        world.setGameRuleValue("doFireTick","false");world.setGameRuleValue("doDaylightCycle","false");
        world.setGameRuleValue("keepInventory","true");world.setStorm(false);world.setThundering(false);world.setTime(18000);
        world.setSpawnLocation(30,71,11);
        return world;
    }
    public static final class VoidGenerator extends ChunkGenerator {
        @Override public byte[][] generateBlockSections(World world, Random random,int x,int z,BiomeGrid biomes) {
            for(int a=0;a<16;a++)for(int b=0;b<16;b++)biomes.setBiome(a,b,org.bukkit.block.Biome.SKY);
            return new byte[16][];
        }
        @Override public Location getFixedSpawnLocation(World world,Random random) { return new Location(world,30.5,71,11.5); }
    }
    private static final class Build { final int slot;final Consumer<Boolean> complete;Build(int slot,Consumer<Boolean> complete){this.slot=slot;this.complete=complete;} }
    private static final class Label { final int x,y,z;final String[] lines=new String[4];Label(int x,int y,int z){this.x=x;this.y=y;this.z=z;} }
}
