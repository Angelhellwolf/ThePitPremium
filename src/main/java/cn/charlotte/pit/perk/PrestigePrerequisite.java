package cn.charlotte.pit.perk;

import cn.charlotte.pit.data.PlayerProfile;

public interface PrestigePrerequisite {

    boolean hasUnlockedRequirement(PlayerProfile profile);

    String getRequirementDescription();

    String getRequirementFailMessage();
}
