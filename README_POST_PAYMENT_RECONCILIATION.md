# 결제 후처리 정합성 배치

결제 완료 이후 포인트·재고·정산 누락 건을 짧은 주기로 재처리한다. `PaymentCompletionService`(PocketPay_Core)가
포인트/재고/정산/알림 단계 중 하나라도 실패하면 예외를 삼키고 `payment_alert_log`에 기록만 남기는데(요소 1),
그 기록을 실제로 복구하는 쪽이 이 배치다. 도메인별로 실패 성격과 재처리 주기가 달라 Job을 3개로 분리했다
(`job.paymentcompletion.{stock,point,settlement}`) — 알림(NOTIFICATION) 실패는 데이터 정합성에 영향을 주지
않으므로 이 배치가 복구하지 않고 Admin 미해결 알림 목록에만 남는다.

## Job 목록

| Job 이름 | 대상 | 필수 파라미터 |
|---|---|---|
| `paymentCompletionStockRecoveryJob` | 재고 확정(reserved → sold) 실패 | `chunkSize`, `staleMinutes` |
| `paymentCompletionPointRecoveryJob` | 포인트 사용/적립 실패 | `chunkSize`, `staleMinutes` |
| `paymentCompletionSettlementRecoveryJob` | 정산 원본 행 생성 실패 | `chunkSize`, `staleMinutes` |

`staleMinutes`는 클레임(`status='PROCESSING'`)한 뒤 완료되지 못하고 죽은 건을 회수하는 임계값이다 — 인스턴스가
클레임 직후 죽으면 해당 건이 영영 PROCESSING에 머무르지 않도록, `updated_at`이 이 값보다 오래된 PROCESSING
건도 다음 실행 대상에 다시 포함시킨다.

## 도메인 구분이 message에 의존하는 이유

`payment_alert_log.alert_type`은 `STOCK_CONFIRMATION_FAILED`(재고 전용)와 `PAYMENT_COMPLETION_FAILED`(포인트
사용/적립·알림·정산 공용) 두 종류뿐이다. Core 스키마는 이 레포가 건드리지 않기로 했으므로, `PAYMENT_COMPLETION_FAILED`
안에서 포인트/정산을 가르는 것은 `message` 컬럼의 `"{step} 후처리 실패"` 접두어(`POINT_USE`/`POINT_EARN`/`SETTLEMENT`)뿐이다.
Core의 `CriticalAlertService#alertPaymentPostProcessingFailed` 메시지 포맷이 바뀌면
`PointRecoveryMapper`/`SettlementRecoveryMapper`의 `findPendingAlerts` 쿼리도 함께 갱신해야 한다 — 더 튼튼하게
하려면 Core 쪽에 `completion_step` 같은 구조화 컬럼을 추가하는 편이 맞지만, 지금은 스키마 변경 없이 가는 쪽으로
결정했다.

## 실행 정책

- 재고 확정 정합성: 1~5분 주기
- 포인트·정산 후처리: 5~10분 주기
- `lastId` 키셋 페이지네이션과 `chunkSize` 기반 처리
- 클레임(`payment_alert_log` 조건부 UPDATE) → 도메인 반영 → RESOLVED 갱신을 하나의 트랜잭션으로 묶어 원자성을
  보장한다. 중간에 실패하면 클레임까지 롤백되어 PENDING/FAILED로 남고, 실패 기록(`retry_count` 증가)만 별도의
  짧은 트랜잭션으로 커밋한다.
- 재고는 Core의 `lock:stock:{productId}` Redisson 락을 동일하게 사용하고, 포인트는 `point_balance`를
  `SELECT ... FOR UPDATE`로 잠가 Core의 `PointService`와 동일한 동시성 제어를 재현한다.
- 포인트 원장(`point_ledger`)과 정산(`settlement`) 모두 재실행 시 중복 적재를 막는 멱등 조건을 건다.
