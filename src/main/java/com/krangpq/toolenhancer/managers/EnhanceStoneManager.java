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

    // config.yml에서 로드할 설정값들
    private final boolean allowBeyondMax;
    private final double maxLevelMultiplier;
    private final int absoluteMaxLevel;
    private final double severityScale;
    private final double globalMinimumRate;

    public EnhanceStoneManager(Plugin plugin) {
        this.plugin = plugin;

        // config.yml에서 설정 로드
        this.allowBeyondMax = plugin.getConfig().getBoolean("success_rates.beyond_vanilla.allow_beyond_max", true);
        this.maxLevelMultiplier = plugin.getConfig().getDouble("success_rates.beyond_vanilla.max_level_limit.max_multiplier", 2.0);
        this.absoluteMaxLevel = plugin.getConfig().getInt("success_rates.beyond_vanilla.max_level_limit.absolute_max_level", 30);
        this.severityScale = plugin.getConfig().getDouble("success_rates.beyond_vanilla.severity_scale", 1.0);
        this.globalMinimumRate = plugin.getConfig().getDouble("success_rates.beyond_vanilla.global_minimum_rate", 0.01);

        plugin.getLogger().info("강화 시스템 설정 로드 완료:");
        plugin.getLogger().info("- 바닐라 최대 레벨 초과 허용: " + allowBeyondMax);
        plugin.getLogger().info("- 최대 레벨 배수: " + maxLevelMultiplier);
        plugin.getLogger().info("- 절대 최대 레벨: " + absoluteMaxLevel);
        plugin.getLogger().info("- 난이도 배율: " + severityScale);
        plugin.getLogger().info("- 전역 최소 성공률: " + (globalMinimumRate * 100) + "%");
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
     * 강화석 개수에 따른 확률 보너스 계산
     */
    public double calculateSuccessRate(int stoneCount, double baseRate) {
        if (stoneCount <= 0) {
            return 0.0;
        }

        double safeBaseRate = Math.max(0.0, Math.min(1.0, baseRate));

        // 강화석 1개당 5% 보너스, 최대 40% 보너스까지
        double bonus = Math.min(stoneCount * 0.05, 0.40);

        // 최종 성공률은 최대 95%까지
        double finalRate = Math.min(safeBaseRate + bonus, 0.95);

        return Math.max(0.0, finalRate);
    }

    /**
     * 강화석 개수에 따른 파괴 확률 계산
     */
    public double calculateDestroyRate(int stoneCount, int level) {
        if (level <= 3) {
            return 0.0;
        }

        double baseDestroyRate;
        if (level <= 5) {
            baseDestroyRate = (level - 3) * 0.05;
        } else if (level <= 7) {
            baseDestroyRate = 0.10 + (level - 5) * 0.10;
        } else if (level <= 10) {
            baseDestroyRate = 0.30 + (level - 7) * 0.15;
        } else {
            baseDestroyRate = Math.min(0.90, 0.75 + (level - 10) * 0.05);
        }

        double reduction = Math.min(stoneCount * 0.02, 0.30);
        double finalDestroyRate = Math.max(0.0, baseDestroyRate - reduction);

        return Math.min(finalDestroyRate, 0.90);
    }

    /**
     * 레벨별 기본 성공률 계산 (config.yml 설정 반영)
     */
    public double getBaseSuccessRate(int currentLevel, int maxLevel) {
        if (currentLevel < 0) {
            return 0.8; // 인챈트가 없는 경우
        }

        // 바닐라 최대 레벨까지는 상대적으로 쉽게
        if (currentLevel < maxLevel) {
            return Math.max(0.3, 0.8 - (currentLevel * 0.1));
        }

        // 바닐라 최대 레벨을 넘어가는 경우
        int overLevel = currentLevel - maxLevel + 1;

        // severityScale 적용: 0.0이면 감소 없음, 1.0이면 기존대로, 2.0이면 더 가혹하게
        double baseReduction;
        if (overLevel <= 3) {
            baseReduction = overLevel * 0.05; // 원래: 5% 씩 감소
        } else if (overLevel <= 6) {
            baseReduction = 0.15 + (overLevel - 3) * 0.03; // 원래: 3% 씩 감소
        } else {
            baseReduction = 0.24; // 최대 24% 감소
        }

        // severityScale 적용
        double scaledReduction = baseReduction * severityScale;
        double rate = 0.3 - scaledReduction;

        // globalMinimumRate 적용
        return Math.max(globalMinimumRate, rate);
    }

    /**
     * 최대 레벨 확인 (config.yml 설정 반영)
     */
    public int getAbsoluteMaxLevel(int vanillaMaxLevel) {
        if (!allowBeyondMax) {
            return vanillaMaxLevel;
        }

        // max_level_limit.enabled 확인
        boolean limitEnabled = plugin.getConfig().getBoolean("success_rates.beyond_vanilla.max_level_limit.enabled", false);

        if (limitEnabled) {
            int calculatedMax = (int) (vanillaMaxLevel * maxLevelMultiplier);
            return Math.min(calculatedMax, absoluteMaxLevel);
        } else {
            // 제한 해제면 absoluteMaxLevel만 적용
            return absoluteMaxLevel;
        }
    }

    /**
     * 최소 필요 강화석 개수
     */
    public int getMinRequiredStones(int level) {
        if (level <= 0) {
            return 1;
        } else if (level <= 3) {
            return 1;
        } else if (level <= 6) {
            return 2;
        } else if (level <= 9) {
            return 3;
        } else if (level <= 12) {
            return 5;
        } else if (level <= 15) {
            return 8;
        } else {
            return Math.min(15, 8 + (level - 15));
        }
    }

    /**
     * 강화석 레시피 등록
     */
    public void registerRecipes() {
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