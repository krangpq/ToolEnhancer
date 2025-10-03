package com.krangpq.toolenhancer.api;

import com.krangpq.toolenhancer.ToolEnhancer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.ArrayList;

/**
 * ToolEnhancer Public API
 *
 * 다른 플러그인에서 이 클래스를 사용하여 ToolEnhancer의 기능에 접근할 수 있습니다.
 *
 * <h3>사용 예시:</h3>
 * <pre>{@code
 * // API 사용 가능 여부 확인
 * if (ToolEnhancerAPI.isEnabled()) {
 *     // 특정 인챈트의 강화 레벨 조회
 *     int sharpnessLevel = ToolEnhancerAPI.getEnhanceLevel(item, Enchantment.SHARPNESS);
 *
 *     // 총 강화 레벨 (모든 인챈트)
 *     int totalLevel = ToolEnhancerAPI.getTotalEnhanceLevel(item);
 *
 *     // 강화 가능 여부 확인
 *     if (ToolEnhancerAPI.canEnhance(item)) {
 *         // 강화 가능한 아이템
 *     }
 * }
 * }</pre>
 *
 * <h3>다른 플러그인의 plugin.yml 설정:</h3>
 * <pre>
 * depend: []
 * softdepend:
 *   - ToolEnhancer
 * </pre>
 *
 * @since 1.0.0
 * @author krangpq
 */
public class ToolEnhancerAPI {

    private static ToolEnhancer plugin;

    /**
     * 내부용 - 플러그인 인스턴스 설정
     * ToolEnhancer 메인 클래스의 onEnable()에서 호출됩니다.
     *
     * @param instance 플러그인 인스턴스
     */
    public static void setPlugin(ToolEnhancer instance) {
        plugin = instance;
    }

    /**
     * API 사용 가능 여부 확인
     * 다른 모든 API 메서드를 호출하기 전에 반드시 이 메서드로 확인해야 합니다.
     *
     * @return 플러그인이 활성화되어 있으면 true
     * @since 1.0.0
     */
    public static boolean isEnabled() {
        return plugin != null && plugin.isEnabled();
    }

    /**
     * 플러그인 버전 반환
     *
     * @return 버전 문자열 (예: "1.0.6")
     * @since 1.0.0
     */
    public static String getVersion() {
        if (!isEnabled()) {
            return "Unknown";
        }

        try {
            return plugin.getDescription().getVersion();
        } catch (Exception e) {
            plugin.getLogger().warning("API 버전 조회 중 오류: " + e.getMessage());
            return "Unknown";
        }
    }

    // ============================================
    // 강화 레벨 조회 API
    // ============================================

