# PocketPay Batch

PocketPay의 비동기 보정과 정산을 담당하는 Spring Batch 애플리케이션입니다. 주문 만료, PG 결과 대사, 결제 후처리 복구와 판매자 정산을 API 요청 경로와 분리해 처리합니다.

## Batch Jobs

| Job | 역할 | 주요 파라미터 |
|---|---|---|
| orderExpirationJob | 미결제 주문 만료와 예약 재고 해제 | chunkSize (선택: startDate, endDate) |
| paymentTimeoutReconciliationJob | TIMEOUT_UNKNOWN 결제를 PG에 재조회해 확정 — 포인트 사용 확정·재고 확정까지 Core와 동일한 원자적 트랜잭션으로 처리 | thresholdMinutes, chunkSize |
| pointEarnRetryJob | 비동기 처리에 실패한 구매 포인트 적립 재시도 | chunkSize |
| paymentAlertRetryJob | Slack 치명 알림 전송 재시도 | chunkSize |
| settlementCreationJob | 완료 결제 중 미정산 건의 정산 생성 | chunkSize |
| settlementJob | 기간별 판매자 정산 집계 | chunkSize, startDate, endDate |

~~~mermaid
flowchart TB
    A[Core 결제] -->|즉시 재조회도 실패, 결과 불명확| B[TIMEOUT_UNKNOWN]
    B --> C[paymentTimeoutReconciliationJob]
    C --> D["결제·주문 상태 보정(포인트 사용 확정·재고 확정 포함)"]
    A -->|포인트 적립 비동기 실패| E[pointEarnRetryJob]
    A -->|치명 알림 발행 실패| F[paymentAlertRetryJob]
    A -->|완료 결제| G[settlementCreationJob]
    G --> H[미정산 결제 조회·정산 생성]
    H --> I[settlementJob]
    I --> J[가맹점별 정산 집계]
    K[결제 기한 초과] --> L[orderExpirationJob]
    L --> M[예약 재고 해제]
~~~

## 핵심 설계

- **영역별 재시도**: 포인트 적립(`pointEarnRetryJob`)과 Slack 알림(`paymentAlertRetryJob`)은 각자 자신의 책임만 조건부 UPDATE(`PENDING`/`FAILED` → `PROCESSING`)로 선점해 재시도하고, 이미 처리된 건은 조건 불일치로 자연스럽게 건너뜁니다.
- **Core와 동일한 원자적 완료 로직**: `paymentTimeoutReconciliationJob`이 `TIMEOUT_UNKNOWN` 결제를 확정할 때 Core의 결제 완료 트랜잭션과 정확히 같은 일(포인트 사용 확정, 재고 확정, 주문 `PAID` 전환)을 하나의 MyBatis 트랜잭션으로 수행합니다 — 어느 경로로 완료되든 결과가 같습니다.
- **Core-Batch 간 행 단위 조율**: 포인트 적립·환불 회수처럼 Core의 비동기 워커와 Batch 재시도 잡이 같은 행을 동시에 건드릴 수 있는 지점은 `FOR UPDATE` 락으로 조율해 이중 지급·유실 업데이트를 막습니다.
- **정산 책임 분리**: Core 응답 경로와 분리해 완료 결제 중 미정산 건을 직접 조회하고 정산을 생성합니다.
- **조인·집계 조회**: 결제·주문 상품·상품 정보를 한 번에 조회해 정산 금액과 수수료를 계산합니다.
- **중복 생성 방지**: `payment_id` 유니크 제약조건과 미정산 조건으로 정산을 한 번만 생성합니다.
- **분산 락**: 재고 변경은 Redisson 락으로 동시 실행을 제어합니다(Core와 같은 `lock:stock:{productId}` 키 공유).
- **DB 분리**: 비즈니스 DB와 Spring Batch 메타데이터 DB를 별도 DataSource로 사용합니다.
- **대량 처리**: MyBatis 기반 Reader/Writer와 chunk 처리로 구성합니다.

## 기술 스택

Java 17, Spring Boot 4.1, Spring Batch, MyBatis, MySQL, Redis/Redisson, Actuator, Prometheus, Testcontainers를 사용합니다.

## 실행

JDK 17, Business MySQL, Batch metadata MySQL, Redis가 필요합니다. `paymentTimeoutReconciliationJob`이 토스페이먼츠 실 API(`https://api.tosspayments.com`)를 직접 조회하므로 별도 Mock 서버 없이 테스트용 시크릿 키만 있으면 됩니다. 웹 서버 없이 실행되며 Job 이름과 파라미터를 지정합니다.

~~~bash
./gradlew bootRun --args='--spring.profiles.active=local --spring.batch.job.name=paymentTimeoutReconciliationJob thresholdMinutes=5 chunkSize=100'
~~~

포인트 적립 재시도 예시:

~~~bash
./gradlew bootRun --args='--spring.profiles.active=local --spring.batch.job.name=pointEarnRetryJob chunkSize=100'
~~~

정산 생성 예시:

~~~bash
./gradlew bootRun --args='--spring.profiles.active=local --spring.batch.job.name=settlementCreationJob chunkSize=100'
~~~

날짜 형식과 필수 값은 각 Job의 Validator를 기준으로 합니다.

## 테스트

~~~bash
./gradlew test
~~~

주문 만료, 파라미터 검증, 미확정 결제 보정(포인트 사용 확정·재고 확정 포함), 포인트 적립·Slack 알림 재시도, 완료 결제 기반 정산 생성의 멱등성과 상태 전이를 테스트합니다.
