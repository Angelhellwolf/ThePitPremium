package cn.charlotte.pit.bot;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** Signed, configurable player skins; never fetch from the network during spawning. */
public final class BotSkins {
    private final String name, value, signature;

    private BotSkins(String name, String value, String signature) {
        this.name = name;
        this.value = value;
        this.signature = signature;
    }

    public static List<BotSkins> load(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Missing public-player-skins.properties");
        Properties data = new Properties();
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { data.load(reader); }
        int count = Integer.parseInt(data.getProperty("count", "0"));
        if (count < 1 || count > 16) throw new IOException("Invalid bot skin count");
        List<BotSkins> skins = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String value = data.getProperty(i + ".value"), signature = data.getProperty(i + ".signature");
            if (value == null || signature == null || value.isEmpty() || signature.isEmpty()) {
                throw new IOException("Incomplete signed bot skin " + i);
            }
            skins.add(new BotSkins("thepit-public-player-" + i, value, signature));
        }
        return Collections.unmodifiableList(skins);
    }

    public void apply(NPC npc) {
        SkinTrait trait = npc.getOrAddTrait(SkinTrait.class);
        trait.setShouldUpdateSkins(false);
        trait.setFetchDefaultSkin(false);
        trait.setSkinPersistent(name, signature, value);
    }
}
