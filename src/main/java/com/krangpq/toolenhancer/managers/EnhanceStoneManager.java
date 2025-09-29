package com.krangpq.toolenhancer.managers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;

public class EnhanceStoneManager {

    private final Plugin plugin;
    public static final String ENHANCE_STONE_KEY = "ENHANCE_STONE";

    public EnhanceStoneManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 강화석 아이템 생성
     */
    public ItemStack createEnhanceStone(int amount) {
        ItemStack stone = new ItemStack(Material.NETHER_STAR, amount);
        ItemMeta meta = stone.getItemMeta();

        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "강화석");
        List<String> lore = Arrays.asList(
                ChatColor.GRAY + "도구와 장비를 강화할 때 사용되는",
                ChatColor.GRAY + "신비한 돌입니다.",
                "",
                ChatColor.YELLOW + "더 많은 강화석을 사용할수록",
                ChatColor.YELLOW + "성공 확률이 높아집니다!",
                "",
                ChatColor.DARK_PURPLE + "✦ 강화석 ✦"
        );
        meta.setLore(lore);

        // CustomModelData로 구분 (선택사항)
        meta.setCustomModelData(12345);

        stone.setItemMeta(meta);
        return stone;
    }

    /**
     * 아이템이 강화석인지 확인
     */
    public boolean isEnhanceStone(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) {
            return false;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        String displayName = item.getItemMeta().getDisplayName();
        return displayName.equals(ChatColor.LIGHT_PURPLE + "강화석");
    }

    /**
     * 강화석 개수에 따른 확률 보너스 계산 (수정된 버전)
     * @param stoneCount 강화석 개수
     * @param baseRate 기본 성공률
     * @return 최종 성공률 (0.0 ~ 1.0)
     */
    public double calculateSuccessRate(int stoneCount, double baseRate) {
        if (stoneCount <= 0) {
            return 0.0; // 강화석이 없으면 강화 불가
        }

        // 안전한 기본 성공률 설정 (음수나 1보다 큰 값 방지)
        double safeBateRate = Math.max(0.0, Math.min(1.0, baseRate));

        // 강화석 1개당 5% 보너스, 최대 40% 보너스까지
        double bonus = Math.min(stoneCount * 0.05, 0.40);

        // 최종 성공률은 최대 95%까지
        double finalRate = Math.min(safeBateRate + bonus, 0.95);

        return Math.max(0.0, finalRate); // 음수 방지
    }

    /**
     * 강화석 개수에 따른 파괴 확률 계산 (수정된 버전)
     * @param stoneCount 강화석 개수
     * @param level 강화하려는 레벨 (현재 레벨 + 1)
     * @return 파괴 확률 (0.0 ~ 1.0)
     */
    public double calculateDestroyRate(int stoneCount, int level) {
        if (level <= 3) {
            return 0.0; // +3 이하는 파괴되지 않음
        }

        // 기본 파괴율 계산 - 레벨이 높을수록 위험하지만 합리적인 범위로 제한
        double baseDestroyRate;
        if (level <= 5) {
            baseDestroyRate = (level - 3) * 0.05; // +4: 5%, +5: 10%
        } else if (level <= 7) {
            baseDestroyRate = 0.10 + (level - 5) * 0.10; // +6: 20%, +7: 30%
        } else if (level <= 10) {
            baseDestroyRate = 0.30 + (level - 7) * 0.15; // +8: 45%, +9: 60%, +10: 75%
        } else {
            // +11 이상은 매우 위험하지만 최대 90%까지만
            baseDestroyRate = Math.min(0.90, 0.75 + (level - 10) * 0.05);
        }

        // 강화석이 많을수록 파괴율 감소 (최대 30% 감소)
        double reduction = Math.min(stoneCount * 0.02, 0.30);

        // 최종 파괴율 계산 (음수가 되지 않도록)
        double finalDestroyRate = Math.max(0.0, baseDestroyRate - reduction);

        // 파괴율은 최대 90%까지만
        return Math.min(finalDestroyRate, 0.90);
    }

    /**
     * 레벨별 기본 성공률 계산 (새로 추가)
     * @param currentLevel 현재 인챈트 레벨
     * @param maxLevel 해당 인챈트의 최대 레벨
     * @return 기본 성공률 (0.0 ~ 1.0)
     */
    public double getBaseSuccessRate(int currentLevel, int maxLevel) {
        if (currentLevel < 0) {
            return 0.8; // 인챈트가 없는 경우 (0 -> 1)
        }

        // 바닐라 최대 레벨까지는 상대적으로 쉽게
        if (currentLevel < maxLevel) {
            return Math.max(0.3, 0.8 - (currentLevel * 0.1));
        }

        // 바닐라 최대 레벨을 넘어가는 경우 매우 어렵게
        int overLevel = currentLevel - maxLevel + 1;
        if (overLevel <= 3) {
            return Math.max(0.1, 0.3 - (overLevel * 0.05)); // 25%, 20%, 15%
        } else if (overLevel <= 6) {
            return Math.max(0.05, 0.15 - ((overLevel - 3) * 0.03)); // 12%, 9%, 6%
        } else {
            return 0.05; // 최소 5%
        }
    }

    /**
     * 최소 필요 강화석 개수 (수정된 버전)
     * @param level 강화하려는 레벨 (현재 레벨 + 1)
     * @return 최소 필요 개수
     */
    public int getMinRequiredStones(int level) {
        if (level <= 0) {
            return 1;
        } else if (level <= 3) {
            return 1; // +1~+3: 1개
        } else if (level <= 6) {
            return 2; // +4~+6: 2개
        } else if (level <= 9) {
            return 3; // +7~+9: 3개
        } else if (level <= 12) {
            return 5; // +10~+12: 5개
        } else if (level <= 15) {
            return 8; // +13~+15: 8개
        } else {
            return Math.min(15, 8 + (level - 15)); // +16 이상: 점진적 증가, 최대 15개
        }
    }

    /**
     * 강화석 레시피 등록
     */
    public void registerRecipes() {
        // 강화석 제작 레시피 (다이아몬드 + 네더의 별 조합)
        ItemStack result = createEnhanceStone(1);
        NamespacedKey key = new NamespacedKey(plugin, "enhance_stone");

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("DED", "ENE", "DED");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('E', Material.EMERALD);
        recipe.setIngredient('N', Material.NETHER_STAR);

        try {
            plugin.getServer().addRecipe(recipe);
            plugin.getLogger().info("강화석 레시피가 등록되었습니다!");
        } catch (Exception e) {
            plugin.getLogger().warning("강화석 레시피 등록에 실패했습니다: " + e.getMessage());
        }
    }
}