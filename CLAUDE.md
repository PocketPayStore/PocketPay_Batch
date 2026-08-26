# CLAUDE.md — payment-batch (PocketPay_Batch)

`PocketPay_Batch`는 [`PocketPay_Core`](../PocketPay_Core)(결제 코어 서버) 포트폴리오의 **요소 5 — 정산 배치 + 대사**를 담당하는 별도 배치 레포다. PocketPay_Core의 CLAUDE.md에 이미 이렇게 명시돼 있다:

> 실제 지급 처리는 별도 배치 잡(요소 5)이 이 테이블(`settlement`)을 읽어서 수행한다.
> 이 레포 또는 별도 배치 잡이 정산 파일과 내부 `payment`/`settlement` 데이터를 Chunk 단위로 대조, 금액 불일치·상태 불일치·한쪽에만 존재 4종을 탐지.

이 레포가 그 "별도 배치 잡"이다. **상시 떠 있는 API 서버가 아니라, 주기적으로 실행되고 끝나는 1회성 배치 잡**이라는 점이 PocketPay_Core와 가장 큰 차이다. 웹 레이어(Controller)가 없고, `spring-boot-starter-webmvc` 의존성도 없다.

---

## PocketPay_Core와의 관계 — DB 인스턴스는 공유하되, 스키마(DB)는 물리적으로 분리한다

이 레포는 **DataSource를 두 개** 쓴다(같은 RDS 인스턴스, 서로 다른 데이터베이스):

- **`business`** — PocketPay_Core가 소유한 기존 스키마(`pocket_pay_store`). `payment`/`settlement` 등을 읽고, `settlement`의 상태(`PENDING → SETTLED/FAILED`)와 `settled_at`을 갱신한다. **이 레포는 여기에 테이블을 만들거나 스키마를 바꾸지 않는다** — 마이그레이션 소유권은 전부 PocketPay_Core의 Flyway.
- **`batch`** — 이 레포가 전적으로 소유하는 별도 스키마(`pocket_pay_batch`). Spring Batch 자체 메타데이터(`BATCH_JOB_INSTANCE`, `BATCH_STEP_EXECUTION` 등)와, 이 레포가 필요로 하는 테이블(대사 결과·불일치 기록 등)이 여기 산다.

물리적으로 다른 데이터베이스로 나눴기 때문에, 처음 검토했던 "같은 스키마를 두 레포가 각자 Flyway 히스토리로 건드리면 충돌한다"는 문제 자체가 생기지 않는다 — `batch` 스키마는 이 레포만 건드리는 게 확실하니까. **그럼에도 이 레포는 Flyway를 쓰지 않기로 했다**(`build.gradle`에 의존성 자체가 없음). 대신:

- `spring.batch.jdbc.initialize-schema: never` — Spring Batch 공식 메타데이터 스키마(`org/springframework/batch/core/schema-mysql.sql`)를 앱이 자동으로 만들어주지 않는다는 뜻이다. **`pocket_pay_batch` DB 자체와 이 스키마는 첫 배포 전에 `scripts/pocket_pay_batch_schema.sql`을 수동으로 한 번 적용해둬야 한다** — 실제 사용 중인 `spring-batch-core:6.0.5` jar 안의 공식 스키마를 그대로 가져온 파일. 나중에 Flyway를 `batch` 데이터소스에만 한정해서 재도입하는 것도 물리적으로 분리돼 있으니 안전하지만, 지금은 안 쓰는 쪽으로 정했다.
- 이 레포가 새로 필요로 하는 도메인 테이블(대사 결과 등)도 같은 이유로 `scripts/pocket_pay_batch_schema.sql` 하단에 이어서 정의한다 — Core 쪽 마이그레이션에 얹지 않는다(이전 버전 문서에선 그렇게 적었는데, 스키마 분리 결정으로 더 이상 유효하지 않음).

### 멀티 DataSource 배선 — `config/DataSourceConfig.java`, `config/BatchJobRepositoryConfig.java`

`spring.datasource.business.*`/`spring.datasource.batch.*`는 Spring Boot가 자동으로 인식하는 프리픽스가 아니라서, `DataSourceConfig`가 `@ConfigurationProperties`로 두 `DataSource` 빈을 직접 만든다. (다른 레포 `seeyouagain-batch`의 `DataSourceConfig` 패턴을 참고했다 — 거긴 JPA라 `@EnableJpaRepositories(entityManagerFactoryRef=...)`로 스키마별 저장소를 나눴지만, 여긴 MyBatis라 `@MapperScan(sqlSessionFactoryRef=...)`로 같은 역할을 한다.)

