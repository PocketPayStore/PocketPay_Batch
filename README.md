# PocketPay Batch

PocketPay의 비동기 보정과 정산을 담당하는 Spring Batch 애플리케이션입니다. 주문 만료, PG 결과 대사, 결제 후처리 복구와 판매자 정산을 API 요청 경로와 분리해 처리합니다.

## Batch Jobs

| Job | 역할 | 주요 파라미터 |
|---|---|---|
| orderExpirationJob | 미결제 주문 만료와 예약 재고 해제 | thresholdMinutes, chunkSize, startDate, endDate |
| paymentTimeoutReconciliationJob | TIMEOUT_UNKNOWN 결제의 PG 결과 재확인 | thresholdMinutes, chunkSize |
| paymentCompletionPointRecoveryJob | 구매 포인트 적립 실패 복구 | chunkSize, staleMinutes |
| paymentCompletionStockRecoveryJob | 예약 재고 판매 확정 복구 | chunkSize, staleMinutes |
| settlementCreationJob | 완료 결제 중 미정산 건의 정산 생성 | chunkSize |
| settlementJob | 기간별 판매자 정산 | chunkSize, startDate, endDate |

~~~mermaid
flowchart TB
    A[Core 결제] -->|결과 미확정| B[결제 결과 확인 필요]
    B --> C[PG 거래 대사 Job]
    C --> D[결제·주문 상태 보정]
    A -->|후처리 실패| E[후처리 실패 목록]
    E --> F[포인트 적립 복구 Job]
    E --> G[재고 복구 Job]
    A -->|완료 결제| H[정산 생성 Job]
    H --> I[미정산 결제 조회·정산 생성]
    L[결제 기한 초과] --> M[주문 만료 Job]
    M --> N[예약 재고 해제]
~~~

## 핵심 설계

- **영역별 복구**: 실패한 책임만 다시 실행하고 기존 포인트·재고·정산 결과를 확인해 중복 반영을 방지합니다.
- **정산 책임 분리**: Core 응답 경로와 분리해 완료 결제 중 미정산 건을 직접 조회하고 정산을 생성합니다.
- **조인·집계 조회**: 결제·주문 상품·상품 정보를 한 번에 조회해 정산 금액과 수수료를 계산합니다.
- **중복 생성 방지**: `payment_id` 유니크 제약조건과 미정산 조건으로 정산을 한 번만 생성합니다.
- **다중 인스턴스 처리**: 조건부 갱신으로 대상을 선점하고, staleMinutes가 지난 중단 건은 다시 시도합니다.
- **분산 락**: 재고 변경은 Redisson 락으로 동시 실행을 제어합니다.
- **DB 분리**: 비즈니스 DB와 Spring Batch 메타데이터 DB를 별도 DataSource로 사용합니다.
- **대량 처리**: MyBatis 기반 Reader/Writer와 chunk 처리로 구성합니다.

## 기술 스택

Java 17, Spring Boot 4.1, Spring Batch, MyBatis, MySQL, Redis/Redisson, Actuator, Prometheus, Testcontainers를 사용합니다.

## 실행

JDK 17, Business MySQL, Batch metadata MySQL, Redis와 PocketPay PG가 필요합니다. 웹 서버 없이 실행되며 Job 이름과 파라미터를 지정합니다.

~~~bash
./gradlew bootRun --args='--spring.profiles.active=local --spring.batch.job.name=paymentTimeoutReconciliationJob thresholdMinutes=5 chunkSize=100'
~~~

후처리 복구 예시:

~~~bash
./gradlew bootRun --args='--spring.profiles.active=local --spring.batch.job.name=paymentCompletionStockRecoveryJob chunkSize=100 staleMinutes=10'
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

주문 만료, 파라미터 검증, 미확정 결제 보정, 포인트·재고 복구, 완료 결제 기반 정산 생성의 멱등성과 상태 전이를 테스트합니다.

## 추가 문서

- [결제 완료 후처리 복구 설계](README_POST_PAYMENT_RECONCILIATION.md)
