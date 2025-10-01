# Development Notes

이 문서는 ToolEnhancer 플러그인의 개발 과정에서 발생한 주요 설계 결정, 해결한 버그, 그리고 코드 이해를 돕기 위한 개발자 노트입니다.

## 📋 목차
- [핵심 설계 의도](#핵심-설계-의도)
- [주요 버그 및 해결 방법](#주요-버그-및-해결-방법)
- [중요한 설계 결정](#중요한-설계-결정)
- [핵심 시스템 동작 흐름](#핵심-시스템-동작-흐름)
- [API 설계](#api-설계) ⭐ NEW
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

---

## 🔌 API 설계

### API 설계 철학

**목표**: 다른 플러그인 개발자가 ToolEnhancer를 쉽고 안전하게 연동할 수 있도록

#### 3가지 핵심 원칙:

1. **안정성 우선 (Stability First)**
    - API 메서드 시그니처는 절대 변경하지 않음
    - 새 기능은 새 메서드로 추가
    - `@Deprecated` 사용하여 점진적 마이그레이션

2. **Null-Safe**
    - 모든 메서드는 null 파라미터를 안전하게 처리
    - 예외를 던지지 않고 기본값 반환 (0, false 등)

3. **독립성 보장 (Independence)**
    - API 호출 전 `isEnabled()` 체크 권장
    - ToolEnhancer 없어도 컴파일/실행 가능하도록 설계

---

### ToolEnhancerAPI 클래스 구조

```java
package com.krangpq.toolenhancer.api;

public class ToolEnhancerAPI {
    private static ToolEnhancer plugin;
    
    // 내부용 - 메인 클래스에서 설정
    public static void setPlugin(ToolEnhancer instance);
    
    // === 필수 체크 메서드 ===
    public static boolean isEnabled();
    public static String getVersion();
    
    // === 강화 레벨 조회 ===
    public static int getEnhanceLevel(ItemStack, Enchantment);
    public static Map<Enchantment, Integer> getAllEnhanceLevels(ItemStack);
    public static int getTotalEnhanceLevel(ItemStack);
    
    // === 바닐라 초과 여부 ===
    public static boolean isBeyondVanillaMax(ItemStack, Enchantment);
    public static boolean hasEnhancementAtLeast(ItemStack, Enchantment, int);
}
```

---

### 주요 메서드 설명

#### 1. **isEnabled()**

```java
public static boolean isEnabled() {
    return plugin != null && plugin.isEnabled();
}
```

**용도**: API 사용 가능 여부 확인 (항상 첫 번째로 체크)

**예시**:
```java
if (!ToolEnhancerAPI.isEnabled()) {
    return; // ToolEnhancer 없음, 기본 로직 사용
}
```

**설계 의도**:
- 다른 플러그인이 ToolEnhancer 없이도 컴파일/실행 가능
- NoClassDefFoundError 방지
- 우아한 실패 (Graceful Degradation)

---

#### 2. **getEnhanceLevel()**

```java
public static int getEnhanceLevel(ItemStack item, Enchantment ench) {
    if (!isEnabled()) return 0;
    if (item == null || ench == null) return 0;
    
    return item.getEnchantmentLevel(ench);
}
```

**설계 의도**:
- `ItemStack.getEnchantmentLevel()`의 **안전한 래퍼**
- null 체크를 API가 대신 처리
- 항상 유효한 정수 반환 (예외 없음)

**왜 단순 래퍼인가?**

현재는 단순하지만, 향후 다음과 같은 기능 추가 가능:
- **캐싱**: 반복 조회 시 성능 최적화
- **로깅**: 디버깅용 API 호출 추적
- **권한 체크**: 특정 플러그인만 접근 허용
- **커스텀 인챈트**: 바닐라 외 인챈트 지원

**트레이드오프**:
- ✅ 장점: 미래 확장 가능, 안전성 보장
- ❌ 단점: 단순 조회에도 메서드 호출 오버헤드
- 💡 결론: 안전성과 확장성이 성능보다 중요

---

#### 3. **isBeyondVanillaMax()**

```java
public static boolean isBeyondVanillaMax(ItemStack item, Enchantment ench) {
    if (!isEnabled()) return false;
    if (item == null || ench == null) return false;
    
    int currentLevel = item.getEnchantmentLevel(ench);
    int vanillaMax = ench.getMaxLevel();
    
    return currentLevel > vanillaMax;
}
```

**용도**: 다른 플러그인이 "강화된 아이템"을 쉽게 식별

**사용 예시**:
```java
// 상점 플러그인에서 강화된 아이템 판매 금지
if (ToolEnhancerAPI.isBeyondVanillaMax(item, Enchantment.SHARPNESS)) {
    player.sendMessage("강화된 아이템은 판매할 수 없습니다!");
    return;
}
```

**설계 의도**:
- 다른 개발자가 바닐라 최대 레벨을 외울 필요 없음
- 명확한 의도 표현 (가독성 향상)

---

#### 4. **getTotalEnhanceLevel()**

```java
public static int getTotalEnhanceLevel(ItemStack item) {
    if (!isEnabled()) return 0;
    if (item == null) return 0;
    
    int total = 0;
    for (Enchantment ench : item.getEnchantments().keySet()) {
        total += item.getEnchantmentLevel(ench);
    }
    return total;
}
```

**용도**: 아이템의 "강화 점수" 계산

**사용 예시**:
```java
// PvP 플러그인에서 밸런스 조정
int power = ToolEnhancerAPI.getTotalEnhanceLevel(weapon);
double damageMultiplier = 1.0 + (power * 0.05);
```

**설계 의도**:
- 강화된 아이템의 "가치"를 단일 숫자로 표현
- 경제 시스템, PvP 밸런싱 등에 활용

---

### ItemEnhancedEvent 설계

```java
package com.krangpq.toolenhancer.api.events;

public class ItemEnhancedEvent extends Event implements Cancellable {
    private final Player player;
    private final ItemStack item;
    private final Enchantment enchantment;
    private final int oldLevel;
    private final int newLevel;
    private boolean cancelled = false;
    
    // Getters...
    
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
```

**설계 의도**:

1. **Cancellable 구현**
    - 다른 플러그인이 강화를 취소할 수 있음
    - 예: 특정 지역에서 강화 금지, 레벨 제한 등

2. **풍부한 정보 제공**
    - oldLevel, newLevel: 변경 전후 비교 가능
    - player, item, enchantment: 필터링/로깅에 활용

3. **이벤트 발생 시점**
    - 강화 성공 직후, 아이템 적용 전
    - 취소 시 강화석은 이미 소모됨 (롤백 불가)

**사용 예시**:
```java
@EventHandler
public void onEnhance(ItemEnhancedEvent event) {
    // 날카로움 10 이상 금지
    if (event.getEnchantment() == Enchantment.SHARPNESS 
        && event.getNewLevel() >= 10) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("날카로움 10 이상은 금지되었습니다!");
    }
}
```

---

### API 버전 관리 정책

#### Semantic Versioning 적용

```
ToolEnhancer 1.2.3
              │ │ │
              │ │ └─ Patch: 버그 수정
              │ └─── Minor: API 메서드 추가 (하위 호환)
              └───── Major: API 메서드 제거/변경 (호환성 깨짐)
```

#### 호환성 보장 약속

1. **Minor 업데이트 (1.x.x → 1.y.x)**
    - 기존 API 메서드는 절대 제거하지 않음
    - 새 메서드만 추가
    - 기존 플러그인은 재컴파일 없이 작동

2. **Major 업데이트 (1.x.x → 2.0.0)**
    - API 메서드 제거/변경 가능
    - 최소 6개월 전 공지
    - Migration Guide 제공

#### Deprecated 패턴

```java
/**
 * @deprecated 1.2.0부터 사용 중단, 2.0.0에서 제거 예정
 * {@link #getEnhanceLevelSafe(ItemStack, Enchantment)} 사용 권장
 */
@Deprecated
public static int getEnhanceLevel(ItemStack item, Enchantment ench) {
    return getEnhanceLevelSafe(item, ench);
}

/**
 * @since 1.2.0
 */
public static int getEnhanceLevelSafe(ItemStack item, Enchantment ench) {
    // 새 구현 (더 안전함)
}
```

---

### 왜 Static API 패턴을 선택했는가?

**선택지 1: Static API (선택됨)**
```java
ToolEnhancerAPI.getEnhanceLevel(item, ench);
```

**선택지 2: Instance API**
```java
ToolEnhancer plugin = (ToolEnhancer) getServer().getPluginManager().getPlugin("ToolEnhancer");
plugin.getAPI().getEnhanceLevel(item, ench);
```

#### Static API의 장점:
1. ✅ **간결함**: 한 줄로 호출 가능
2. ✅ **타이핑 적음**: 다른 플러그인 코드가 깔끔
3. ✅ **NPE 방지**: `isEnabled()` 체크로 안전
4. ✅ **전역 접근**: 어디서든 `import static` 가능

#### Static API의 단점:
1. ❌ **테스트 어려움**: 모킹(Mocking) 불가
2. ❌ **멀티 인스턴스 불가**: 싱글톤 패턴 강제
3. ❌ **메모리 누수 위험**: Static 참조는 GC 안됨

#### 결론:
- Minecraft 플러그인 환경에서는 **Static API가 표준**
- Bukkit/Spigot API 자체도 Static 패턴 (Bukkit.getServer())
- 테스트는 실제 서버 환경에서 진행 (Unit Test 불필요)

---

### API 안전 장치 (Safeguards)

#### 1. 플러그인 미설치 시 안전

```java
public static int getEnhanceLevel(ItemStack item, Enchantment ench) {
    if (!isEnabled()) return 0; // ⭐ 첫 번째 체크
    // ... 나머지 로직
}
```

**효과**: ToolEnhancer 없어도 다른 플러그인이 정상 작동

---

#### 2. Null 파라미터 안전

```java
public static int getEnhanceLevel(ItemStack item, Enchantment ench) {
    if (!isEnabled()) return 0;
    if (item == null || ench == null) return 0; // ⭐ Null 체크
    // ... 나머지 로직
}
```

**효과**: NullPointerException 절대 발생 안함

---

#### 3. 예외 발생 방지

```java
try {
    return item.getEnchantmentLevel(ench);
} catch (Exception e) {
    plugin.getLogger().warning("API 호출 실패: " + e.getMessage());
    return 0; // 기본값 반환
}
```

**효과**: API 호출이 절대 크래시를 유발하지 않음

---

### 향후 API 확장 계획

#### v1.1.0 예정 (Minor Update)
- `canEnhanceFurther(ItemStack, Enchantment)`: 추가 강화 가능 여부
- `getMaxPossibleLevel(Enchantment)`: config 기반 최대 레벨 반환
- `getEnhanceHistory(ItemStack)`: 강화 이력 조회 (로그 기능 추가 시)

#### v1.2.0 예정 (Minor Update)
- `calculateEnhanceSuccessRate(ItemStack, Enchantment, int)`: 예상 성공률 계산
- `getRequiredStones(int)`: 레벨별 필요 강화석 개수 반환

#### v2.0.0 고려 사항 (Major Update)
- **커스텀 인챈트 지원**: Enchantment → CustomEnchantment 확장
- **비동기 API**: CompletableFuture 도입 (DB 조회 시)
- **이벤트 우선순위**: 강화 전/후 이벤트 분리

---

### API 문서화 정책

#### JavaDoc 필수 사항

모든 public 메서드는 다음 정보 포함:
```java
/**
 * 아이템의 인챈트 레벨을 조회합니다.
 * 
 * <p>이 메서드는 null-safe하며, ToolEnhancer가 없어도 안전하게 호출할 수 있습니다.
 * ToolEnhancer가 없거나 파라미터가 null인 경우 0을 반환합니다.</p>
 * 
 * <h3>사용 예시:</h3>
 * <pre>{@code
 * ItemStack sword = ...;
 * int level = ToolEnhancerAPI.getEnhanceLevel(sword, Enchantment.SHARPNESS);
 * if (level > 5) {
 *     // 날카로움 6 이상
 * }
 * }</pre>
 * 
 * @param item 대상 아이템 (null 가능)
 * @param ench 인챈트 (null 가능)
 * @return 인챈트 레벨, ToolEnhancer 없거나 null인 경우 0
 * @since 1.0.7
 * @see #isBeyondVanillaMax(ItemStack, Enchantment)
 */
public static int getEnhanceLevel(ItemStack item, Enchantment ench) {
    // ...
}
```

---

### API 사용 시 주의사항

#### ❌ 잘못된 사용 예시

```java
// 1. isEnabled() 체크 없이 호출
int level = ToolEnhancerAPI.getEnhanceLevel(item, ench); // 위험!

// 2. 메인 클래스에서 직접 import
import com.krangpq.toolenhancer.ToolEnhancer; // NoClassDefFoundError!

// 3. 강제 캐스팅
ToolEnhancer plugin = (ToolEnhancer) Bukkit.getPluginManager()
    .getPlugin("ToolEnhancer"); // NullPointerException!
```

#### ✅ 올바른 사용 예시

```java
// 1. 항상 isEnabled() 먼저 체크
if (!ToolEnhancerAPI.isEnabled()) {
    // ToolEnhancer 없을 때 기본 동작
    return;
}

// 2. Integration 클래스 분리
public class ToolEnhancerIntegration {
    public boolean initialize() {
        try {
            return ToolEnhancerAPI.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }
}

// 3. 메인 클래스에서는 Integration만 사용
if (hasToolEnhancerIntegration()) {
    integration.doSomething();
}
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

## 📅 최근 업데이트 (v1.0.8 - 2025-10-01)

### 커밋: "feat: Add give command for other players and tab completion"

#### 추가된 주요 기능:

**1. 다른 플레이어에게 강화석 지급**
```bash
/enhance give <개수> <플레이어>
```

**설계 의도**:
- 관리자가 이벤트 보상으로 강화석 지급 가능
- 콘솔에서도 사용 가능 (자동화 스크립트 지원)
- 최대 64개 제한으로 남용 방지

**구현 상세**:
```java
// EnhanceCommand.java - handleGiveCommand()
private boolean handleGiveCommand(CommandSender sender, String[] args) {
    // 플레이어 파라미터가 없으면 자신에게 지급
    Player target;
    if (args.length >= 3) {
        target = Bukkit.getPlayer(args[2]);
    } else {
        if (!(sender instanceof Player)) {
            sender.sendMessage("콘솔에서는 플레이어 이름을 지정해야 합니다!");
            return true;
        }
        target = (Player) sender;
    }
    
    // 강화석 생성 및 지급
    ItemStack stones = plugin.getEnhanceStoneManager().createEnhanceStone(amount);
    target.getInventory().addItem(stones);
    
    // 양방향 피드백 메시지
    if (sender.equals(target)) {
        sender.sendMessage("강화석 " + amount + "개를 지급받았습니다!");
    } else {
        target.sendMessage("강화석 " + amount + "개를 지급받았습니다!");
        sender.sendMessage(target.getName() + "님에게 강화석 " + amount + "개를 지급했습니다!");
    }
    
    // 로그 기록
    plugin.getLogger().info(sender.getName() + "이(가) " + target.getName() + 
        "에게 강화석 " + amount + "개를 지급했습니다.");
}
```

**2. Tab 자동완성 기능**
```java
// EnhanceCommand.java - TabCompleter 인터페이스 구현
public class EnhanceCommand implements CommandExecutor, TabCompleter {
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, 
                                      String alias, String[] args) {
        if (args.length == 1) {
            // 서브명령어 제안: give, help
            return Arrays.asList("give", "help").stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
                
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // 개수 제안: 1, 5, 10, 32, 64
            return Arrays.asList("1", "5", "10", "32", "64");
            
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // 온라인 플레이어 목록 제안
            String input = args[2].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}
```

**구현 내용**:
- 첫 번째 인자: `give`, `help` 제안
- 두 번째 인자: `1`, `5`, `10`, `32`, `64` 제안
- 세 번째 인자: 온라인 플레이어 목록 동적 제안
- 권한 없는 명령어는 자동완성에서 제외

**3. 명확한 피드백 시스템**

**자신에게 지급 시**:
```
[플레이어] 강화석 10개를 지급받았습니다!
```

**다른 플레이어에게 지급 시**:
```
[수령자] 강화석 10개를 지급받았습니다!
[지급자] Steve님에게 강화석 10개를 지급했습니다!
```

**콘솔 로그**:
```
[ToolEnhancer] Admin이(가) Steve에게 강화석 10개를 지급했습니다.
```

**설계 의도**:
- 지급자와 수령자 모두에게 피드백
- 로그에도 기록하여 관리자가 추적 가능
- 명확한 메시지로 혼란 방지

---

### 주요 코드 변경사항:

**EnhanceCommand.java**
- `TabCompleter` 인터페이스 구현 추가
- `handleGiveCommand()` 메서드 리팩토링
    - 플레이어 파라미터 선택적으로 변경
    - 콘솔 사용 시 플레이어 필수 검증
    - 최대 개수 64개 제한 추가
    - 존재하지 않는 플레이어 에러 처리
- `onTabComplete()` 메서드 구현
    - 권한 기반 자동완성 필터링
    - 온라인 플레이어 목록 동적 생성
    - 부분 입력 시 필터링 지원

**ToolEnhancer.java**
- `onEnable()`에서 TabCompleter 등록 추가
```java
EnhanceCommand enhanceCommand = new EnhanceCommand(this, enhanceGUI);
getCommand("enhance").setExecutor(enhanceCommand);
getCommand("enhance").setTabCompleter(enhanceCommand); // 추가
```

**plugin.yml**
- `usage` 필드를 다중 라인 형식으로 변경
- 모든 서브명령어 사용법 명시
```yaml
commands:
  enhance:
    usage: |
      /enhance - 강화 GUI 열기
      /enhance give <개수> - 강화석 지급
      /enhance give <개수> <플레이어> - 다른 플레이어에게 강화석 지급
      /enhance help - 도움말
```

---

### 안전장치 및 검증:

**1. 최대 개수 제한**
```java
if (amount > 64) {
    sender.sendMessage("한 번에 최대 64개까지만 지급할 수 있습니다!");
    return true;
}
```

**2. 플레이어 존재 확인**
```java
Player target = Bukkit.getPlayer(targetName);
if (target == null) {
    sender.sendMessage("플레이어 '" + targetName + "'를 찾을 수 없습니다!");
    return true;
}
```

**3. 권한 확인**
```java
if (!sender.hasPermission("toolenhancer.admin")) {
    sender.sendMessage("이 명령어를 사용할 권한이 없습니다!");
    return true;
}
```

**4. 콘솔 사용 시 플레이어 이름 필수**
```java
if (!(sender instanceof Player)) {
    sender.sendMessage("콘솔에서는 플레이어 이름을 지정해야 합니다!");
    sender.sendMessage("사용법: /enhance give <개수> <플레이어>");
    return true;
}
```

---

### 테스트 시나리오:

**1. 기본 기능 테스트**
```bash
/enhance give 10            # 자신에게 지급
/enhance give 10 Steve      # Steve에게 지급
enhance give 10 Steve       # 콘솔에서 실행
```

**2. Tab 자동완성 테스트**
```bash
/enhance <Tab>              # give, help 표시
/enhance give <Tab>         # 1, 5, 10, 32, 64 표시
/enhance give 10 <Tab>      # 온라인 플레이어 목록 표시
/enhance give 10 St<Tab>    # Steve, Steve2 등 필터링
```

**3. 에러 케이스 테스트**
```bash
/enhance give               # "사용법" 메시지 표시
/enhance give abc           # "올바른 숫자를 입력해주세요"
/enhance give 100           # "최대 64개까지만 지급 가능"
/enhance give 10 없는플레이어  # "플레이어를 찾을 수 없습니다"
/enhance give -5            # "1 이상이어야 합니다"
```

**4. 권한 테스트**
```bash
# 일반 플레이어로 로그인
/enhance give 10            # "권한이 없습니다" 메시지
/enhance <Tab>              # give가 표시되지 않음
```

**5. 콘솔 테스트**
```bash
# 서버 콘솔에서
enhance give 10             # "플레이어 이름을 지정해야 합니다"
enhance give 10 Steve       # 정상 지급
```

---

### 알려진 제약사항:

**1. 오프라인 플레이어 지급 불가**
- 현재 온라인 플레이어에게만 지급 가능
- `Bukkit.getPlayer()`는 온라인 플레이어만 반환
- 향후 개선: `Bukkit.getOfflinePlayer()` + 인벤토리 직접 수정

**2. 대량 지급 시 제한**
- 한 번에 최대 64개까지만 지급 가능
- ItemStack의 최대 스택 크기 제한
- 회피 방법: 여러 번 실행 또는 스크립트 사용

**3. 인벤토리 가득 찬 경우**
- 현재: 인벤토리에 추가 시도
- `addItem()`은 넘치는 아이템을 반환
- 향후 개선: 넘치는 아이템을 땅에 드롭

---

### 향후 개선 방향:

**v1.1.0 예정 (Minor Update)**
- [ ] `/enhance giveall <개수>` - 모든 온라인 플레이어에게 지급
- [ ] `/enhance give <개수> <플레이어> silent` - 조용히 지급 (메시지 없음)
- [ ] 오프라인 플레이어 지급 지원
- [ ] 인벤토리 가득 찬 경우 자동 드롭

**v1.2.0 고려**
- [ ] `/enhance history` - 강화석 지급 이력 조회
- [ ] `/enhance take <개수> <플레이어>` - 강화석 회수 기능
- [ ] `/enhance balance <플레이어>` - 특정 플레이어의 강화석 소지량 확인

---

### 성능 및 메모리 영향:

**Tab 자동완성 오버헤드**:
```java
// 온라인 플레이어 목록 생성 비용
Bukkit.getOnlinePlayers().stream()  // O(n) where n = 온라인 플레이어 수
    .map(Player::getName)            // O(n)
    .filter(...)                     // O(n)
    .collect(Collectors.toList());   // O(n)

// 총 비용: O(n)
// 100명 서버 기준: ~1ms 미만 (무시 가능)
```

**메모리 사용량**:
- TabCompleter는 상태를 저장하지 않음
- 임시 리스트만 생성 (즉시 GC 대상)
- 영향도: 매우 낮음

---

### 설계 결정 배경:

**왜 최대 64개로 제한했는가?**
- ItemStack의 자연스러운 스택 크기
- 너무 많은 개수는 관리자 실수 가능성
- 대량 지급이 필요하면 스크립트 사용 권장

**왜 TabCompleter를 추가했는가?**
- 관리자 편의성 향상 (타이핑 오류 감소)
- 온라인 플레이어 이름 기억 불필요
- 표준 Minecraft 명령어 경험 제공

**왜 양방향 피드백을 제공하는가?**
- 지급자: 명령어가 성공했는지 확인
- 수령자: 누가 보냈는지 알 수 있음
- 투명성 증가 (악용 방지)

---

### 관련 커밋:

```
feat: Add give command for other players and tab completion

- Add player parameter to /enhance give command
- Implement TabCompleter interface for command suggestions
- Add console support with player name requirement
- Update plugin.yml usage field
- Add max amount limit (64) and validation
- Add detailed feedback messages for both sender and receiver
- Add logging for admin actions

Closes #XX (if applicable)
```

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