- **`batchDataSource`가 `@Primary`다** — Boot의 배치 관련 자동설정 일부가 DataSource가 여러 개일 때 `@Primary`(또는 유일한 후보)를 요구하는 경우가 있어, 배치 인프라 쪽을 기본값으로 맞춰둔다. MyBatis 매퍼는 `@MapperScan(basePackages=..., sqlSessionFactoryRef=...)`로 `mapper.business`/`mapper.batch` 패키지를 각각 다른 `SqlSessionFactory`에 명시적으로 묶어놨으니, `@Primary` 여부와 무관하게 매퍼는 항상 올바른 스키마로 간다. **mapper 인터페이스는 반드시 이 두 패키지 아래에 만들 것** — 지금은 실제 대사 Job/매퍼가 없어서 두 패키지 다 비어 있다(스캔 대상이 없어도 에러는 안 남).
- `application-dev.yml`의 top-level `mybatis:` 블록은 뺐다 — `DataSourceConfig`가 `SqlSessionFactory`를 직접 만들면서 `mapUnderscoreToCamelCase`/매퍼 경로를 Java 코드에서 지정하므로, Boot의 MyBatis 자동설정(및 그 YAML 프로퍼티)은 아예 안 쓰인다.
- **`batchTransactionManager`는 `DataSourceConfig`가 소유하고 `BatchJobRepositoryConfig`가 그대로 주입받아 쓴다** — 두 클래스가 각자 트랜잭션 매니저를 따로 만들면 Spring Batch의 Chunk 트랜잭션과 `batch` 스키마에 쓰는 MyBatis `ItemWriter`(대사 결과 기록)가 서로 다른 트랜잭션이 되어, Chunk 커밋/롤백이 메타데이터와 우리 도메인 테이블에 함께 적용되는 원자성이 깨진다.

Spring Batch 쪽은 예상과 다른 함정이 하나 있었다: 아무 설정도 안 하고 `spring-boot-starter-batch`만 넣으면 Boot가 기본으로 구성해주는 `JobRepository`는(Spring Batch 6의 `DefaultBatchConfiguration` 기본 구현, 실제 jar 바이트코드로 확인함) **`ResourcelessJobRepository`** 다 — 즉 Job/Step 실행 이력이 DB에 전혀 남지 않는다. `@BatchDataSource`류의 한정자 애노테이션으로 DataSource만 지정해주면 되는 구조가 아니었다(Spring Boot 4.1의 `spring-boot-batch` 모듈엔 그런 애노테이션 자체가 없다). 그래서 `BatchJobRepositoryConfig`가 `DefaultBatchConfiguration`을 상속해서 `jobRepository()`를 직접 오버라이드하고, `JdbcJobRepositoryFactoryBean`으로 `batch` DataSource·`batchTransactionManager`에 명시적으로 연결한 JDBC 기반 `JobRepository`를 만든다 — "마지막 성공 Chunk부터 재시작"이 이 레포의 핵심 요구사항이라 이 부분은 타협 불가.

### 테스트에서의 스키마 부트스트랩 (알려진 트레이드오프)

Flyway가 없고 MyBatis는 JPA처럼 엔티티에서 DDL을 자동 생성해주지 않으므로, 테스트에서 스키마를 만들 방법이 없다. `business` 쪽은 **`src/test/resources/schema.sql`에 PocketPay_Core가 소유한 테이블 중 이 레포가 실제로 건드리는 부분(`payment`, `settlement`)만 손으로 옮겨 적어** Spring Boot의 SQL 초기화로 띄운다 — Core의 실제 마이그레이션과 별개로 유지되는 **사본**이라, Core 쪽 스키마가 바뀌면 수동으로 맞춰 갱신해야 한다(자동 동기화 없음, 알려진 리스크). `batch` 쪽은 Spring Batch 공식 스키마 + 이 레포가 만드는 테이블을 마찬가지로 테스트 리소스에 SQL로 박아둔다. (둘 다 아직 파일 자체는 안 만들었다 — 대사 로직 구현 착수 시점에 함께 작성할 것. `payment`/`settlement`를 실제로 읽는 매퍼 테스트를 짤 때 필요해짐.)

