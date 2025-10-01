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
        if (plugin == null) {
            throw new IllegalArgumentException("plugin은 null일 수 없습니다!");
        }

        this.plugin = plugin;
        this.random = new Random();

        plugin.getLogger().info("EnhanceManager 초기화 완료");
    }

    /**
     * 아이템이 강화 가능한지 확인
     *
     * @param item 확인할 아이템
     * @return 강화 가능하면 true
     */
    public boolean canEnhance(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

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
     *
     * @param item 대상 아이템
     * @return 적용 가능한 인챈트 Set
     */
    public Set<Enchantment> getApplicableEnchantments(ItemStack item) {
        if (item == null) {
            plugin.getLogger().warning("getApplicableEnchantments() 호출 오류: item이 null입니다!");
            return new HashSet<>();
        }

        Set<Enchantment> applicable = new HashSet<>();

        try {
            for (Enchantment enchantment : Enchantment.values()) {
                if (enchantment.canEnchantItem(item)) {
                    applicable.add(enchantment);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("적용 가능한 인챈트 조회 중 오류: " + e.getMessage());
            e.printStackTrace();
        }

        return applicable;
    }

    /**
     * 아이템의 현재 인챈트와 적용 가능한 인챈트를 모두 가져오기
     *
     * @param item 대상 아이템
     * @return 인챈트와 레벨 Map
     */
    public Map<Enchantment, Integer> getAllPossibleEnchantments(ItemStack item) {
        if (item == null) {
            plugin.getLogger().warning("getAllPossibleEnchantments() 호출 오류: item이 null입니다!");
            return new HashMap<>();
        }

        Map<Enchantment, Integer> enchantments = new HashMap<>();

        try {
            if (item.hasItemMeta()) {
                enchantments.putAll(item.getItemMeta().getEnchants());
            }

            for (Enchantment ench : getApplicableEnchantments(item)) {
                if (!enchantments.containsKey(ench)) {
                    enchantments.put(ench, 0);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("인챈트 목록 조회 중 오류: " + e.getMessage());
            e.printStackTrace();
        }

        return enchantments;
    }

    /**
     * 인챈트북 생성 (GUI용)
     *
     * @param enchantment 인챈트
     * @param currentLevel 현재 레벨
     * @param nextLevel 다음 레벨
     * @return 인챈트북 ItemStack
     */
    public ItemStack createEnchantmentBook(Enchantment enchantment, int currentLevel, int nextLevel) {
        if (enchantment == null) {
            plugin.getLogger().severe("createEnchantmentBook() 호출 오류: enchantment가 null입니다!");
            return new ItemStack(Material.ENCHANTED_BOOK);
        }

        try {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();

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

        } catch (Exception e) {
            plugin.getLogger().severe("인챈트북 생성 중 오류: " + e.getMessage());
            e.printStackTrace();
            return new ItemStack(Material.ENCHANTED_BOOK);
        }
    }

    /**
     * 강화 정보 종이 생성
     */
    public ItemStack createEnhanceInfoPaper(Enchantment enchantment, int currentLevel, int nextLevel,
                                            int stoneCount, EnhanceStoneManager stoneManager) {
        if (enchantment == null || stoneManager == null) {
            plugin.getLogger().severe("createEnhanceInfoPaper() 호출 오류: 파라미터가 null입니다!");
            return new ItemStack(Material.PAPER);
        }

        try {
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();

            meta.setDisplayName(ChatColor.GOLD + "강화 정보");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");
            lore.add(ChatColor.AQUA + "인챈트: " + getKoreanEnchantName(enchantment));
            lore.add(ChatColor.WHITE + "현재 레벨: " + ChatColor.GREEN + getRomanNumeral(currentLevel));
            lore.add(ChatColor.WHITE + "강화 후: " + ChatColor.GOLD + getRomanNumeral(nextLevel));
            lore.add("");

            int vanillaMaxLevel = enchantment.getMaxLevel();
            if (nextLevel > vanillaMaxLevel) {
                lore.add(ChatColor.RED + "⚠ 바닐라 최대 레벨(" + vanillaMaxLevel + ")을 초과합니다!");
                lore.add("");
            }

            int minStones = stoneManager.getMinRequiredStones(nextLevel);
            lore.add(ChatColor.YELLOW + "최소 강화석: " + minStones + "개");
            lore.add(ChatColor.WHITE + "현재 강화석: " + stoneCount + "개");
            lore.add("");

            if (stoneCount >= minStones) {
                double baseRate = stoneManager.getBaseSuccessRate(currentLevel, vanillaMaxLevel);
                double successRate = stoneManager.calculateSuccessRate(stoneCount, baseRate);
                double destroyRate = stoneManager.calculateDestroyRate(stoneCount, nextLevel);

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

        } catch (Exception e) {
            plugin.getLogger().severe("강화 정보 생성 중 오류: " + e.getMessage());
            e.printStackTrace();
            return new ItemStack(Material.PAPER);
        }
    }

    /**
     * 강화 버튼 생성
     */
    public ItemStack createEnhanceButton(boolean canEnhance) {
        try {
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

        } catch (Exception e) {
            plugin.getLogger().severe("강화 버튼 생성 중 오류: " + e.getMessage());
            e.printStackTrace();
            return new ItemStack(Material.LIME_CONCRETE);
        }
    }

    /**
     * 실제 강화 진행
     *
     * @param item 강화할 아이템
     * @param enchantment 강화할 인챈트
     * @param stoneCount 사용한 강화석 개수
     * @param stoneManager 강화석 매니저
     * @return 강화 결과
     */
    public EnhanceResult performEnhance(ItemStack item, Enchantment enchantment, int stoneCount,
                                        EnhanceStoneManager stoneManager) {
        // 파라미터 검증
        if (item == null) {
            plugin.getLogger().severe("performEnhance() 호출 오류: item이 null입니다!");
            return new EnhanceResult(EnhanceResult.Type.FAILED,
                    ChatColor.RED + "내부 오류가 발생했습니다.", null);
        }

        if (enchantment == null) {
            plugin.getLogger().severe("performEnhance() 호출 오류: enchantment가 null입니다!");
            return new EnhanceResult(EnhanceResult.Type.FAILED,
                    ChatColor.RED + "내부 오류가 발생했습니다.", null);
        }

        if (stoneManager == null) {
            plugin.getLogger().severe("performEnhance() 호출 오류: stoneManager가 null입니다!");
            return new EnhanceResult(EnhanceResult.Type.FAILED,
                    ChatColor.RED + "내부 오류가 발생했습니다.", null);
        }

        plugin.getLogger().info("강화 시작: " + getKoreanEnchantName(enchantment));

        try {
            int currentLevel = item.getEnchantmentLevel(enchantment);
            int nextLevel = currentLevel + 1;

            int vanillaMaxLevel = enchantment.getMaxLevel();
            int absoluteMaxLevel = stoneManager.getAbsoluteMaxLevel(vanillaMaxLevel);

            if (nextLevel > absoluteMaxLevel) {
                plugin.getLogger().info("강화 실패: 최대 레벨 도달 (" + nextLevel + " > " + absoluteMaxLevel + ")");
                return new EnhanceResult(EnhanceResult.Type.MAX_LEVEL,
                        ChatColor.RED + "더 이상 강화할 수 없습니다! 최대 레벨: " + absoluteMaxLevel, item);
            }

            int minStones = stoneManager.getMinRequiredStones(nextLevel);
            if (stoneCount < minStones) {
                plugin.getLogger().info("강화 실패: 강화석 부족 (" + stoneCount + "/" + minStones + ")");
                return new EnhanceResult(EnhanceResult.Type.INSUFFICIENT_MATERIALS,
                        ChatColor.RED + "강화석이 부족합니다! (" + stoneCount + "/" + minStones + ")", item);
            }

            double baseRate = stoneManager.getBaseSuccessRate(currentLevel, vanillaMaxLevel);
            double successRate = stoneManager.calculateSuccessRate(stoneCount, baseRate);
            double destroyRate = stoneManager.calculateDestroyRate(stoneCount, nextLevel);

            successRate = Math.max(0.0, Math.min(1.0, successRate));
            destroyRate = Math.max(0.0, Math.min(1.0, destroyRate));

            plugin.getLogger().info(String.format("강화 시도 - %s 레벨: %d->%d, 성공률: %.1f%%, 파괴율: %.1f%%, 강화석: %d개",
                    getKoreanEnchantName(enchantment), currentLevel, nextLevel,
                    successRate * 100, destroyRate * 100, stoneCount));

            double roll = random.nextDouble();

            if (roll < destroyRate) {
                plugin.getLogger().info("강화 결과: 파괴 (roll: " + String.format("%.4f", roll) + " < " + String.format("%.4f", destroyRate) + ")");
                return new EnhanceResult(EnhanceResult.Type.DESTROYED,
                        ChatColor.DARK_RED + "강화 실패로 아이템이 파괴되었습니다!", null);

            } else if (roll < destroyRate + successRate) {
                plugin.getLogger().info("강화 결과: 성공 (roll: " + String.format("%.4f", roll) + ")");
                ItemStack result = item.clone();
                result.addUnsafeEnchantment(enchantment, nextLevel);
                return new EnhanceResult(EnhanceResult.Type.SUCCESS,
                        ChatColor.GREEN + "강화 성공! " + getKoreanEnchantName(enchantment) + " " +
                                getRomanNumeral(nextLevel) + " 획득!", result);

            } else {
                plugin.getLogger().info("강화 결과: 실패 (roll: " + String.format("%.4f", roll) + ")");
                return new EnhanceResult(EnhanceResult.Type.FAILED,
                        ChatColor.YELLOW + "강화에 실패했습니다. 아이템은 유지됩니다.", item);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("=========================================");
            plugin.getLogger().severe("강화 진행 중 예외 발생!");
            plugin.getLogger().severe("인챈트: " + getKoreanEnchantName(enchantment));
            plugin.getLogger().severe("강화석: " + stoneCount + "개");
            plugin.getLogger().severe("에러: " + e.getMessage());
            plugin.getLogger().severe("=========================================");
            e.printStackTrace();

            return new EnhanceResult(EnhanceResult.Type.FAILED,
                    ChatColor.RED + "강화 처리 중 오류가 발생했습니다.", item);
        }
    }

    /**
     * 인챈트명 한글화
     */
    private String getKoreanEnchantName(Enchantment enchantment) {
        if (enchantment == null) return "알 수 없음";

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
        if (number > 20) return String.valueOf(number);

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