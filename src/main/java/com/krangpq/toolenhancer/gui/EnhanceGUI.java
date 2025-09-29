package com.krangpq.toolenhancer.gui;

import com.krangpq.toolenhancer.ToolEnhancer;
import com.krangpq.toolenhancer.managers.EnhanceManager;
import com.krangpq.toolenhancer.managers.EnhanceStoneManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnhanceGUI implements Listener {

    private final ToolEnhancer plugin;
    private final EnhanceManager enhanceManager;
    private final EnhanceStoneManager stoneManager;

    // GUI 세션 관리 - ConcurrentHashMap으로 동시성 문제 해결
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();

    // 강화 진행 중인 플레이어 추적 (중복 실행 방지)
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();

    public EnhanceGUI(ToolEnhancer plugin, EnhanceManager enhanceManager, EnhanceStoneManager stoneManager) {
        this.plugin = plugin;
        this.enhanceManager = enhanceManager;
        this.stoneManager = stoneManager;
    }

    /**
     * 1단계: 인챈트 선택 GUI 열기
     */
    public void openEnhanceSelectGUI(Player player, ItemStack targetItem) {
        Map<Enchantment, Integer> enchantments = enhanceManager.getAllPossibleEnchantments(targetItem);

        if (enchantments.isEmpty()) {
            player.sendMessage(ChatColor.RED + "이 아이템에는 적용할 수 있는 인챈트가 없습니다!");
            return;
        }

        // 현재 메인핸드 슬롯 번호 가져오기
        int currentSlot = player.getInventory().getHeldItemSlot();

        // 기존 세션 제거하고 새 세션 생성
        UUID playerId = player.getUniqueId();
        sessions.remove(playerId);
        processingPlayers.remove(playerId);

        // 슬롯 번호도 함께 저장하여 새 세션 생성
        GuiSession session = new GuiSession(playerId, targetItem.clone(), currentSlot);
        sessions.put(playerId, session);

        // 인벤토리 크기 계산 (9의 배수)
        int size = Math.min(54, ((enchantments.size() - 1) / 9 + 1) * 9);
        Inventory gui = Bukkit.createInventory(null, size, ChatColor.DARK_BLUE + "강화할 인챈트 선택");

        // 인챈트북들 배치
        int slot = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            Enchantment ench = entry.getKey();
            int currentLevel = entry.getValue();
            int nextLevel = currentLevel + 1;
            ItemStack enchantBook = enhanceManager.createEnchantmentBook(ench, currentLevel, nextLevel);
            gui.setItem(slot, enchantBook);
            slot++;
        }

        // 빈 공간을 유리판으로 채우기
        ItemStack glass = createGlassPane(ChatColor.GRAY + " ");
        for (int i = slot; i < size; i++) {
            gui.setItem(i, glass);
        }

        player.openInventory(gui);
    }

    /**
     * 2단계: 강화 진행 GUI 열기
     */
    public void openEnhanceProcessGUI(Player player, Enchantment selectedEnchantment) {
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(ChatColor.RED + "세션이 만료되었습니다. 다시 시도해주세요!");
            return;
        }

        session.selectedEnchantment = selectedEnchantment;

        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "도구 강화");

        // GUI 레이아웃 설정
        setupEnhanceProcessLayout(gui, session);

        player.openInventory(gui);
    }

    /**
     * 강화 진행 GUI 레이아웃 설정
     */
    private void setupEnhanceProcessLayout(Inventory gui, GuiSession session) {
        // 배경 유리판
        ItemStack backgroundGlass = createGlassPane(ChatColor.GRAY + " ");
        ItemStack borderGlass = createColoredGlass(Material.BLACK_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ");

        // 테두리 설정
        for (int i = 0; i < 9; i++) gui.setItem(i, borderGlass); // 상단
        for (int i = 45; i < 54; i++) gui.setItem(i, borderGlass); // 하단
        for (int i = 9; i < 45; i += 9) gui.setItem(i, borderGlass); // 좌측
        for (int i = 17; i < 45; i += 9) gui.setItem(i, borderGlass); // 우측

        // 중앙 영역 배경
        for (int row = 1; row < 5; row++) {
            for (int col = 1; col < 8; col++) {
                gui.setItem(row * 9 + col, backgroundGlass);
            }
        }

        // 도구 표시 슬롯 (좌상단)
        gui.setItem(10, session.targetItem.clone()); // 복제해서 넣기
        gui.setItem(19, createInfoItem(Material.ARROW, ChatColor.YELLOW + "강화 대상",
                Arrays.asList(ChatColor.GRAY + "위에 있는 도구가", ChatColor.GRAY + "강화됩니다.")));

        // 강화석 투입 슬롯들 (중앙)
        int[] stoneSlots = {12, 13, 14, 21, 22, 23, 30, 31, 32};
        for (int slot : stoneSlots) {
            gui.setItem(slot, createStoneSlot());
        }

        // 강화석 투입 안내
        gui.setItem(39, createInfoItem(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "강화석 투입 구역",
                Arrays.asList(
                        ChatColor.GRAY + "강화석을 위 슬롯들에",
                        ChatColor.GRAY + "넣어주세요.",
                        "",
                        ChatColor.YELLOW + "더 많이 넣을수록",
                        ChatColor.YELLOW + "성공 확률이 높아집니다!"
                )));

        // 강화 정보 업데이트
        updateEnhanceInfo(gui, session);

        // 강화 버튼
        updateEnhanceButton(gui, session);

        // 닫기 버튼
        gui.setItem(53, createInfoItem(Material.BARRIER, ChatColor.RED + "나가기",
                Arrays.asList(ChatColor.GRAY + "강화를 취소하고 나갑니다.")));
    }

    /**
     * 강화 정보 업데이트
     */
    private void updateEnhanceInfo(Inventory gui, GuiSession session) {
        if (session.selectedEnchantment == null) return;

        int currentLevel = session.targetItem.getEnchantmentLevel(session.selectedEnchantment);
        int nextLevel = currentLevel + 1;
        int stoneCount = countEnhanceStones(gui);

        ItemStack infoPaper = enhanceManager.createEnhanceInfoPaper(
                session.selectedEnchantment, currentLevel, nextLevel, stoneCount, stoneManager);

        gui.setItem(16, infoPaper);
    }

    /**
     * 강화 버튼 업데이트
     */
    private void updateEnhanceButton(Inventory gui, GuiSession session) {
        if (session.selectedEnchantment == null) return;

        int stoneCount = countEnhanceStones(gui);
        int nextLevel = session.targetItem.getEnchantmentLevel(session.selectedEnchantment) + 1;
        int minRequired = stoneManager.getMinRequiredStones(nextLevel);

        boolean canEnhance = stoneCount >= minRequired && !processingPlayers.contains(session.playerId);
        ItemStack button = enhanceManager.createEnhanceButton(canEnhance);

        gui.setItem(25, button);
    }

    /**
     * 강화석 개수 카운트
     */
    private int countEnhanceStones(Inventory gui) {
        int count = 0;
        int[] stoneSlots = {12, 13, 14, 21, 22, 23, 30, 31, 32};

        for (int slot : stoneSlots) {
            ItemStack item = gui.getItem(slot);
            if (item != null && stoneManager.isEnhanceStone(item)) {
                count += item.getAmount();
            }
        }

        return count;
    }

    /**
     * 강화석 슬롯 생성
     */
    private ItemStack createStoneSlot() {
        ItemStack slot = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = slot.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + "강화석 투입 슬롯");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "여기에 강화석을 놓으세요",
                    ChatColor.YELLOW + "강화석이 많을수록 성공률 증가!"
            ));
            slot.setItemMeta(meta);
        }
        return slot;
    }

    /**
     * 이벤트 핸들러: 인벤토리 클릭
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        // 강화 관련 GUI인지 확인
        if (title.equals(ChatColor.DARK_BLUE + "강화할 인챈트 선택")) {
            handleEnchantSelectionClick(event, player);
            return;
        }

        if (title.equals(ChatColor.DARK_GREEN + "도구 강화")) {
            // 강화 GUI에서의 클릭 처리
            handleEnhanceProcessClick(event, player);
            return;
        }
    }

    /**
     * 인챈트 선택 GUI 클릭 처리
     */
    private void handleEnchantSelectionClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() != Material.ENCHANTED_BOOK) {
            return;
        }

        // 인챈트북에서 인챈트 정보 추출
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
            org.bukkit.inventory.meta.EnchantmentStorageMeta meta =
                    (org.bukkit.inventory.meta.EnchantmentStorageMeta) clickedItem.getItemMeta();

            if (!meta.getStoredEnchants().isEmpty()) {
                Enchantment selectedEnchant = meta.getStoredEnchants().keySet().iterator().next();
                player.closeInventory();

                // 강화 진행 GUI 열기
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    openEnhanceProcessGUI(player, selectedEnchant);
                }, 1L);
            }
        }
    }

    /**
     * 강화 진행 GUI 클릭 처리
     */
    private void handleEnhanceProcessClick(InventoryClickEvent event, Player player) {
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // 플레이어 인벤토리 영역 클릭 (슬롯 54번부터) - 아이템 이동 차단 로직 추가
        if (slot >= 54) {
            // 대상 아이템 이동 시도 감지
            if (clickedItem != null && isSimilarItem(clickedItem, session.targetItem)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "강화 진행 중에는 대상 아이템을 이동할 수 없습니다!");
                return;
            }

            // 커서에 있는 아이템이 대상 아이템과 같은 경우도 차단
            if (cursorItem != null && isSimilarItem(cursorItem, session.targetItem)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "강화 진행 중에는 대상 아이템을 이동할 수 없습니다!");
                return;
            }

            // 원본 슬롯에서 아이템을 빼내려는 시도 감지
            if (slot - 54 == session.originalSlot) {
                ItemStack slotItem = player.getInventory().getItem(session.originalSlot);
                if (slotItem != null && isSimilarItem(slotItem, session.targetItem)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "강화 진행 중에는 대상 아이템을 이동할 수 없습니다!");
                    return;
                }
            }

            // 플레이어 인벤토리는 자유롭게 클릭 가능 (대상 아이템 제외)
            return;
        }

        // 여기서부터는 기존 GUI 내부 클릭 처리 로직 그대로 유지

        // 강화석 투입 슬롯들
        int[] stoneSlots = {12, 13, 14, 21, 22, 23, 30, 31, 32};
        boolean isStoneSlot = Arrays.stream(stoneSlots).anyMatch(s -> s == slot);

        if (isStoneSlot) {
            // 강화석 슬롯 처리
            handleStoneSlotClick(event, player, session);
        } else if (slot == 25) {
            // 강화 버튼 클릭
            event.setCancelled(true);

            // 중복 실행 방지
            if (processingPlayers.contains(player.getUniqueId())) {
                return;
            }

            handleEnhanceButtonClick(event, player, session);
        } else if (slot == 53) {
            // 나가기 버튼
            event.setCancelled(true);
            player.closeInventory();
        } else if (slot < 54) {
            // GUI 내부의 다른 슬롯들은 클릭 금지 (강화석 슬롯 제외)
            event.setCancelled(true);
        }
    }

    /**
     * 강화석 슬롯 클릭 처리
     */
    private void handleStoneSlotClick(InventoryClickEvent event, Player player, GuiSession session) {
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // 강화석이 아닌 아이템은 배치 불가
        if (cursorItem != null && !cursorItem.getType().isAir() && !stoneManager.isEnhanceStone(cursorItem)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "강화석만 배치할 수 있습니다!");
            return;
        }

        // 기본 아이템(슬롯 표시용)이면 제거하고 강화석 배치
        if (clickedItem != null && clickedItem.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
            event.setCurrentItem(null);
        }

        // 강화 정보 업데이트 (다음 틱에) - 지연 시간 증가로 안정성 향상
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.getOpenInventory().getTitle().equals(ChatColor.DARK_GREEN + "도구 강화")) {
                updateEnhanceInfo(event.getInventory(), session);
                updateEnhanceButton(event.getInventory(), session);
            }
        }, 2L);
    }

    /**
     * 강화 버튼 클릭 처리 (중요: 버그 수정)
     */
    private void handleEnhanceButtonClick(InventoryClickEvent event, Player player, GuiSession session) {
        // 기존 validateAndRefreshSession(player) 호출 부분을 삭제하고 직접 체크
        if (session == null || !session.isValid()) {
            player.sendMessage(ChatColor.RED + "세션이 만료되었습니다. 다시 /enhance를 실행해주세요!");
            player.closeInventory();
            return;
        }

        // 중복 실행 방지
        if (!processingPlayers.add(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "강화가 진행 중입니다. 잠시만 기다려주세요.");
            return;
        }

        try {
            if (session.selectedEnchantment == null) {
                player.sendMessage(ChatColor.RED + "인챈트가 선택되지 않았습니다!");
                return;
            }

            // 원본 슬롯에 대상 아이템이 여전히 있는지 확인
            ItemStack originalItem = player.getInventory().getItem(session.originalSlot);
            if (originalItem == null || !isSimilarItem(originalItem, session.targetItem)) {
                player.sendMessage(ChatColor.RED + "강화 대상 아이템을 찾을 수 없습니다! 아이템이 이동되었거나 변경되었습니다.");
                player.closeInventory();
                return;
            }

            // 현재 GUI의 강화석 개수 확인
            int stoneCount = countEnhanceStones(event.getInventory());
            int nextLevel = session.targetItem.getEnchantmentLevel(session.selectedEnchantment) + 1;
            int minRequired = stoneManager.getMinRequiredStones(nextLevel);

            if (stoneCount < minRequired) {
                player.sendMessage(ChatColor.RED + "강화석이 부족합니다! (" + stoneCount + "/" + minRequired + ")");
                return;
            }

            // 강화석 먼저 소모
            if (!consumeEnhanceStones(event.getInventory(), stoneCount)) {
                player.sendMessage(ChatColor.RED + "강화석 처리 중 오류가 발생했습니다!");
                return;
            }

            // 원본 슬롯의 실제 아이템으로 강화 진행
            EnhanceManager.EnhanceResult result = enhanceManager.performEnhance(
                    originalItem.clone(), session.selectedEnchantment, stoneCount, stoneManager);

            // GUI 닫기
            player.closeInventory();

            // 결과 메시지 출력
            player.sendMessage(result.getMessage());

            // 결과에 따른 아이템 처리 - 원본 슬롯에 적용
            if (result.getType() == EnhanceManager.EnhanceResult.Type.SUCCESS) {
                // 성공: 원본 슬롯의 아이템을 강화된 아이템으로 교체
                player.getInventory().setItem(session.originalSlot, result.getResultItem());
                player.sendMessage(ChatColor.GOLD + "축하합니다! 강화에 성공했습니다!");

            } else if (result.getType() == EnhanceManager.EnhanceResult.Type.DESTROYED) {
                // 파괴: 원본 슬롯의 아이템 제거
                player.getInventory().setItem(session.originalSlot, new ItemStack(Material.AIR));
                player.sendMessage(ChatColor.DARK_RED + "안타깝게도 아이템이 파괴되었습니다...");

            } else if (result.getType() == EnhanceManager.EnhanceResult.Type.FAILED) {
                // 실패: 원본 아이템 유지 (변경 없음)
                player.sendMessage(ChatColor.YELLOW + "다음에 다시 도전해보세요!");
            }

        } finally {
            // 처리 완료 후 플래그 제거
            processingPlayers.remove(player.getUniqueId());
        }
    }

    /**
     * 강화석 소모 (수정된 버전)
     */
    private boolean consumeEnhanceStones(Inventory gui, int amount) {
        int[] stoneSlots = {12, 13, 14, 21, 22, 23, 30, 31, 32};
        int remaining = amount;
        List<ItemStack> toConsume = new ArrayList<>();

        // 먼저 소모할 아이템들을 수집
        for (int slot : stoneSlots) {
            if (remaining <= 0) break;

            ItemStack item = gui.getItem(slot);
            if (item != null && stoneManager.isEnhanceStone(item)) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    toConsume.add(new ItemStack(Material.AIR)); // 전체 소모
                    remaining -= itemAmount;
                } else {
                    ItemStack newItem = item.clone();
                    newItem.setAmount(itemAmount - remaining);
                    toConsume.add(newItem); // 일부 소모
                    remaining = 0;
                }
            } else {
                toConsume.add(item); // 변경 없음
            }
        }

        // 검증: 모든 강화석이 소모되었는지 확인
        if (remaining > 0) {
            return false; // 부족한 경우 소모하지 않음
        }

        // 실제로 소모 적용
        for (int i = 0; i < stoneSlots.length && i < toConsume.size(); i++) {
            ItemStack newItem = toConsume.get(i);
            if (newItem != null && newItem.getType() == Material.AIR) {
                gui.setItem(stoneSlots[i], null);
            } else {
                gui.setItem(stoneSlots[i], newItem);
            }
        }

        return true;
    }

    /**
     * 인벤토리 닫기 이벤트 처리
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();

        if (title.equals(ChatColor.DARK_GREEN + "도구 강화")) {
            // 강화 GUI 닫을 때 강화석 반환 (처리 중이 아닐 때만)
            if (!processingPlayers.contains(player.getUniqueId())) {
                returnEnhanceStones(event.getInventory(), player);
            }
        }

        // 세션 정리 (10초 후 - 시간 증가)
        //Bukkit.getScheduler().runTaskLater(plugin, () -> {
        //    sessions.remove(player.getUniqueId());
        //    processingPlayers.remove(player.getUniqueId()); // 안전장치
        //}, 200L);
    }

    /**
     * 강화석 반환
     */
    private void returnEnhanceStones(Inventory gui, Player player) {
        int[] stoneSlots = {12, 13, 14, 21, 22, 23, 30, 31, 32};

        for (int slot : stoneSlots) {
            ItemStack item = gui.getItem(slot);
            if (item != null && stoneManager.isEnhanceStone(item)) {
                // 플레이어 인벤토리에 반환
                HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(item);

                // 인벤토리가 가득 찬 경우 땅에 드롭
                if (!leftOver.isEmpty()) {
                    for (ItemStack drop : leftOver.values()) {
                        player.getWorld().dropItem(player.getLocation(), drop);
                    }
                }
            }
        }
    }

    /**
     * 유틸리티: 유리판 생성
     */
    private ItemStack createGlassPane(String name) {
        return createColoredGlass(Material.GRAY_STAINED_GLASS_PANE, name);
    }

    /**
     * 유틸리티: 색상 유리 생성
     */
    private ItemStack createColoredGlass(Material material, String name) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            glass.setItemMeta(meta);
        }
        return glass;
    }

    //유틸리티: 정보 아이템 생성
    private ItemStack createInfoItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // EnhanceGUI.java의 세션 관리 부분 수정


    //GUI 세션 클래스
    private static class GuiSession {
        final UUID playerId;
        final ItemStack targetItem;
        final int originalSlot; // 추가: 원래 아이템이 있던 슬롯 번호
        Enchantment selectedEnchantment;
        private long lastAccess;
        private static final long SESSION_TIMEOUT = 300_000; // 5분

        GuiSession(UUID playerId, ItemStack targetItem, int originalSlot) {
            this.playerId = playerId;
            this.targetItem = targetItem;
            this.originalSlot = originalSlot; // 슬롯 번호 저장
            this.lastAccess = System.currentTimeMillis();
        }

        void updateLastAccess() {
            this.lastAccess = System.currentTimeMillis();
        }

        boolean isValid() {
            return (System.currentTimeMillis() - lastAccess) < SESSION_TIMEOUT;
        }
    }

    public void cleanupExpiredSessions() {
        int removed = 0;
        Iterator<Map.Entry<UUID, GuiSession>> iterator = sessions.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, GuiSession> entry = iterator.next();
            if (!entry.getValue().isValid()) {
                iterator.remove();
                processingPlayers.remove(entry.getKey());
                removed++;
            }
        }

        if (removed > 0) {
            plugin.getLogger().info("만료된 세션 " + removed + "개를 정리했습니다.");
        }
    }

    private boolean isSimilarItem(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) {
            return false;
        }

        // 타입이 다르면 다른 아이템
        if (item1.getType() != item2.getType()) {
            return false;
        }

        // 인챈트 비교 (강화 대상 식별의 핵심)
        if (!item1.getEnchantments().equals(item2.getEnchantments())) {
            return false;
        }

        // 내구도가 있는 아이템의 경우 내구도 비교
        if (item1.getType().getMaxDurability() > 0 &&
                item1.hasItemMeta() && item2.hasItemMeta()) {

            org.bukkit.inventory.meta.Damageable meta1 =
                    (org.bukkit.inventory.meta.Damageable) item1.getItemMeta();
            org.bukkit.inventory.meta.Damageable meta2 =
                    (org.bukkit.inventory.meta.Damageable) item2.getItemMeta();

            if (meta1.getDamage() != meta2.getDamage()) {
                return false;
            }
        }

        // 커스텀 이름이 있는 경우 비교
        if (item1.hasItemMeta() && item2.hasItemMeta()) {
            ItemMeta meta1 = item1.getItemMeta();
            ItemMeta meta2 = item2.getItemMeta();

            boolean hasName1 = meta1.hasDisplayName();
            boolean hasName2 = meta2.hasDisplayName();

            if (hasName1 != hasName2) {
                return false;
            }

            if (hasName1 && !meta1.getDisplayName().equals(meta2.getDisplayName())) {
                return false;
            }
        }

        return true;
    }
}