### `src/test/resources/application.yml` — 컨텍스트 로드용 최소 설정 (이미 있음)

`@SpringBootTest`가 뜨려면 `business`/`batch` 두 `DataSource`가 뭐든 연결 가능한 값을 가지고 있어야 한다(빈 값이면 Hikari가 기동 시점에 바로 실패). 실 RDS(`application-dev.yml`, gitignored)에 의존하지 않도록 H2 인메모리로 채워뒀다:

- `spring.datasource.business`/`batch` — 각각 별도 H2 인메모리 DB(`jdbc:h2:mem:business`/`batch`, `MODE=MySQL`)
- `spring.batch.jdbc.initialize-schema: always` — 운영은 `never`지만 테스트는 H2라 Boot가 공식 Spring Batch 스키마를 알아서 만들어주는 쪽이 훨씬 간편하고 안전하다(`batchDataSource`가 `@Primary`라 이 초기화기가 정확히 그 DataSource를 잡음)
- `spring.batch.job.enabled: false` — 컨텍스트 로드 시점에 실제 Job이 자동 실행되면 안 됨(아직 Job 자체도 없음)
- `spring.main.web-application-type: none` — 이 레포는 웹 의존성이 없는 순수 배치라, `servlet`을 강제하면(처음에 다른 배치 프로젝트 설정을 그대로 참고했다가) 서블릿 컨텍스트 클래스가 없어서 기동 자체가 실패한다. 웹 의존성이 없으면 Boot가 원래 자동으로 `NONE`을 잡아주므로 명시할 필요조차 없다 — `application-dev.yml`도 동일하게 고쳐져 있다.
- `DataSourceConfig`의 매퍼 경로는 `classpath:`가 아니라 **`classpath*:`**를 쓴다 — `mapper.business`/`mapper.batch`에 아직 매퍼 XML이 하나도 없는 지금 상태에서 단일 `classpath:`는 "그 경로 자체가 존재해야 함"을 요구해서 기동이 깨진다. `classpath*:`는 없으면 빈 결과로 넘어간다(MyBatis-Spring 공식 예제 표기와도 일치).

이 네 가지를 실제로 `./gradlew test`/`./gradlew build`를 돌려보면서 하나씩 맞춘 것 — 순서대로 Hikari 빈 URL → 서블릿 컨텍스트 없음 → 매퍼 경로 없음, 총 세 번 실패하고 나서야 초록불이 됐다. 지금은 `PocketPayBatchApplicationTests`(빈 컨텍스트 로드 테스트)만 있고, 실제 매퍼/Job 테스트는 아직 없다.

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| Language | Java 17 (toolchain 고정) |
| Framework | Spring Boot 4.1.1, **Spring Batch** (`spring-boot-starter-batch`) — 웹 레이어 없음 |
| DB 접근 | **MyBatis** (`mybatis-spring-boot-starter`). PocketPay_Core는 JPA를 쓰지만 이 레포는 MyBatis로 간다 — 대사 로직은 도메인 객체 그래프 조작이 아니라 "정산 파일 vs 내부 테이블"을 대량으로 비교·집계하는 SQL 중심 작업이라 JPA의 이점(연관관계 매핑, 변경 감지)이 크지 않고, 오히려 대사에 필요한 집계/조인 쿼리를 직접 다루는 게 명확하다. |
| DB | MySQL(`mysql-connector-j`), **DataSource 2개**(`business`=PocketPay_Core 공유 스키마 읽기전용, `batch`=이 레포 전용 스키마) — **Flyway는 쓰지 않음**, 아래 관계 섹션 참고 |
| 테스트 DB | 단순 매퍼 단위 테스트는 H2, **대사 로직 통합테스트는 Testcontainers-MySQL** — MyBatis는 SQL을 그대로 실행하므로 H2가 MySQL 방언을 가려주지 않는다. 대사 쿼리 정확성이 이 프로젝트의 핵심이라 여기서는 방언 차이를 감수하지 않는다(PocketPay_Core가 Redis에만 Testcontainers를 쓰고 DB는 H2로 충분했던 것과 다른 이유). 스키마는 Flyway가 아니라 손으로 옮겨 적은 `schema.sql`로 부트스트랩(아래 관계 섹션 참고). |
| Actuator | 헬스체크(잡 실행 전 사전 점검용). Prometheus 레지스트리는 포함돼 있지만, 1회성 배치 프로세스 특성상 pull 기반 스크레이핑이 정상 동작하지 않을 수 있다(스크레이프 시점에 이미 종료됐을 수 있음) — Pushgateway 연동 등 후속 조치 없이는 사실상 장식적 의존성이라는 점을 인지하고 간다. |
| 코드 생성 | Lombok |
| 개발 편의 | `spring-boot-devtools` |
| 배포 | Docker 이미지가 아니라 **JAR 직접 배포**. CI/CD는 GitHub Actions가 전담(Jenkins는 빌드/배포 파이프라인이 아니라 **배치 트리거 전용** — private subnet EC2에서 컨테이너로 떠 있으면서 크론으로 `java -jar`를 직접 실행). 상세는 아래 "CI/CD" 섹션 참고. |

