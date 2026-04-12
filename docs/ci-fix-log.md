# CI 수정 로그

`docs/next-steps.md`의 "검증 안 된 것" 1번(실 빌드 검증)을 진행하면서 GitHub Actions CI가 줄줄이 실패했고, 라운드별로 수정한 기록.

**핵심 교훈**: 병렬 구현 에이전트 방식은 개발 속도는 빠르지만 **컴파일/런타임 검증 부재**로 인한 hidden bug가 쌓인다. Step 1~11을 모두 마친 뒤 첫 CI 실행에서 한꺼번에 터졌다.

---

## 라운드 1 — Backend 컴파일 에러

**증상**: `DistributedAdvanceLockTest.java:74,77` — `GameService` 생성자 인자 개수 불일치.

**원인**:
- Step 6(통합 테스트)에서 테스트 작성 당시 `GameService`는 7-arg
- Step 5(Prometheus)에서 `GameAdvanceMetrics`, `MeterRegistry` 추가 → 9-arg
- Step 10(Tracing)에서 `OpenTelemetry` 추가 → 10-arg
- 테스트는 9-arg로 호출하던 상태 (Step 10 에이전트가 테스트 업데이트를 "Step 6 영향 없음"으로 건너뜀)

**수정**: `DistributedAdvanceLockTest`에 `@Autowired OpenTelemetry` + `new GameService(..., openTelemetry)` 10-arg 호출.

**커밋**: `eb57eaa`

---

## 라운드 2 — Frontend npm cache 실패

**증상**: `Some specified paths were not resolved, unable to cache dependencies.`

**원인**: `ci.yml`이 `cache-dependency-path: frontend/package-lock.json`을 참조하는데 `package-lock.json` 파일이 리포에 없음. 에이전트 환경에 npm이 없어서 생성 못 한 상태로 커밋됐음.

**수정**: 로컬에서 `npm install --package-lock-only` 실행 → 1851 lines 생성 → 커밋.

**커밋**: `eb57eaa` (라운드 1과 함께)

---

## 라운드 3 — JUnit Platform launcher 누락

**증상**: `Failed to load JUnit Platform. Please ensure that all JUnit Platform dependencies are available on the test's runtime classpath, including the JUnit Platform launcher.`

**원인**: Gradle 9.x가 JUnit Platform launcher를 runtime classpath에 **명시적으로 요구**. 이전 Gradle 8.x에선 transitive로 해결됐음. GitHub Actions의 `gradle/actions/setup-gradle@v4`가 Gradle 9.4.1을 프로비저닝.

**수정**: `build.gradle`에 `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` 추가. 버전은 Spring Boot BOM 관리.

**커밋**: `192d51c`

---

## 라운드 4 — OAuth2 autoconfig 예외 (부작용 발생)

**증상**: 통합 테스트 8개 전부 Spring context 로딩 실패 — `IllegalStateException at OAuth2ClientProperties.java:69`.

**원인**: Spring Boot 3.3은 `spring.security.oauth2.client.registration.github.client-id`가 **빈 문자열**이면 registration을 무시하지 않고 예외 던짐. CI에는 `GITHUB_CLIENT_ID` 환경변수 없음 → `${GITHUB_CLIENT_ID:}` 기본값이 빈 문자열 → 예외.

**시도한 수정**: `application-test.yml`에 `autoconfigure.exclude: OAuth2ClientAutoConfiguration + ReactiveOAuth2ClientAutoConfiguration` 추가.

**부작용**: `ClientRegistrationRepository` 빈이 없어져서 `SecurityConfig.oauth2Login()`이 주입 실패 → `NoSuchBeanDefinitionException`. 여전히 context 로딩 실패.

**커밋**: `ec9f337` (부작용 발생 — 라운드 5에서 복구)

---

## 라운드 5 — Dummy GitHub Registration

**증상**: `No qualifying bean of type ClientRegistrationRepository available`.

