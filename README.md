# ToolEnhancer

마인크래프트 도구/무기/방어구를 바닐라 최대 레벨 이상으로 강화할 수 있는 Spigot/Bukkit 플러그인입니다.

## 📋 목차
- [기능](#기능)
- [설치 방법](#설치-방법)
- [사용 방법](#사용-방법)
- [강화 시스템](#강화-시스템)
- [설정](#설정)
- [명령어 및 권한](#명령어-및-권한)
- [개발 정보](#개발-정보)

## ✨ 기능

- **바닐라 최대 레벨 초과 강화**: 인챈트를 기본 최대 레벨 이상으로 강화 가능
- **확률형 강화 시스템**: 강화석 개수에 따라 성공률이 증가
- **직관적인 GUI**: 2단계 GUI로 쉽게 강화 진행
- **강화석 제작**: 다이아몬드 + 에메랄드 + 네더의 별 조합으로 제작
- **안전 구간**: +3 이하는 파괴되지 않는 안전 구간
- **세션 관리**: 강화 중 아이템 이동 방지 및 중복 실행 차단

## 📦 설치 방법

1. [Releases](https://github.com/krangpq/toolenhancer/releases)에서 최신 버전 다운로드
2. `ToolEnhancer-1.0.6.jar` 파일을 서버의 `plugins/` 폴더에 넣기
3. 서버 재시작 또는 `/reload` 명령어 실행
4. `plugins/ToolEnhancer/config.yml` 파일에서 설정 조정 (선택사항)

### 요구사항
- Minecraft 1.21+
- Spigot/Paper 서버
- Java 17 이상

## 🎮 사용 방법

### 1. 강화석 제작
강화석 제작대 레시피:
```
D E D
E N E
D E D

D = 다이아몬드
E = 에메랄드
N = 네더의 별
```

또는 관리자 명령어로 지급:
```
/enhance give <개수>
```

### 2. 도구 강화하기

1. 강화할 도구/무기/방어구를 손에 들기
2. `/enhance` 명령어 실행
3. 강화할 인챈트 선택
4. 강화석을 슬롯에 배치 (많이 넣을수록 성공률 증가)
5. 초록색 "강화 시작!" 버튼 클릭

### 강화 가능한 아이템
- **도구**: 곡괭이, 도끼, 삽, 괭이, 가위
- **무기**: 검, 삼지창, 활, 석궁
- **방어구**: 투구, 갑옷, 레깅스, 부츠
- **기타**: 낚싯대, 부싯돌과 부시

## ⚔️ 강화 시스템

### 확률 계산

#### 기본 성공률 (레벨별)
- **0 → 1**: 80%
- **바닐라 최대 이하**: 80% - (현재 레벨 × 10%)
- **바닐라 최대 초과 1~3**: 25%, 20%, 15%
- **바닐라 최대 초과 4~6**: 12%, 9%, 6%
- **바닐라 최대 초과 7+**: 최소 5%

#### 강화석 보너스
- 강화석 1개당 **+5% 성공률** (최대 +40%)
- 최종 성공률은 **최대 95%**까지

#### 파괴 확률
- **+3 이하**: 파괴 없음 (안전 구간)
- **+4**: 5%
- **+5**: 10%
- **+6**: 20%
- **+7**: 30%
- **+8 이상**: 점진적 증가 (최대 90%)
- 강화석 1개당 **-2% 파괴율** (최대 -30%)

#### 최소 필요 강화석
| 목표 레벨 | 최소 개수 |
|---------|---------|
| +1 ~ +3 | 1개 |
| +4 ~ +6 | 2개 |
| +7 ~ +9 | 3개 |
| +10 ~ +12 | 5개 |
| +13 ~ +15 | 8개 |
| +16 이상 | 8 + (레벨-15)개 (최대 15개) |

### 강화 결과
- **성공**: 인챈트 레벨 +1, 강화석 소모
- **실패**: 아이템 유지, 강화석 소모
- **파괴**: 아이템 파괴, 강화석 소모 (+4 이상에서만 발생)

## ⚙️ 설정

`config.yml` 파일에서 강화 시스템을 상세하게 커스터마이징할 수 있습니다:

```yaml
success_rates:
  beyond_vanilla:
    # 바닐라 최대 레벨 초과 허용 여부
    allow_beyond_max: true
    
    # 난이도 배율 (바닐라 최대 레벨 초과 시 적용)
    # 0.0 = 초과 레벨에서도 감소 없음 (쉬움)
    # 1.0 = 기본 난이도
    # 2.0 = 두 배로 어려움
    severity_scale: 1.0
    
    # 초과 레벨에서 적용될 절대 최소 성공률
    # 0.01 = 1%, 0.05 = 5%
    global_minimum_rate: 0.01
    
    # 최대 레벨 제한 설정
    max_level_limit:
      # true: 레벨 제한 활성화, false: 제한 해제
      enabled: false
      
      # 바닐라 최대 레벨의 몇 배까지 허용할지
      # 예: 2.0이면 효율성 5 -> 최대 10까지 가능
      max_multiplier: 2.0
      
      # 절대 최대 레벨 (어떤 인챈트도 이 레벨을 초과할 수 없음)
      absolute_max_level: 30
```

### 주요 설정 항목 설명

#### **severity_scale** (난이도 조절)
바닐라 최대 레벨을 초과한 강화의 어려움을 조절합니다:
- `0.0`: 매우 쉬움 - 초과 레벨에서도 성공률 감소 없음
- `0.5`: 쉬움 - 감소폭 절반
- `1.0`: 기본 - 밸런스 잡힌 난이도 (권장)
- `1.5`: 어려움 - 감소폭 1.5배
- `2.0`: 매우 어려움 - 감소폭 2배

#### **global_minimum_rate** (최소 성공률 보장)
아무리 높은 레벨이라도 이 확률 이하로 떨어지지 않습니다:
- `0.01` (1%): 매우 어려운 게임 플레이
- `0.05` (5%): 도전적이지만 합리적 (권장)
- `0.10` (10%): 접근하기 쉬운 난이도

#### **max_level_limit** (최대 레벨 제한)
- `enabled: false`: 제한 없음 (absolute_max_level까지 가능)
- `enabled: true`: 바닐라 최대 × max_multiplier까지만 허용
    - 예: 날카로움(바닐라 최대 5) × 2.0 = 최대 10레벨

### 설정 예시

**초보자 친화적 서버**:
```yaml
severity_scale: 0.5
global_minimum_rate: 0.10
max_level_limit:
  enabled: true
  max_multiplier: 1.5
```

**하드코어 서버**:
```yaml
severity_scale: 2.0
global_minimum_rate: 0.01
max_level_limit:
  enabled: false
  absolute_max_level: 50
```

## 📝 명령어 및 권한

### 명령어
| 명령어 | 설명 | 권한 |
|-------|------|------|
| `/enhance` | 강화 GUI 열기 | `toolenhancer.use` |
| `/enhance give <개수>` | 강화석 지급 (관리자) | `toolenhancer.admin` |
| `/enhance help` | 도움말 보기 | `toolenhancer.use` |

### 권한
| 권한 | 설명 | 기본값 |
|-----|------|-------|
| `toolenhancer.use` | 강화 시스템 사용 | 모든 플레이어 |
| `toolenhancer.admin` | 관리자 명령어 사용 | OP만 |

## 🔧 개발 정보

### 프로젝트 구조
```
src/main/java/com/krangpq/toolenhancer/
├── ToolEnhancer.java              # 메인 플러그인 클래스
├── commands/
│   └── EnhanceCommand.java        # /enhance 명령어 처리
├── gui/
│   └── EnhanceGUI.java            # GUI 관리 (2단계: 선택 → 강화)
└── managers/
    ├── EnhanceManager.java        # 강화 로직 및 확률 계산
    └── EnhanceStoneManager.java   # 강화석 관리 및 레시피
```

### 핵심 클래스 설명

#### EnhanceGUI
- **openEnhanceSelectGUI()**: 1단계 - 인챈트 선택 GUI
- **openEnhanceProcessGUI()**: 2단계 - 강화 진행 GUI
- **handleEnhanceButtonClick()**: 강화 버튼 클릭 처리 (중복 실행 방지, 아이템 이동 검증 포함)
- **GuiSession**: 플레이어별 강화 세션 관리 (타임아웃: 5분)

#### EnhanceManager
- **performEnhance()**: 실제 강화 수행 (확률 계산 → 결과 적용)
- **getAllPossibleEnchantments()**: 적용 가능한 모든 인챈트 반환
- **EnhanceResult**: 강화 결과 (SUCCESS, FAILED, DESTROYED)

#### EnhanceStoneManager
- **calculateSuccessRate()**: 강화석 개수에 따른 최종 성공률 계산
- **calculateDestroyRate()**: 레벨 및 강화석 개수에 따른 파괴율 계산
- **getBaseSuccessRate()**: 레벨별 기본 성공률 반환

### 빌드 방법
```bash
mvn clean package
```
빌드된 JAR 파일은 `target/ToolEnhancer-1.0.6.jar`에 생성됩니다.

### 기술 스택
- Java 17
- Spigot API 1.21
- Maven

## 📄 라이선스

이 프로젝트는 개인 프로젝트입니다.

## 👨‍💻 제작자

**krangpq**

## 🐛 버그 제보 및 건의

이슈나 건의사항이 있으시면 [GitHub Issues](https://github.com/krangpq/toolenhancer/issues)에 남겨주세요.

---

**Version**: 1.0.6  
**API Version**: 1.21  
**Last Updated**: 2025-10-01