---

## 대사(Reconciliation) 로직

### 비교 대상

- **PG 정산 파일**: `mock-pg` 레포(이 레포 밖)가 생성. **파일 소스는 아직 미정** — S3에 CSV로 적재되는지, mock-pg의 REST 엔드포인트로 조회하는지는 mock-pg 쪽 구현이 정해지는 대로 확정한다. 이 불확실성이 설계에 영향을 주지 않도록, 파일 읽기는 `ItemReader` 구현체 하나로 캡슐화하고 비교 로직(`ItemProcessor`)과 분리해둘 것 — 소스가 바뀌어도 `ItemReader` 구현체만 교체하면 되게.
- **내부 데이터**: PocketPay_Core가 쓰는 `payment`, `settlement` 테이블(이 레포는 읽기 전용 + `settlement` 상태 갱신만).

### 탐지하는 불일치 4종 (PocketPay_Core CLAUDE.md에 명시된 것과 동일)

1. **금액 불일치** — 내부 `payment.amount`(또는 `settlement.amount`)와 정산 파일 금액이 다름
2. **상태 불일치** — 내부 상태(`DONE`/`CANCELED`/`PARTIAL_CANCELED`)와 정산 파일상 상태가 다름
3. **내부에만 존재** — 우리 DB엔 결제/정산 기록이 있는데 정산 파일엔 없음(PG가 놓쳤거나, 아직 파일에 반영 안 됨)
4. **PG 파일에만 존재** — 정산 파일엔 있는데 우리 DB엔 없음(우리 쪽이 놓친 경우 — 가장 심각한 케이스, 돈은 오갔는데 우리 시스템이 모르는 상태일 수 있음)

### Chunk 기반 처리와 재시작

Spring Batch의 Chunk-oriented Step(`ItemReader` → `ItemProcessor` → `ItemWriter`)으로 구현한다. Spring Batch의 `JobRepository`가 Job/Step 실행 이력을 남기기 때문에, **실패한 Job을 동일 `JobParameters`로 재실행하면 마지막으로 커밋된 Chunk 이후부터 자동으로 재개된다** — 이게 PocketPay_Core CLAUDE.md가 요구하는 "마지막 성공 Chunk부터 재시작 가능"의 정확한 근거다. 별도로 체크포인트를 직접 구현할 필요는 없고, Step을 restartable하게(멱등하게) 설계하는 것만 신경 쓰면 된다 — 같은 레코드를 두 번 처리해도 중복 불일치 기록이 남지 않도록 `ItemWriter`가 upsert 하거나, Job 시작 시 이전 미완료 실행의 부분 결과를 정리하는 방식.

Spring Batch 메타데이터 테이블(`BATCH_JOB_INSTANCE` 등)은 전용 `batch` 데이터소스(`pocket_pay_batch`)에 산다. `spring.batch.jdbc.initialize-schema: never`로 두고 있어 앱이 자동으로 만들어주지 않으므로, 배포 전 공식 스키마를 수동으로 한 번 적용해둬야 한다 — 위 "PocketPay_Core와의 관계" 참고.

### 트리거 방식

REST API 없음. `CommandLineRunner`(또는 `JobLauncher` 직접 호출)로 애플리케이션 기동 시 Job을 실행하고, 끝나면 종료 코드로 성공/실패를 알린다. 로컬 개발 시엔 `bootRun`으로 즉시 1회 실행해서 확인한다.

---

## 검증 (안정성형 항목 — Before/After 없음, k6 불필요)