    /**
     * 특정 인챈트의 강화 레벨 조회
     *
     * <p>바닐라 최대 레벨을 초과하는 강화 레벨을 반환합니다.
     * 예: 날카로움 V(5)가 +7까지 강화되었다면 7을 반환합니다.</p>
     *
     * @param item 대상 아이템
     * @param enchantment 조회할 인챈트
     * @return 강화 레벨 (0 = 인챈트 없음 또는 강화 안됨)
     * @since 1.0.0
     */
    public static int getEnhanceLevel(ItemStack item, Enchantment enchantment) {
        // 파라미터 검증
        if (!isEnabled()) {
            return 0;
        }
        if (item == null) {
            plugin.getLogger().warning("API 호출 오류: item이 null입니다 (getEnhanceLevel)");
            return 0;
        }
        if (enchantment == null) {
            plugin.getLogger().warning("API 호출 오류: enchantment가 null입니다 (getEnhanceLevel)");
            return 0;
        }

        try {
            // 아이템에 해당 인챈트가 있는지 확인
            if (!item.containsEnchantment(enchantment)) {
                return 0;
            }

            // 현재 인챈트 레벨 반환
            return item.getEnchantmentLevel(enchantment);

        } catch (Exception e) {
            plugin.getLogger().warning("API getEnhanceLevel() 호출 중 오류: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 아이템의 총 강화 레벨 조회
     *
     * <p>모든 인챈트의 레벨을 합산하여 반환합니다.
     * 예: 날카로움 VII(7) + 내구성 V(5) = 12를 반환합니다.</p>
     *
     * @param item 대상 아이템
     * @return 총 강화 레벨 (0 = 인챈트 없음)
     * @since 1.0.0
     */
    public static int getTotalEnhanceLevel(ItemStack item) {
        // 파라미터 검증
        if (!isEnabled()) {
            return 0;
        }
        if (item == null) {
            plugin.getLogger().warning("API 호출 오류: item이 null입니다 (getTotalEnhanceLevel)");
            return 0;
        }

        try {
            int total = 0;

            // 모든 인챈트의 레벨 합산
            for (Enchantment ench : item.getEnchantments().keySet()) {
                total += item.getEnchantmentLevel(ench);
            }

            return total;

        } catch (Exception e) {
            plugin.getLogger().warning("API getTotalEnhanceLevel() 호출 중 오류: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 특정 인챈트가 바닐라 최대 레벨을 초과했는지 확인
     *
     * @param item 대상 아이템
     * @param enchantment 확인할 인챈트
     * @return 바닐라 최대 레벨을 초과했으면 true
     * @since 1.0.0
     */
    public static boolean isBeyondVanillaMax(ItemStack item, Enchantment enchantment) {
        // 파라미터 검증
        if (!isEnabled()) {
            return false;
        }
        if (item == null || enchantment == null) {
            return false;
        }

        try {
            int currentLevel = item.getEnchantmentLevel(enchantment);
            int vanillaMax = enchantment.getMaxLevel();

            return currentLevel > vanillaMax;

        } catch (Exception e) {
            plugin.getLogger().warning("API isBeyondVanillaMax() 호출 중 오류: " + e.getMessage());
            return false;
        }
    }

    // ============================================
    // 강화 가능 여부 확인 API
    // ============================================

    /**
     * 아이템이 강화 가능한지 확인
     *
     * <p>도구, 무기, 방어구 등 인챈트 가능한 아이템만 true를 반환합니다.</p>
     *
     * @param item 대상 아이템
     * @return 강화 가능하면 true
     * @since 1.0.0
     */
    public static boolean canEnhance(ItemStack item) {
        // 파라미터 검증
        if (!isEnabled()) {
            return false;
        }
        if (item == null) {
            return false;
        }

        try {
            return plugin.getEnhanceManager().canEnhance(item);

        } catch (Exception e) {
            plugin.getLogger().warning("API canEnhance() 호출 중 오류: " + e.getMessage());
            return false;
        }
    }

    /**
     * 특정 인챈트를 더 강화할 수 있는지 확인
     *
     * @param item 대상 아이템
     * @param enchantment 확인할 인챈트
     * @return 더 강화 가능하면 true
     * @since 1.0.0
     */
    public static boolean canEnhanceFurther(ItemStack item, Enchantment enchantment) {
        // 파라미터 검증
        if (!isEnabled()) {
            return false;
        }
        if (item == null || enchantment == null) {
            return false;
        }

        try {
            // 기본적으로 강화 가능한 아이템인지 확인
            if (!canEnhance(item)) {
                return false;
            }

            // 해당 인챈트를 적용할 수 있는지 확인
            if (!enchantment.canEnchantItem(item)) {
                return false;
            }

            // 현재 레벨이 절대 최대 레벨 미만인지 확인
            int currentLevel = item.getEnchantmentLevel(enchantment);
            int vanillaMax = enchantment.getMaxLevel();
            int absoluteMax = plugin.getEnhanceStoneManager().getAbsoluteMaxLevel(vanillaMax);

            return currentLevel < absoluteMax;

        } catch (Exception e) {
            plugin.getLogger().warning("API canEnhanceFurther() 호출 중 오류: " + e.getMessage());
            return false;
        }
    }

    // ============================================
    // 강화 확률 조회 API
    // ============================================

    /**
     * 강화 성공 확률 계산
     *
     * <p>강화석 개수에 따른 예상 성공률을 반환합니다.</p>
     *
     * @param item 대상 아이템
     * @param enchantment 강화할 인챈트
     * @param stoneCount 사용할 강화석 개수
     * @return 성공 확률 (0.0 ~ 1.0), 계산 불가 시 0.0
     * @since 1.0.0
     */
    public static double getSuccessRate(ItemStack item, Enchantment enchantment, int stoneCount) {
        // 파라미터 검증
        if (!isEnabled()) {
            return 0.0;
        }
        if (item == null || enchantment == null) {
            return 0.0;
        }
        if (stoneCount < 0) {
            return 0.0;
        }

        try {
            int currentLevel = item.getEnchantmentLevel(enchantment);
            int vanillaMax = enchantment.getMaxLevel();

            // 기본 성공률 계산
            double baseRate = plugin.getEnhanceStoneManager()
                    .getBaseSuccessRate(currentLevel, vanillaMax);

            // 강화석 보너스 적용
            double finalRate = plugin.getEnhanceStoneManager()
                    .calculateSuccessRate(stoneCount, baseRate);

            return finalRate;

        } catch (Exception e) {
            plugin.getLogger().warning("API getSuccessRate() 호출 중 오류: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * 아이템 파괴 확률 계산
     *
     * <p>강화 실패 시 아이템이 파괴될 확률을 반환합니다.</p>
     *
     * @param item 대상 아이템
     * @param enchantment 강화할 인챈트
     * @param stoneCount 사용할 강화석 개수
     * @return 파괴 확률 (0.0 ~ 1.0), 계산 불가 시 0.0
     * @since 1.0.0
     */
    public static double getDestroyRate(ItemStack item, Enchantment enchantment, int stoneCount) {
        // 파라미터 검증
        if (!isEnabled()) {
            return 0.0;
        }
        if (item == null || enchantment == null) {
            return 0.0;
        }
        if (stoneCount < 0) {
            return 0.0;
        }

        try {
            int currentLevel = item.getEnchantmentLevel(enchantment);
            int nextLevel = currentLevel + 1;

            return plugin.getEnhanceStoneManager()
                    .calculateDestroyRate(stoneCount, nextLevel);

        } catch (Exception e) {
            plugin.getLogger().warning("API getDestroyRate() 호출 중 오류: " + e.getMessage());
            return 0.0;
        }
    }

    // ============================================
    // 유틸리티 API
    // ============================================

    /**
     * 특정 레벨 강화에 필요한 최소 강화석 개수 조회
     *
     * @param nextLevel 강화하려는 레벨 (현재 레벨 + 1)
     * @return 최소 필요 강화석 개수
     * @since 1.0.0
     */
    public static int getMinRequiredStones(int nextLevel) {
        if (!isEnabled()) {
            return 0;
        }

        try {
            return plugin.getEnhanceStoneManager().getMinRequiredStones(nextLevel);

        } catch (Exception e) {
            plugin.getLogger().warning("API getMinRequiredStones() 호출 중 오류: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 아이템이 강화석인지 확인
     *
     * @param item 확인할 아이템
     * @return 강화석이면 true
     * @since 1.0.0
     */
    public static boolean isEnhanceStone(ItemStack item) {
        if (!isEnabled()) {
            return false;
        }
        if (item == null) {
            return false;
        }

        try {
            return plugin.getEnhanceStoneManager().isEnhanceStone(item);

        } catch (Exception e) {
            plugin.getLogger().warning("API isEnhanceStone() 호출 중 오류: " + e.getMessage());
            return false;
        }

    }
    /**
     * 강화석 아이템 생성
     */
    public static ItemStack createEnhancementStone() {
        if (!isEnabled()) {
            return null;
        }

        try {
            ItemStack stone = plugin.getEnhanceStoneManager().createEnhanceStone(1);

            // 고유 태그 추가
            ItemMeta meta = stone.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add("§8§l[ENHANCEMENT_STONE]"); // 숨겨진 태그
                meta.setLore(lore);
                stone.setItemMeta(meta);
            }

            return stone;
        } catch (Exception e) {
            plugin.getLogger().warning("API createEnhancementStone() 호출 중 오류: " + e.getMessage());
            return null;
        }
    }
}