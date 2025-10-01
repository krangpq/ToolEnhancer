package com.krangpq.toolenhancer.commands;

import com.krangpq.toolenhancer.ToolEnhancer;
import com.krangpq.toolenhancer.gui.EnhanceGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EnhanceCommand implements CommandExecutor, TabCompleter {

    private final ToolEnhancer plugin;
    private final EnhanceGUI enhanceGUI;

    public EnhanceCommand(ToolEnhancer plugin, EnhanceGUI enhanceGUI) {
        this.plugin = plugin;
        this.enhanceGUI = enhanceGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 서브 명령어 처리
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "give":
                    return handleGiveCommand(sender, args);

                case "help":
                    sendHelpMessage(sender);
                    return true;

                default:
                    sender.sendMessage(ChatColor.RED + "알 수 없는 명령어입니다. /enhance help를 확인하세요.");
                    return true;
            }
        }

        // 기본 명령어 - 플레이어만 사용 가능
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

    /**
     * /enhance give 명령어 처리
     * 사용법: /enhance give <개수> [플레이어]
     */
    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        // 권한 확인
        if (!sender.hasPermission("toolenhancer.admin")) {
            sender.sendMessage(ChatColor.RED + "이 명령어를 사용할 권한이 없습니다!");
            return true;
        }

        // 인자 개수 확인
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "사용법: /enhance give <개수> [플레이어]");
            sender.sendMessage(ChatColor.YELLOW + "예시: /enhance give 10");
            sender.sendMessage(ChatColor.YELLOW + "예시: /enhance give 10 Steve");
            return true;
        }

        // 개수 파싱
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            if (amount <= 0) {
                sender.sendMessage(ChatColor.RED + "개수는 1 이상이어야 합니다!");
                return true;
            }
            if (amount > 64) {
                sender.sendMessage(ChatColor.RED + "한 번에 최대 64개까지만 지급할 수 있습니다!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "올바른 숫자를 입력해주세요!");
            return true;
        }

        // 대상 플레이어 결정
        Player target;

        if (args.length >= 3) {
            // 플레이어 이름이 지정된 경우
            String targetName = args[2];
            target = Bukkit.getPlayer(targetName);

            if (target == null) {
                sender.sendMessage(ChatColor.RED + "플레이어 '" + targetName + "'를 찾을 수 없습니다!");
                return true;
            }
        } else {
            // 플레이어 이름이 없으면 자신에게 지급 (플레이어만 가능)
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "콘솔에서는 플레이어 이름을 지정해야 합니다!");
                sender.sendMessage(ChatColor.YELLOW + "사용법: /enhance give <개수> <플레이어>");
                return true;
            }
            target = (Player) sender;
        }

        // 강화석 생성 및 지급
        ItemStack stones = plugin.getEnhanceStoneManager().createEnhanceStone(amount);
        target.getInventory().addItem(stones);

        // 메시지 전송
        if (sender.equals(target)) {
            // 자신에게 지급
            sender.sendMessage(ChatColor.GREEN + "강화석 " + amount + "개를 지급받았습니다!");
        } else {
            // 다른 플레이어에게 지급
            target.sendMessage(ChatColor.GREEN + "강화석 " + amount + "개를 지급받았습니다!");
            sender.sendMessage(ChatColor.GREEN + target.getName() + "님에게 강화석 " + amount + "개를 지급했습니다!");
        }

        // 로그 기록
        plugin.getLogger().info(sender.getName() + "이(가) " + target.getName() + "에게 강화석 " + amount + "개를 지급했습니다.");

        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "━━━━━ ToolEnhancer 도움말 ━━━━━");
        sender.sendMessage(ChatColor.YELLOW + "/enhance" + ChatColor.WHITE + " - 손에 든 아이템 강화");

        if (sender.hasPermission("toolenhancer.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/enhance give <개수>" + ChatColor.WHITE + " - 자신에게 강화석 지급");
            sender.sendMessage(ChatColor.YELLOW + "/enhance give <개수> <플레이어>" + ChatColor.WHITE + " - 다른 플레이어에게 강화석 지급");
        }

        sender.sendMessage(ChatColor.YELLOW + "/enhance help" + ChatColor.WHITE + " - 도움말 보기");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "강화 시스템 설명:");
        sender.sendMessage(ChatColor.WHITE + "1. 강화할 도구를 손에 들고 /enhance 실행");
        sender.sendMessage(ChatColor.WHITE + "2. 강화할 인챈트 선택");
        sender.sendMessage(ChatColor.WHITE + "3. 강화석을 넣고 강화 버튼 클릭");
        sender.sendMessage(ChatColor.WHITE + "4. 강화석이 많을수록 성공률 증가!");
        sender.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 첫 번째 인자: 서브 명령어
            List<String> subCommands = Arrays.asList("give", "help");

            String input = args[0].toLowerCase();
            for (String subCmd : subCommands) {
                if (subCmd.startsWith(input)) {
                    // 권한 확인
                    if (subCmd.equals("give") && !sender.hasPermission("toolenhancer.admin")) {
                        continue;
                    }
                    completions.add(subCmd);
                }
            }

        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // /enhance give [개수]
            if (sender.hasPermission("toolenhancer.admin")) {
                completions.addAll(Arrays.asList("1", "5", "10", "32", "64"));
            }

        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // /enhance give <개수> [플레이어]
            if (sender.hasPermission("toolenhancer.admin")) {
                String input = args[2].toLowerCase();
                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}