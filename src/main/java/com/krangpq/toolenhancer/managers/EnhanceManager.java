package com.krangpq.toolenhancer.managers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class EnhanceManager {

    private final Plugin plugin;
    private final Random random;

    public EnhanceManager(Plugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    /**
     * 아이템이 강화 가능한지 확인
     */
    public boolean canEnhance(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        // 강화 가능한 아이템 타입 확인
        return isEnhantableItem(item.getType());
    }

    /**
     * 인챈트 가능한 아이템인지 확인
     */
    private boolean isEnhantableItem(Material type) {
        return type.toString().contains("SWORD") ||
                type.toString().contains("AXE") ||
                type.toString().contains("PICKAXE") ||
                type.toString().contains("SHOVEL") ||
                type.toString().contains("HOE") ||
                type.toString().contains("HELMET") ||
                type.toString().contains("CHESTPLATE") ||
                type.toString().contains("LEGGINGS") ||
                type.toString().contains("BOOTS") ||
                type == Material.BOW ||
                type == Material.CROSSBOW ||
                type == Material.TRIDENT ||
                type == Material.FISHING_ROD ||
                type == Material.SHEARS ||
                type == Material.FLINT_AND_STEEL;
    }

    /**
     * 아이템에 적용할 수 있는 인챈트 목록 가져오기
     */
    public Set<Enchantment> getApplicableEnchantments(ItemStack item) {
        Set<Enchantment> applicable = new HashSet<>();

        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment.canEnchantItem(item)) {
                applicable.add(enchantment);
            }
        }

        return applicable;
    }

    /**
     * 아이템의 현재 인챈트와 적용 가능한 인챈트를 모두 가져오기
     */
    public Map<Enchantment, Integer> getAllPossibleEnchantments(ItemStack item) {
        Map<Enchantment, Integer> enchantments = new HashMap<>();

        // 현재 적용된 인챈트
        if (item.hasItemMeta()) {
            enchantments.putAll(item.getItemMeta().getEnchants());
        }

        // 적용 가능한 인챈트 (레벨 0으로 추가)
        for (Enchantment ench : getApplicableEnchantments(item)) {
            if (!enchantments.containsKey(ench)) {
                enchantments.put(ench, 0);
            }
        }

        return enchantments;
    }

    /**
     * 인챈트북 생성 (GUI용)
     */
    public ItemStack createEnchantmentBook(Enchantment enchantment, int currentLevel, int nextLevel) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();

        // 인챈트명 한글화
        String enchantName = getKoreanEnchantName(enchantment);

        meta.setDisplayName(ChatColor.AQUA + enchantName);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");

        if (currentLevel > 0) {
            lore.add(ChatColor.WHITE + "현재 레벨: " + ChatColor.GREEN + getRomanNumeral(currentLevel));
        } else {
            lore.add(ChatColor.WHITE + "현재 레벨: " + ChatColor.RED + "없음");
        }
        lore.add(ChatColor.WHITE + "강화 후: " + ChatColor.GOLD + getRomanNumeral(nextLevel));

        lore.add("");
        lore.add(ChatColor.YELLOW + "클릭하여 이 인챈트를 선택");
        lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");

        meta.setLore(lore);
        meta.addStoredEnchant(enchantment, Math.max(1, currentLevel), true);

        book.setItemMeta(meta);
        return book;
    }

    /**
     * 강화 정보 종이 생성
     */
    public ItemStack createEnhanceInfoPaper(Enchantment enchantment, int currentLevel, int nextLevel,
                                            int stoneCount, EnhanceStoneManager stoneManager) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "강화 정보");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");
        lore.add(ChatColor.AQUA + "인챈트: " + getKoreanEnchantName(enchantment));
        lore.add(ChatColor.WHITE + "현재 레벨: " + ChatColor.GREEN + getRomanNumeral(currentLevel));
        lore.add(ChatColor.WHITE + "강화 후: " + ChatColor.GOLD + getRomanNumeral(nextLevel));
        lore.add("");

        // 바닐라 최대 레벨 정보 추가
        int vanillaMaxLevel = enchantment.getMaxLevel();
        if (nextLevel > vanillaMaxLevel) {
            lore.add(ChatColor.RED + "⚠ 바닐라 최대 레벨(" + vanillaMaxLevel + ")을 초과합니다!");
            lore.add("");
        }

        // 최소 필요 강화석
        int minStones = stoneManager.getMinRequiredStones(nextLevel);
        lore.add(ChatColor.YELLOW + "최소 강화석: " + minStones + "개");
        lore.add(ChatColor.WHITE + "현재 강화석: " + stoneCount + "개");
        lore.add("");

        if (stoneCount >= minStones) {
            // 올바른 확률 계산 방식 사용
            double baseRate = stoneManager.getBaseSuccessRate(currentLevel, vanillaMaxLevel);
            double successRate = stoneManager.calculateSuccessRate(stoneCount, baseRate);
            double destroyRate = stoneManager.calculateDestroyRate(stoneCount, nextLevel);

            // 확률 검증 및 안전장치
            successRate = Math.max(0.0, Math.min(1.0, successRate));
            destroyRate = Math.max(0.0, Math.min(1.0, destroyRate));
            double failRate = Math.max(0.0, 1.0 - successRate - destroyRate);

            lore.add(ChatColor.GREEN + "✓ 성공: " + String.format("%.1f%%", successRate * 100));
            lore.add(ChatColor.YELLOW + "▼ 실패: " + String.format("%.1f%%", failRate * 100));
            if (destroyRate > 0) {
                lore.add(ChatColor.RED + "✗ 파괴: " + String.format("%.1f%%", destroyRate * 100));
            } else {
                lore.add(ChatColor.GRAY + "✗ 파괴 없음 (안전 구간)");
            }
        } else {
            lore.add(ChatColor.RED + "강화석이 부족합니다!");
            lore.add(ChatColor.RED + "" + (minStones - stoneCount) + "개 더 필요");
        }

        lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");

        meta.setLore(lore);
        paper.setItemMeta(meta);
        return paper;
    }

    /**
     * 강화 버튼 생성
     */
    public ItemStack createEnhanceButton(boolean canEnhance) {
        Material material = canEnhance ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (canEnhance) {
            meta.setDisplayName(ChatColor.GREEN + "강화 시작!");
            meta.setLore(Arrays.asList(
                    ChatColor.WHITE + "클릭하여 강화를 진행합니다.",
                    ChatColor.YELLOW + "행운을 빕니다!"
            ));
        } else {
            meta.setDisplayName(ChatColor.RED + "강화 불가");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "조건을 만족하지 않습니다.",
                    ChatColor.GRAY + "강화석을 더 넣어주세요."
            ));
        }

        button.setItemMeta(meta);
        return button;
    }

    /**
     * 실제 강화 진행
     */
    public EnhanceResult performEnhance(ItemStack item, Enchantment enchantment, int stoneCount,
                                        EnhanceStoneManager stoneManager) {
        int currentLevel = item.getEnchantmentLevel(enchantment);
        int nextLevel = currentLevel + 1;

        // 절대 최대 레벨 체크 (바닐라 최대 레벨의 2배까지만)
        int vanillaMaxLevel = enchantment.getMaxLevel();
        int absoluteMaxLevel = vanillaMaxLevel * 2;

        if (nextLevel > absoluteMaxLevel) {
            return new EnhanceResult(EnhanceResult.Type.MAX_LEVEL,
                    ChatColor.RED + "더 이상 강화할 수 없습니다! 최대 레벨: " + absoluteMaxLevel, item);
        }

        // 최소 강화석 체크
        int minStones = stoneManager.getMinRequiredStones(nextLevel);
        if (stoneCount < minStones) {
            return new EnhanceResult(EnhanceResult.Type.INSUFFICIENT_MATERIALS,
                    ChatColor.RED + "강화석이 부족합니다! (" + stoneCount + "/" + minStones + ")", item);
        }

        // 올바른 확률 계산 방식 사용
        double baseRate = stoneManager.getBaseSuccessRate(currentLevel, vanillaMaxLevel);
        double successRate = stoneManager.calculateSuccessRate(stoneCount, baseRate);
        double destroyRate = stoneManager.calculateDestroyRate(stoneCount, nextLevel);

        // 확률 검증 및 안전장치
        successRate = Math.max(0.0, Math.min(1.0, successRate));
        destroyRate = Math.max(0.0, Math.min(1.0, destroyRate));

        // 디버그 정보 출력 (개발 중에만 사용)
        plugin.getLogger().info(String.format("강화 시도 - %s 레벨: %d->%d, 성공률: %.1f%%, 파괴율: %.1f%%, 강화석: %d개",
                getKoreanEnchantName(enchantment), currentLevel, nextLevel, successRate * 100, destroyRate * 100, stoneCount));

        double roll = random.nextDouble();

        if (roll < destroyRate) {
            // 파괴 (먼저 체크)
            return new EnhanceResult(EnhanceResult.Type.DESTROYED,
                    ChatColor.DARK_RED + "강화 실패로 아이템이 파괴되었습니다!", null);

        } else if (roll < destroyRate + successRate) {
            // 성공
            ItemStack result = item.clone();
            result.addUnsafeEnchantment(enchantment, nextLevel);
            return new EnhanceResult(EnhanceResult.Type.SUCCESS,
                    ChatColor.GREEN + "강화 성공! " + getKoreanEnchantName(enchantment) + " " +
                            getRomanNumeral(nextLevel) + " 획득!", result);

        } else {
            // 실패 (아이템 유지)
            return new EnhanceResult(EnhanceResult.Type.FAILED,
                    ChatColor.YELLOW + "강화에 실패했습니다. 아이템은 유지됩니다.", item);
        }
    }

    /**
     * 인챈트명 한글화
     */
    private String getKoreanEnchantName(Enchantment enchantment) {
        switch (enchantment.getKey().getKey()) {
            case "sharpness": return "날카로움";
            case "efficiency": return "효율성";
            case "unbreaking": return "내구성";
            case "fortune": return "행운";
            case "silk_touch": return "섬세한 손길";
            case "power": return "힘";
            case "protection": return "보호";
            case "fire_protection": return "화염 보호";
            case "projectile_protection": return "투사체 보호";
            case "blast_protection": return "폭발 보호";
            case "feather_falling": return "가벼운 착지";
            case "respiration": return "호흡";
            case "aqua_affinity": return "물의 친화력";
            case "thorns": return "가시";
            case "depth_strider": return "물갈퀴";
            case "frost_walker": return "차가운 발걸음";
            case "soul_speed": return "영혼 가속";
            default: return enchantment.getKey().getKey();
        }
    }

    /**
     * 숫자를 로마 숫자로 변환
     */
    private String getRomanNumeral(int number) {
        if (number <= 0) return "0";
        if (number > 20) return String.valueOf(number); // 20 이상은 아라비아 숫자로

        String[] romanNumerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};

        return number < romanNumerals.length ? romanNumerals[number] : String.valueOf(number);
    }

    /**
     * 강화 결과 클래스
     */
    public static class EnhanceResult {
        public enum Type {
            SUCCESS, FAILED, DESTROYED, MAX_LEVEL, INSUFFICIENT_MATERIALS
        }

        private final Type type;
        private final String message;
        private final ItemStack resultItem;

        public EnhanceResult(Type type, String message, ItemStack resultItem) {
            this.type = type;
            this.message = message;
            this.resultItem = resultItem;
        }

        public Type getType() { return type; }
        public String getMessage() { return message; }
        public ItemStack getResultItem() { return resultItem; }
        public boolean isSuccess() { return type == Type.SUCCESS; }
    }
}