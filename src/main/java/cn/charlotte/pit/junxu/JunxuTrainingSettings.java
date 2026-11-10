package cn.charlotte.pit.junxu;

/** Per-owner practice parameters. They do not change rewards or service NPCs. */
public final class JunxuTrainingSettings {
    public enum Armor {
        STANDARD("远征配装"), NONE("无盔甲"), LEATHER("皮革套装"), CHAINMAIL("锁链套装"), IRON("铁套装"), DIAMOND("钻石套装");
        public final String title; Armor(String title) { this.title = title; }
    }
    public enum Weapon {
        NONE("空手"), WOOD("木剑"), STONE("石剑"), IRON("铁剑"), DIAMOND("钻石剑");
        public final String title; Weapon(String title) { this.title = title; }
    }
    public enum Mode {
        HOSTILE("主动攻击"), RETALIATE("受击反击"), PASSIVE("不还手");
        public final String title; Mode(String title) { this.title = title; }
    }
    public enum Route {
        FORMATION("分组巡逻"), RING("环场巡逻"), CROSS("交叉巡逻"), FOLLOW("持续跟随"), STATIONARY("原地驻守");
        public final String title; Route(String title) { this.title = title; }
    }
    public enum Strength {
        EASY("轻度", 0.16F, 16), NORMAL("标准", 0.20F, 10), HARD("强劲", 0.24F, 5);
        public final String title; public final float speed; public final int attackTicks;
        Strength(String title, float speed, int attackTicks) { this.title=title; this.speed=speed; this.attackTicks=attackTicks; }
    }
    public final double health;
    public final Armor armor;
    public final Weapon weapon;
    public final Mode mode;
    public final Route route;
    public final Strength strength;
    public final boolean paused;
    public JunxuTrainingSettings(double health, Armor armor, Weapon weapon, Mode mode) {
        this(health,armor,weapon,mode,Route.FORMATION,Strength.NORMAL,false);
    }
    public JunxuTrainingSettings(double health, Armor armor, Weapon weapon, Mode mode, Route route, Strength strength, boolean paused) {
        if (!Double.isFinite(health) || health < 1 || health > 2048) throw new IllegalArgumentException("Invalid practice health");
        this.health = health; this.armor = java.util.Objects.requireNonNull(armor);
        this.weapon = java.util.Objects.requireNonNull(weapon); this.mode = java.util.Objects.requireNonNull(mode);
        this.route=java.util.Objects.requireNonNull(route);this.strength=java.util.Objects.requireNonNull(strength);this.paused=paused;
    }
    public static JunxuTrainingSettings defaults() { return new JunxuTrainingSettings(20, Armor.STANDARD, Weapon.IRON, Mode.HOSTILE); }
    public JunxuTrainingSettings health(double value) { return new JunxuTrainingSettings(value, armor, weapon, mode,route,strength,paused); }
    public JunxuTrainingSettings armor(Armor value) { return new JunxuTrainingSettings(health, value, weapon, mode,route,strength,paused); }
    public JunxuTrainingSettings weapon(Weapon value) { return new JunxuTrainingSettings(health, armor, value, mode,route,strength,paused); }
    public JunxuTrainingSettings mode(Mode value) { return new JunxuTrainingSettings(health, armor, weapon, value,route,strength,paused); }
    public JunxuTrainingSettings route(Route value) { return new JunxuTrainingSettings(health,armor,weapon,mode,value,strength,paused); }
    public JunxuTrainingSettings strength(Strength value) { return new JunxuTrainingSettings(health,armor,weapon,mode,route,value,paused); }
    public JunxuTrainingSettings paused(boolean value) { return new JunxuTrainingSettings(health,armor,weapon,mode,route,strength,value); }
}
