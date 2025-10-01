# Development Notes

이 문서는 ToolEnhancer 플러그인의 개발 과정에서 발생한 주요 설계 결정, 해결한 버그, 그리고 코드 이해를 돕기 위한 개발자 노트입니다.

## 📋 목차
- [핵심 설계 의도](#핵심-설계-의도)
- [주요 버그 및 해결 방법](#주요-버그-및-해결-방법)
- [중요한 설계 결정](#중요한-설계-결정)
- [알려진 제약사항](#알려진-제약사항)
- [향후 개선 방향](#향후-개선-방향)

---

## 🎯 핵심 설계 의도

### 최우선 목표
1. **아이템 무결성 보장**: 강화 과정에서 아이템 복제/소실 절대 방지
2. **플레이어 경험**: 직관적인 GUI와 명확한 피드백
3. **서버 안정성**: 동시성 문제 없는 안전한 멀티플레이어 환경

### 설계 철학
- **방어적 프로그래밍**: 모든 단계에서 검증과 안전장치
- **명확한 상태 관리**: GuiSession을 통한 플레이어별 독립적 상태 추적
- **원자적 작업**: 강화석 소모 → 확률 계산 → 결과 적용의 순서 보장

---

## 🔧 중요한 설계 결정

### 1. GuiSession 클래스 설계

**왜 targetItem을 복제해서 저장하는가?**

```java
GuiSession(UUID playerId, ItemStack targetItem, int originalSlot) {
    this.targetItem = targetItem.clone(); // ⭐ 복제본 저장
    this.originalSlot = originalSlot;
}
```

**이유**:
- GUI 표시용 아이템은 **불변(immutable)해야 함**
- 플레이어가 원본을 수정해도 GUI에 표시된 정보는 변경되지 않음
- 강화 실행 시 원본과 비교하여 변경 여부 감지 가능

**트레이드오프**:
- ✅ 장점: 데이터 무결성 보장, 변경 감지 가능
- ❌ 단점: 메모리 사용량 증가 (플레이어당 ItemStack 1개)
- 💡 결론: 서버에서 ItemStack 하나는 미미한 비용, 안전성이 우선

---

### 2. isSimilarItem() 메서드의 비교 로직

**왜 단순 equals()를 사용하지 않는가?**

```java
private boolean isSimilarItem(ItemStack item1, ItemStack item2) {
    // 타입, 인챈트, 내구도, 커스텀 이름 비교
    // 하지만 수량(amount)는 비교하지 않음
}
```

**이유**:
- `ItemStack.equals()`는 **수량까지 비교**하여 너무 엄격함
- 우리는 "같은 종류의 아이템"인지만 확인하면 됨
- 인챈트와 내구도가 같으면 동일한 아이템으로 간주

**비교 항목**:
1. ✅ Material (타입)
2. ✅ Enchantments (인챈트)
3. ✅ Durability (내구도)
4. ✅ Custom Name (커스텀 이름)
5. ❌ Amount (수량) - 비교하지 않음

**관련 코드**: `EnhanceGUI.java:557-595`

---

### 3. 강화석 소모 순서

**중요: 강화석은 확률 계산 전에 먼저 소모됨**

```java
// handleEnhanceButtonClick()
if (!consumeEnhanceStones(event.getInventory(), stoneCount)) {
    return; // 강화석 소모 실패
}

// 강화석 소모 후 확률 계산 및 강화 진행
EnhanceResult result = enhanceManager.performEnhance(...);
```

**이유**:
1. **데이터 일관성**: 실패 시 복구가 더 복잡함
2. **악용 방지**: 강화 실패 시 강화석 복구 버그 악용 가능성 차단
3. **현실성**: 실제 강화 시스템과 유사한 느낌 (소모품 먼저 사용)

**트레이드오프**:
- ❌ 단점: 예외 발생 시 강화석만 소모되고 강화 안될 수 있음
- ✅ 해결: try-finally와 철저한 검증으로 예외 발생 방지

---

### 4. 세션 타임아웃 설정

```java
private static final long SESSION_TIMEOUT = 300_000; // 5분
```

**왜 5분인가?**
- 너무 짧으면: 플레이어가 강화석 준비하다 세션 만료
- 너무 길면: 메모리 낭비, 서버 재시작 시 문제
- 5분: 충분한 여유 + 주기적 정리로 메모리 관리

**주기적 정리**:
```java
// ToolEnhancer.java - onEnable()
Bukkit.getScheduler().runTaskTimer(this, () -> {
    enhanceGUI.cleanupExpiredSessions();
}, 6000L, 6000L); // 5분마다 실행
```

---

### 5. ConcurrentHashMap 사용

```java
private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();
private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();
```

**왜 ConcurrentHashMap인가?**
- Bukkit의 이벤트 핸들러는 **단일 스레드**에서 실행
- 하지만 `runTaskLater`, `runTaskTimer` 등은 다른 스레드일 수 있음
- 세션 정리, 타임아웃 체크 등에서 **동시 접근 가능성** 존재

**안전성 우선**: 성능 오버헤드는 미미하지만 스레드 안전성 보장

---

## ⚙️ 핵심 시스템 동작 흐름

### 강화 프로세스 전체 흐름

```
[1. 명령어 실행]
/enhance 입력
    ↓
EnhanceCommand.onCommand()
    ↓
현재 슬롯 번호 저장: player.getInventory().getHeldItemSlot()
    ↓
EnhanceGUI.openEnhanceSelectGUI(player, itemInHand)

[2. 인챈트 선택]
GuiSession 생성 (targetItem.clone(), originalSlot 저장)
    ↓
인챈트 선택 GUI 표시
    ↓
플레이어가 인챈트 클릭
    ↓
handleEnchantSelectionClick()
    ↓
session.selectedEnchantment 설정
    ↓
EnhanceGUI.openEnhanceProcessGUI()

[3. 강화 진행]
강화석 슬롯 표시 (12, 13, 14, 21, 22, 23, 30, 31, 32)
    ↓
플레이어가 강화석 배치
    ↓
handleStoneSlotClick() → updateEnhanceInfo(), updateEnhanceButton()
    ↓
플레이어가 강화 버튼 클릭
    ↓
handleEnhanceButtonClick()
    ↓
[중복 실행 체크] processingPlayers.add()
    ↓
[세션 검증] session.isValid()
    ↓
[원본 아이템 검증] originalSlot의 아이템이 여전히 같은지 확인
    ↓
[강화석 개수 검증] minRequired 이상인지 확인
    ↓
[강화석 소모] consumeEnhanceStones()
    ↓
EnhanceManager.performEnhance()
    ↓
[확률 계산]
baseRate = getBaseSuccessRate(currentLevel, vanillaMax)
successRate = calculateSuccessRate(stoneCount, baseRate)
destroyRate = calculateDestroyRate(stoneCount, nextLevel)
    ↓
[난수 생성 및 결과 판정]
roll = random.nextDouble()
- roll < destroyRate → DESTROYED
- roll < destroyRate + successRate → SUCCESS
- else → FAILED
    ↓
[결과 적용]
SUCCESS: originalSlot에 강화된 아이템 설정
DESTROYED: originalSlot을 AIR로 설정
FAILED: 변경 없음
    ↓
[정리]
processingPlayers.remove()
GUI 닫기
결과 메시지 출력
```

---

### 확률 계산 상세

**1단계: 기본 성공률 결정**
```java
double baseRate = stoneManager.getBaseSuccessRate(currentLevel, vanillaMax);

// 로직:
if (currentLevel < 0) {
    return 0.8; // 인챈트 없는 상태에서 +1
}
if (currentLevel < vanillaMax) {
    return max(0.3, 0.8 - currentLevel * 0.1); // 바닐라 내에서는 쉬움
}
// 바닐라 초과 시 급격히 어려워짐
int overLevel = currentLevel - vanillaMax + 1;
if (overLevel <= 3) return max(0.1, 0.3 - overLevel * 0.05);
if (overLevel <= 6) return max(0.05, 0.15 - (overLevel-3) * 0.03);
return 0.05; // 최소 5%
```

**2단계: 강화석 보너스 적용**
```java
double successRate = stoneManager.calculateSuccessRate(stoneCount, baseRate);

// 로직:
double bonus = min(stoneCount * 0.05, 0.40); // 개당 5%, 최대 40%
double finalRate = min(baseRate + bonus, 0.95); // 최대 95%
```

**3단계: 파괴율 계산**
```java
double destroyRate = stoneManager.calculateDestroyRate(stoneCount, level);

// 로직:
if (level <= 3) return 0.0; // 안전 구간

double baseDestroy;
if (level <= 5) baseDestroy = (level - 3) * 0.05;       // +4: 5%, +5: 10%
else if (level <= 7) baseDestroy = 0.10 + (level-5)*0.10; // +6: 20%, +7: 30%
else if (level <= 10) baseDestroy = 0.30 + (level-7)*0.15; // +8~+10
else baseDestroy = min(0.90, 0.75 + (level-10)*0.05);

double reduction = min(stoneCount * 0.02, 0.30); // 개당 -2%, 최대 -30%
return max(0.0, baseDestroy - reduction);
```

**예시: 날카로움 5 → 6 (바닐라 최대 초과 1)**
```
baseRate = max(0.1, 0.3 - 1*0.05) = 0.25 (25%)
강화석 5개 사용 시:
  successRate = min(0.25 + 5*0.05, 0.95) = 0.50 (50%)
  destroyRate = max(0.0, 0.20 - 5*0.02) = 0.10 (10%)
  failRate = 1.0 - 0.50 - 0.10 = 0.40 (40%)
```

---

## ⚠️ 알려진 제약사항

### 1. 강화 중 서버 크래시 시나리오

**문제**:
```
1. 플레이어가 강화석 소모하고 확률 계산 중
2. 서버 크래시 발생
3. 재시작 시 강화석만 소모되고 강화 결과 적용 안됨
```

**현재 상태**: 해결 안됨 (로그 기록만 존재)
**영향도**: 매우 낮음 (크래시 타이밍이 정확히 맞아야 함)
**향후 개선**: 트랜잭션 로그 또는 롤백 시스템 필요

---

### 2. 멀티월드 지원

**현재 상태**:
- 기본적으로 작동하지만 테스트 안됨
- 월드별 설정 분리 없음

**제약사항**:
- 모든 월드에서 동일한 강화 확률 사용
- 특정 월드에서만 강화 비활성화 불가

---

### 3. 버전 호환성

**테스트된 버전**: 1.21
**이론상 지원**: 1.20.x (Spigot API 호환)
**미확인**: 1.19.x 이하

**잠재적 문제**:
- `EnchantmentStorageMeta` 동작 차이
- `Damageable` 인터페이스 변경
- GUI 렌더링 차이

---

### 4. 성능 고려사항

**세션 메모리 사용량**:
```java
플레이어당 세션 크기:
- UUID: 16 bytes
- ItemStack: ~100-500 bytes (인챈트에 따라)
- Enchantment 참조: 8 bytes
- 기타 메타데이터: ~50 bytes

→ 플레이어당 약 200-600 bytes
→ 100명 동시 접속 시 약 20-60 KB (무시 가능한 수준)
```

**주기적 정리**:
- 5분마다 만료된 세션 정리
- processingPlayers는 강화 완료 즉시 제거
- 메모리 누수 가능성: 매우 낮음

---

## 🚀 향후 개선 방향

### 1. 강화 히스토리 로깅
```java
// 각 강화 시도를 DB 또는 파일에 기록
[2025-10-01 14:23:45] Player: krangpq
  Item: DIAMOND_SWORD (Sharpness V → VI)
  Stones: 5
  Result: SUCCESS (50% chance)
```

**장점**:
- 디버깅 용이
- 통계 분석 가능
- 롤백 기능 구현 가능

---

### 2. 강화 애니메이션
```java
// 강화 버튼 클릭 시 1-2초간 애니메이션 재생
// - 강화석이 반짝이는 효과
// - 사운드 이펙트
// - 결과에 따른 파티클
```

**장점**:
- 플레이어 몰입감 증가
- 강화의 중요성 강조

---

### 3. 보호 주문서 아이템
```java
// 파괴 방지 아이템 추가
// - 강화 실패 시 파괴 대신 레벨 유지
// - 매우 희귀한 아이템으로 설정
```

---

### 4. 강화 성공 시 서버 전체 공지
```java
if (level >= 10) { // +10 이상 달성 시
    Bukkit.broadcastMessage(
        ChatColor.GOLD + "[!] " + player.getName() + 
        "님이 " + itemName + "을(를) +" + level + " 강화에 성공했습니다!"
    );
}
```

---

### 5. 강화 통계 명령어
```java
/enhance stats [player]
// 표시 내용:
// - 총 강화 시도 횟수
// - 성공/실패/파괴 비율
// - 가장 높은 강화 레벨
// - 사용한 총 강화석 개수
```

---

## 📚 참고 자료

### 관련 Spigot API
- [InventoryClickEvent](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/inventory/InventoryClickEvent.html)
- [Enchantment](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/enchantments/Enchantment.html)
- [ItemStack](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/inventory/ItemStack.html)

### 학습 자료
- [Bukkit Plugin Tutorial](https://www.spigotmc.org/wiki/spigot-plugin-development/)
- [GUI Creation Guide](https://www.spigotmc.org/wiki/creating-a-gui-inventory/)

---

---

## 📅 최근 업데이트 (v1.0.7 - 2025-10-01)

### 커밋: cdfd277 "변경"

#### 추가된 주요 기능:

**1. config.yml 기반 난이도 조절 시스템**
```java
// EnhanceStoneManager.java 생성자에서 설정 로드
private final double severityScale;
private final double globalMinimumRate;
private final boolean allowBeyondMax;
private final double maxLevelMultiplier;
private final int absoluteMaxLevel;

public EnhanceStoneManager(Plugin plugin) {
    this.severityScale = plugin.getConfig().getDouble(
        "success_rates.beyond_vanilla.severity_scale", 1.0
    );
    // ... 기타 설정 로드
}
```

**설계 의도**:
- 서버 관리자가 재빌드 없이 난이도 조절 가능
- 초보자 친화적 서버 vs 하드코어 서버 차별화
- 플러그인 설치 후 바로 사용 가능한 기본값 제공

**2. 동적 최대 레벨 계산 시스템**
```java
// 이전 (하드코딩)
int absoluteMaxLevel = vanillaMaxLevel * 2;

// 이후 (설정 기반)
int absoluteMaxLevel = stoneManager.getAbsoluteMaxLevel(vanillaMaxLevel);

public int getAbsoluteMaxLevel(int vanillaMaxLevel) {
    if (!allowBeyondMax) return vanillaMaxLevel;
    
    if (limitEnabled) {
        int calculatedMax = (int) (vanillaMaxLevel * maxLevelMultiplier);
        return Math.min(calculatedMax, absoluteMaxLevel);
    }
    return absoluteMaxLevel;
}
```

**왜 이렇게 변경했는가?**:
- 하드코딩된 `× 2` 제한이 모든 서버에 적합하지 않음
- PvP 서버: 낮은 최대 레벨로 밸런스 유지
- PvE 서버: 높은 최대 레벨로 엔드게임 콘텐츠 제공
- 설정으로 분리하여 유연성 확보

**3. severityScale 적용 로직**
```java
// getBaseSuccessRate() 메서드 수정
double baseReduction;
if (overLevel <= 3) {
    baseReduction = overLevel * 0.05; // 기본 5%씩 감소
} else if (overLevel <= 6) {
    baseReduction = 0.15 + (overLevel - 3) * 0.03;
} else {
    baseReduction = 0.24;
}

// severityScale 적용
double scaledReduction = baseReduction * severityScale;
double rate = 0.3 - scaledReduction;

// globalMinimumRate 적용
return Math.max(globalMinimumRate, rate);
```

**효과**:
- `severityScale = 0.5`: 감소폭 절반 → 더 쉬운 강화
- `severityScale = 2.0`: 감소폭 2배 → 더 어려운 강화
- `globalMinimumRate`: 최악의 경우에도 최소 성공률 보장

**4. UTF-8 인코딩 설정**
```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <encoding>UTF-8</encoding>
    </configuration>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-resources-plugin</artifactId>
    <configuration>
        <encoding>UTF-8</encoding>
    </configuration>
</plugin>
```

**이유**:
- config.yml의 한글 주석이 빌드 시 깨지는 문제 방지
- Windows 환경에서도 정상적인 한글 처리

---

#### 리팩토링 및 코드 정리:

**불필요한 주석 제거**
```java
// 이전: 과도한 주석
// 강화 가능한 아이템 타입 확인
return isEnhantableItem(item.getType());

// 이후: 메서드명이 자명한 경우 주석 제거
return isEnhantableItem(item.getType());
```

**설계 철학**:
- 코드 자체가 문서가 되도록 작성 (Self-Documenting Code)
- 복잡한 로직에만 주석 유지
- README와 DEVELOPMENT_NOTES로 상세 설명 이동

---

#### 설정 마이그레이션 가이드:

**v1.0.6 이하에서 업그레이드 시**:
1. 기존 `config.yml` 백업
2. 새 버전 설치 후 자동 생성된 `config.yml` 사용
3. 기존 설정값 이전 (주로 `allow_beyond_max`)

**새 설정 항목 기본값**:
- `severity_scale: 1.0` (기존 동작 유지)
- `global_minimum_rate: 0.01` (1%)
- `max_level_limit.enabled: false` (제한 없음)
- `absolute_max_level: 30` (안전장치)

---

## 관련 문서

- **사용자 가이드**: `README.md`
- **버그 히스토리**: `BUGFIX.md`
- **개발 표준**: `PLUGIN_DEVELOPMENT_STANDARD.md`

---

**마지막 업데이트**: 2025-10-01  
**작성자**: krangpq  
**버전**: 1.0.7