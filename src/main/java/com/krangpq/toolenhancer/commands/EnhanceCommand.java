package com.krangpq.toolenhancer.commands;

import com.krangpq.toolenhancer.ToolEnhancer;
import com.krangpq.toolenhancer.gui.EnhanceGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class EnhanceCommand implements CommandExecutor {

    private final ToolEnhancer plugin;
    private final EnhanceGUI enhanceGUI;

    public EnhanceCommand(ToolEnhancer plugin, EnhanceGUI enhanceGUI) {
        this.plugin = plugin;
        this.enhanceGUI = enhanceGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "이 명령어는 플레이어만 사용할 수 있습니다!");
            return true;
        }

        Player player = (Player) sender;

        // 권한 확인
        if (!player.hasPermission("toolenhancer.use")) {
            player.sendMessage(ChatColor.RED + "강화 시스템을 사용할 권한이 없습니다!");
            return true;
        }

        // 서브 명령어 처리
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "give":
                    if (args.length >= 2 && player.hasPermission("toolenhancer.admin")) {
                        try {
                            int amount = Integer.parseInt(args[1]);
                            ItemStack stones = plugin.getEnhanceStoneManager().createEnhanceStone(amount);
                            player.getInventory().addItem(stones);
                            player.sendMessage(ChatColor.GREEN + "강화석 " + amount + "개를 지급했습니다!");
                        } catch (NumberFormatException e) {
                            player.sendMessage(ChatColor.RED + "올바른 숫자를 입력해주세요!");
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "사용법: /enhance give <개수>");
                    }
                    return true;

                case "help":
                    sendHelpMessage(player);
                    return true;
            }
        }

        // 메인 핸드 아이템 확인
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "강화할 도구를 손에 들고 명령어를 사용해주세요!");
            return true;
        }

        // 강화 가능한 아이템인지 확인
        if (!plugin.getEnhanceManager().canEnhance(itemInHand)) {
            player.sendMessage(ChatColor.RED + "이 아이템은 강화할 수 없습니다!");
            player.sendMessage(ChatColor.GRAY + "강화 가능한 아이템: 도구, 무기, 방어구");
            return true;
        }

        // GUI 열기
        enhanceGUI.openEnhanceSelectGUI(player, itemInHand);
        player.sendMessage(ChatColor.GREEN + "강화할 인챈트를 선택해주세요!");

        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "━━━━━ ToolEnhancer 도움말 ━━━━━");
        player.sendMessage(ChatColor.YELLOW + "/enhance" + ChatColor.WHITE + " - 손에 든 아이템 강화");
        if (player.hasPermission("toolenhancer.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/enhance give <개수>" + ChatColor.WHITE + " - 강화석 지급");
        }
        player.sendMessage(ChatColor.YELLOW + "/enhance help" + ChatColor.WHITE + " - 도움말 보기");
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "강화 시스템 설명:");
        player.sendMessage(ChatColor.WHITE + "1. 강화할 도구를 손에 들고 /enhance 실행");
        player.sendMessage(ChatColor.WHITE + "2. 강화할 인챈트 선택");
        player.sendMessage(ChatColor.WHITE + "3. 강화석을 넣고 강화 버튼 클릭");
        player.sendMessage(ChatColor.WHITE + "4. 강화석이 많을수록 성공률 증가!");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}