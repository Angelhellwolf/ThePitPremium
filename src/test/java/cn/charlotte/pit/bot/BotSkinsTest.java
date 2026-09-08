package cn.charlotte.pit.bot;

import org.junit.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import static org.junit.Assert.*;

public class BotSkinsTest {
    @Test public void bundledPlayersHaveDistinctMinecraftTexturesAndValidMojangSignatures() throws Exception {
        Properties skins = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/public-player-skins.properties")) {
            assertNotNull(input);
            skins.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        byte[] key;
        try (InputStream input = com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService.class
                .getResourceAsStream("/yggdrasil_session_pubkey.der")) {
            assertNotNull("Mojang session public key", input);
            key = input.readAllBytes();
        }
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key));
        Set<String> textures = new HashSet<>();
        assertEquals("3", skins.getProperty("count"));
        for (int i = 0; i < 3; i++) {
            String value = skins.getProperty(i + ".value");
            String payload = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            assertTrue(payload.contains("textures.minecraft.net/texture/"));
            assertTrue(textures.add(payload));
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initVerify(publicKey);
            signature.update(value.getBytes(StandardCharsets.UTF_8));
            assertTrue("Player skin " + i, signature.verify(Base64.getDecoder().decode(skins.getProperty(i + ".signature"))));
        }
        assertEquals(3, BotSkins.load(getClass().getResourceAsStream("/public-player-skins.properties")).size());
    }
}
