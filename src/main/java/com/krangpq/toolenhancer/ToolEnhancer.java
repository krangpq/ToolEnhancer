package com.krangpq.toolenhancer;

import com.krangpq.toolenhancer.commands.EnhanceCommand;
import com.krangpq.toolenhancer.gui.EnhanceGUI;
import com.krangpq.toolenhancer.managers.EnhanceManager;
import com.krangpq.toolenhancer.managers.EnhanceStoneManager;
import org.bukkit.Bukkit;  // 이 줄 추가
import org.bukkit.plugin.java.JavaPlugin;

public final class ToolEnhancer extends JavaPlugin {

    private EnhanceManager enhanceManager;
    private EnhanceStoneManager enhanceStoneManager;
    private EnhanceGUI enhanceGUI;

    @Override
    public void onEnable() {
        getLogger().info("ToolEnhancer 플러그인이 활성화되었습니다!");

        // 매니저 초기화
        this.enhanceStoneManager = new EnhanceStoneManager(this);
        this.enhanceManager = new EnhanceManager(this);
        this.enhanceGUI = new EnhanceGUI(this, enhanceManager, enhanceStoneManager);

        // 명령어 등록
        getCommand("enhance").setExecutor(new EnhanceCommand(this, enhanceGUI));

        // 이벤트 리스너 등록
        getServer().getPluginManager().registerEvents(enhanceGUI, this);

        // 설정 파일 생성
        saveDefaultConfig();

        // 강화석 레시피 등록
        enhanceStoneManager.registerRecipes();

        // 주기적 세션 정리 (5분마다)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            enhanceGUI.cleanupExpiredSessions();
        }, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        getLogger().info("ToolEnhancer 플러그인이 비활성화되었습니다!");
    }

    public EnhanceManager getEnhanceManager() {
        return enhanceManager;
    }

    public EnhanceStoneManager getEnhanceStoneManager() {
        return enhanceStoneManager;
    }

    public EnhanceGUI getEnhanceGUI() {
        return enhanceGUI;
    }
}