package com.krangpq.toolenhancer;

import com.krangpq.toolenhancer.api.ToolEnhancerAPI;
import com.krangpq.toolenhancer.commands.EnhanceCommand;
import com.krangpq.toolenhancer.gui.EnhanceGUI;
import com.krangpq.toolenhancer.managers.EnhanceManager;
import com.krangpq.toolenhancer.managers.EnhanceStoneManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ToolEnhancer extends JavaPlugin {

    private static ToolEnhancer instance;

    private EnhanceManager enhanceManager;
    private EnhanceStoneManager enhanceStoneManager;
    private EnhanceGUI enhanceGUI;

    @Override
    public void onEnable() {
        instance = this;

        try {
            getLogger().info("=================================");
            getLogger().info("ToolEnhancer 초기화 시작");
            getLogger().info("버전: " + getDescription().getVersion());
            getLogger().info("=================================");

            // 1. API 초기화
            getLogger().info("[1/6] API 초기화 중...");
            ToolEnhancerAPI.setPlugin(this);
            getLogger().info("[1/6] API 초기화 완료");

            // 2. 설정 파일
            getLogger().info("[2/6] 설정 파일 로드 중...");
            saveDefaultConfig();
            validateConfig();
            getLogger().info("[2/6] 설정 파일 로드 완료");

            // 3. 매니저 초기화
            getLogger().info("[3/6] 매니저 초기화 중...");
            this.enhanceStoneManager = new EnhanceStoneManager(this);
            this.enhanceManager = new EnhanceManager(this);
            getLogger().info("[3/6] 매니저 초기화 완료");

            // 4. GUI 초기화
            getLogger().info("[4/6] GUI 초기화 중...");
            this.enhanceGUI = new EnhanceGUI(this, enhanceManager, enhanceStoneManager);
            getServer().getPluginManager().registerEvents(enhanceGUI, this);
            getLogger().info("[4/6] GUI 초기화 완료");

            // 5. 명령어 및 레시피 등록
            getLogger().info("[5/6] 명령어 및 레시피 등록 중...");
            EnhanceCommand enhanceCommand = new EnhanceCommand(this, enhanceGUI);
            getCommand("enhance").setExecutor(enhanceCommand);
            getCommand("enhance").setTabCompleter(enhanceCommand);
            enhanceStoneManager.registerRecipes();
            getLogger().info("[5/6] 명령어 및 레시피 등록 완료");

            // 6. 주기적 세션 정리
            getLogger().info("[6/6] 세션 관리 스케줄러 시작 중...");
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                try {
                    enhanceGUI.cleanupExpiredSessions();
                } catch (Exception e) {
                    getLogger().severe("세션 정리 중 오류 발생: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 6000L, 6000L); // 5분마다 실행
            getLogger().info("[6/6] 세션 관리 스케줄러 시작 완료");

            getLogger().info("=================================");
            getLogger().info("ToolEnhancer 활성화 성공!");
            getLogger().info("=================================");

        } catch (Exception e) {
            getLogger().severe("=================================");
            getLogger().severe("플러그인 초기화 실패!");
            getLogger().severe("=================================");
            getLogger().severe("에러: " + e.getMessage());
            e.printStackTrace();
            getLogger().severe("=================================");
            getLogger().severe("플러그인을 비활성화합니다.");
            getLogger().severe("=================================");

            // 플러그인 비활성화
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("=================================");
        getLogger().info("ToolEnhancer 비활성화 중...");

        // 세션 정리
        if (enhanceGUI != null) {
            try {
                enhanceGUI.cleanupExpiredSessions();
                getLogger().info("모든 세션 정리 완료");
            } catch (Exception e) {
                getLogger().warning("세션 정리 중 오류: " + e.getMessage());
            }
        }

        getLogger().info("ToolEnhancer 비활성화 완료!");
        getLogger().info("=================================");
    }

    /**
     * config.yml 검증 및 경고
     */
    private void validateConfig() {
        getLogger().info("설정 파일 검증 중...");

        try {
            // 필수 값 체크
            if (!getConfig().contains("success_rates.beyond_vanilla.allow_beyond_max")) {
                getLogger().warning("경고: 'allow_beyond_max' 설정이 없습니다! 기본값(true) 사용");
            }

            // severity_scale 범위 체크
            double severityScale = getConfig().getDouble("success_rates.beyond_vanilla.severity_scale", 1.0);
            if (severityScale < 0.0 || severityScale > 5.0) {
                getLogger().severe("오류: severity_scale 값이 범위를 벗어났습니다! (0.0-5.0)");
                getLogger().severe("현재 값: " + severityScale);
                getLogger().severe("기본값 1.0을 사용합니다.");
                getConfig().set("success_rates.beyond_vanilla.severity_scale", 1.0);
            }

            // global_minimum_rate 범위 체크
            double globalMinRate = getConfig().getDouble("success_rates.beyond_vanilla.global_minimum_rate", 0.01);
            if (globalMinRate < 0.0 || globalMinRate > 1.0) {
                getLogger().severe("오류: global_minimum_rate 값이 범위를 벗어났습니다! (0.0-1.0)");
                getLogger().severe("현재 값: " + globalMinRate);
                getLogger().severe("기본값 0.01을 사용합니다.");
                getConfig().set("success_rates.beyond_vanilla.global_minimum_rate", 0.01);
            }

            // max_multiplier 범위 체크
            double maxMultiplier = getConfig().getDouble("success_rates.beyond_vanilla.max_level_limit.max_multiplier", 2.0);
            if (maxMultiplier < 1.0 || maxMultiplier > 10.0) {
                getLogger().severe("오류: max_multiplier 값이 범위를 벗어났습니다! (1.0-10.0)");
                getLogger().severe("현재 값: " + maxMultiplier);
                getLogger().severe("기본값 2.0을 사용합니다.");
                getConfig().set("success_rates.beyond_vanilla.max_level_limit.max_multiplier", 2.0);
            }

            // absolute_max_level 범위 체크
            int absoluteMaxLevel = getConfig().getInt("success_rates.beyond_vanilla.max_level_limit.absolute_max_level", 30);
            if (absoluteMaxLevel < 1 || absoluteMaxLevel > 100) {
                getLogger().severe("오류: absolute_max_level 값이 범위를 벗어났습니다! (1-100)");
                getLogger().severe("현재 값: " + absoluteMaxLevel);
                getLogger().severe("기본값 30을 사용합니다.");
                getConfig().set("success_rates.beyond_vanilla.max_level_limit.absolute_max_level", 30);
            }

            // debug_mode 추가 (없으면 생성)
            if (!getConfig().contains("debug_mode")) {
                getConfig().set("debug_mode", false);
                saveConfig();
                getLogger().info("debug_mode 설정 추가됨 (기본값: false)");
            }

            getLogger().info("설정 파일 검증 완료!");

        } catch (Exception e) {
            getLogger().severe("설정 파일 검증 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 디버그 로그 (debug_mode가 true일 때만 출력)
     */
    public void debug(String message) {
        if (getConfig().getBoolean("debug_mode", false)) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    // Getters
    public static ToolEnhancer getInstance() {
        return instance;
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