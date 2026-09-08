package cn.charlotte.pit.bot;

import org.junit.Test;
import static org.junit.Assert.*;

public class PublicNpcNametagsTest {
    @Test public void legacyFallbackPreservesColorsAndCompleteColorCodes(){
        String[] parts=PublicNpcNametags.legacy("§7[§6XXV§7-§b120§7] §e", " §e✬ §6" );
        assertTrue(parts[0].startsWith("§7"));assertTrue(parts[0].length()<=16);assertTrue(parts[1].length()<=16);
        assertFalse(parts[0].endsWith("§"));assertFalse(parts[1].endsWith("§"));assertTrue(parts[1].contains("✬"));
    }
}