PocketPay_Core의 원칙을 그대로 따른다: 이 배치는 성능형이 아니라 안정성형이라 부하테스트 대상이 아니다. **정산 파일에 의도적으로 불일치 케이스 N건(4종 각각 최소 1건 이상)을 주입한 뒤, 배치가 N건 전부 정확한 타입으로 탐지하는지** Testcontainers-MySQL 통합테스트로 검증한다. 재시작 시나리오(Step 중간 실패 → 재실행 → 중복 없이 이어서 처리)도 별도 테스트로 남긴다.

---

## CI/CD

- **CI** (`.github/workflows/ci.yml`) — PR마다 GitHub Actions가 빌드+테스트. PocketPay_Core와 동일 패턴(시크릿으로 `application-dev.yml` 만들고 `./gradlew build`).
- **CD** (`.github/workflows/cd.yml`) — main 브랜치 push 시 GitHub Actions가 전담. **Jenkins는 이 파이프라인의 일부가 아니다** — Jenkins는 private subnet EC2에서 컨테이너로 떠 있으면서, 크론으로 잡을 트리거해 컨테이너 안의 jar를 `java -jar`로 직접 실행하는 역할만 한다(그 잡 스케줄·실행 커맨드 설정은 Jenkins 쪽에서 관리하는 것으로 이 레포 밖). 이 레포의 CD가 하는 일은 **"새로 빌드한 jar를 Jenkins 컨테이너 안의 정해진 경로에 가져다 놓는 것"까지**다.
- 대상 EC2가 private subnet이라 GitHub Actions 러너에서 직접 붙을 네트워크 경로가 없다 — **AWS SSM Run Command**로 우회한다: ① jar를 S3 스테이징 버킷에 올리고 ② `aws ssm send-command`로 그 EC2에 "S3에서 내려받아 `docker cp`로 Jenkins 컨테이너 안에 넣는" 셸 스크립트를 원격 실행시킨다. SSM Agent가 EC2 → AWS로 아웃바운드 폴링하는 구조라 인바운드 포트를 열 필요가 없다는 게 이 방식을 쓰는 이유다.
- 인프라 사전 준비(이 레포 밖): 대상 EC2에 SSM Agent + `AmazonSSMManagedInstanceCore` 권한의 인스턴스 프로파일, GitHub Actions가 assume하는 배포 롤에 `s3:PutObject`/`ssm:SendCommand`/`ssm:GetCommandInvocation` 권한, 그리고 `cd.yml` 상단 주석에 적힌 GitHub `vars` 5종(`AWS_REGION`, `DEPLOY_ARTIFACT_BUCKET`, `EC2_INSTANCE_ID`, `JENKINS_CONTAINER_NAME`, `BATCH_JAR_PATH_IN_CONTAINER`). `BATCH_JAR_PATH_IN_CONTAINER`는 `/opt/pocketpay-batch/app.jar`로 정함 — Jenkins workspace와 무관한 전용 경로(다른 잡이 지우거나 덮어쓸 일 없게). **이 값 자체는 코드로 못 넣는다** — GitHub 레포 Settings → Secrets and variables → Actions → Variables에 직접 등록해둘 것(`gh variable set BATCH_JAR_PATH_IN_CONTAINER --body "/opt/pocketpay-batch/app.jar"`로도 가능).
- Docker 이미지를 안 쓰므로 이 레포엔 `Dockerfile`이 없다.
- **알려진 함정**: `docker cp`는 목적지 디렉터리가 이미 있어야 하고, 없는 상위 경로를 알아서 만들어주지 않는다. 컨테이너 안 `jenkins` 유저는 `/opt` 밑에 쓰기 권한이 없고 컨테이너엔 `sudo`도 없어서(실제로 겪음), `docker cp` 전에 호스트에서 `docker exec -u root <container> mkdir -p ...`로 먼저 만들어준다 — 호스트의 Docker 데몬은 컨테이너 안 유저 권한과 무관하게 root로 exec할 수 있어서 이렇게 우회 가능. 복사 후엔 `chmod -R a+rX`로 Jenkins가 실제 실행할 때 읽을 수 있게 해준다.

---

## 커맨드

```bash
./gradlew test                 # H2(매퍼 단위) + Testcontainers-MySQL(대사 통합테스트) 실행
./gradlew bootRun              # 로컬에서 배치 1회 실행
./gradlew bootJar              # 배포용 JAR 생성 (Docker 이미지가 아니라 JAR 자체를 배포)
```