**수정**: 라운드 4의 autoconfig 제외를 걷어내고, `application-test.yml`에 dummy 값 주입:
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: test-client-id
            client-secret: test-client-secret
            scope: read:user,user:email
```

OAuth2 autoconfig는 활성 상태로 `ClientRegistrationRepository` 빈 생성. 실제 OAuth 플로우는 테스트에서 호출되지 않으므로 무해.

**커밋**: `43de40b`

**결과**: context 로딩 성공 → 테스트가 실제로 실행되기 시작. 하지만...

---

## 라운드 6 — Testcontainers Singleton Pattern

**증상**:
- `DistributedAdvanceLockTest`는 **통과**
- `DuplicateAnswerTest` 이후 6개 테스트 모두 각 2분 넘게 걸리며 실패
- 로그: `PSQLException: Connection to localhost:32774 refused`, `AmqpConnectException: Connection refused`
- 전체 실행 14분 넘어 timeout

**원인**: `@Container` + `@Testcontainers`는 **테스트 클래스별** JUnit 5 lifecycle.
- 각 테스트 클래스가 로드될 때 컨테이너 start
- 클래스 종료 시 stop
- 그런데 Spring TestContext는 `@SpringBootTest` 기본 동작으로 캐시 재사용
- 첫 클래스 이후 **Spring context는 살아있는데 컨테이너는 재시작** → 포트 매핑 바뀜 → 구 포트로 연결 시도 → refused

`withReuse(true)`는 로컬에서만 동작(`~/.testcontainers.properties` 설정 필요). CI에서는 무시.

**수정**: Singleton Container Pattern으로 전환.
- `@Testcontainers`, `@Container` 어노테이션 제거
- `static { POSTGRES.start(); REDIS.start(); RABBIT.start(); }` — JVM lifetime 동안 한 번만 기동
- `@DynamicPropertySource`는 그대로 → 모든 테스트 클래스가 같은 포트 공유

**커밋**: `412c924`

**결과**: CI 실행 중 (이 문서 작성 시점).

---

## 타임라인

```
685d4aa  Step 11 완료 (CI 실패 시작)
eb57eaa  Round 1+2: GameService 10-arg + package-lock.json       (CI: Backend 런타임 실패)
192d51c  Round 3: junit-platform-launcher                        (CI: 테스트 7개 컨텍스트 실패)
ec9f337  Round 4: OAuth2 autoconfig exclude                      (CI: Bean 누락 실패)
43de40b  Round 5: Dummy GitHub registration                      (CI: 테스트 실행됨, 인프라 연결 실패)
412c924  Round 6: Singleton Container Pattern                    (CI: 대기 중)
```

---

## 일반화된 교훈

1. **병렬 구현 에이전트는 컴파일 검증을 대체하지 않는다** — 각 에이전트는 자기 파일만 컴파일 가능성을 가정. 여러 Step에 걸쳐 같은 클래스가 확장되면 테스트 업데이트가 누락될 수 있다. → **Step 완료 후 즉시 CI 한 번은 돌릴 것**.
2. **Gradle 9+ 마이그레이션 주의** — `junit-platform-launcher` 등 이전엔 transitive로 해결되던 의존성이 명시 요구로 변경.
3. **Spring Boot 3.3 OAuth2** — 빈 `client-id`는 silent-skip 아님. Test 프로파일에 항상 dummy 값 주입.
4. **Testcontainers + `@SpringBootTest` 캐시** — `@Container` 방식은 multi-class 테스트 스위트에 위험. Singleton Container Pattern이 기본값이어야 한다.
5. **병렬 에이전트가 "Step 6 테스트 영향 없음"이라 단언해도 검증 못 한다** — Step 10에서 `GameService`에 `OpenTelemetry` 추가 시 테스트 생성자 호출도 같이 업데이트하거나 최소한 CI로 확인.
