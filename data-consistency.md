# 데이터 일관성을 다루기 위한 패턴

## 왜 현대 아키텍처는 2PC(분산 트랜잭션)를 쓰지 않을까?
- **2PC를 안 쓰는 이유**: 긴 Lock 대기, 성능 저하, 시스템 간 강한 결합도(SPOF 위험)
- **현대의 패러다임**: 강한 일관성을 포기하고, 결과적 일관성(Eventual Consistency)을 채택
- **현실적인 대안**: Saga 패턴(전체 흐름 제어) + Outbox 패턴(안전한 메시지 발행)의 조합

### 대안책
- Outbox 패턴
  - **DB 변경**과 **메시지 발행 의도**를 **원자적으로 묶고**, 실제 발행은 릴레이가 `at-least-once`로 **보장**한다
  - 메시지가 최소 한 번은 무조건 발행(`At-least-once`)되므로, 결과적으로 전체 시스템이 안전하게 **결과적 일관성**으로 갈 수 있다.
- Saga 패턴
  - 여러 마이크로서비스에 걸쳐 있는 분산 트랜잭션의 **데이터 일관성을 유지**하기 위한 패턴
  - 진행 중 중간에 특정 서비스에서 **에러가 발생**하면, 이전에 **이미 성공한 로컬 트랜잭션들을 역순으로 취소(롤백)** 하는 별도의 트랜잭션을 실행

  | 항목 | 2PC / XA (Two-Phase Commit) | Saga 패턴 (Saga Pattern) | Transactional Outbox 패턴  |
    | :--- | :--- | :--- | :--- |
  | **핵심 개념** | 중앙 조정자(Coordinator)가 참여 노드들을 묶어 **동기식**으로 2단계 커밋을 수행 | 글로벌 트랜잭션을 일련의 **독립적인 로컬 트랜잭션 체인**으로 분할하고 비동기로 연쇄 호출 | 비즈니스 데이터 변경과 메시지 데이터를 **동일 DB의 단일 로컬 트랜잭션**으로 묶어 저장 후 발행 |
  | **트랜잭션 범위** | **글로벌 분산 트랜잭션** (모든 참여 노드에 Lock 유지) | **각 서비스별 로컬 트랜잭션** | **단일 서비스 내부**의 로컬 트랜잭션 |
  | **일관성 모델** | **강한 일관성 (Strong Consistency)**<br>모든 노드가 즉시 동일 시점 데이터 보장 | **결과적 일관성 (Eventual Consistency)**<br>일시적 불일치를 허용하되 최종 정합성 달성 | 결과적 일관성 (이벤트 발행 관점)<br>메시지 브로커로의 안정적 전달을 보장 |
  | **격리성 (Isolation)** | 분산 격리 수준(Serializable 등) 제공 가능하나 성능 급락 | **격리성 없음 (ACID 중 I의 부재)**<br>중간 성공 상태가 타 서비스에 노출됨 | 단일 서비스 내부에서만 로컬 격리 수준을 따름 |
  | **성능 / 처리량** | **매우 낮음** (동기식 블로킹, 네트워크 n회 왕복, Long Lock으로 병목 심각) | **매우 높음** (비동기/이벤트 기반 처리로 시스템 자원의 빠른 해제 가능) | **높음** (로컬 DB 쓰기 성능에 수렴하며, 외부 메시지 브로커 장애에 영향받지 않음) |
  | **장애 복구 방식** | 코디네이션 상태 복구 및 전역 롤백 메커니즘에 의존 | 실패 시 이미 성공한 단계를 역순으로 뒤집는 **보상 트랜잭션(Compensating Tx) 실행** | **CDC(Debezium 등)나 Polling Publisher**가 Outbox 테이블을 읽어 브로커로 재발행 |
  | **구현 복잡도** | 인프라/DB 레벨 지원으로 코드는 단순하나 운영 및 설정이 매우 까다로움 | **매우 높음** (보상 트랜잭션 설계, 멱등성 보장, 중간 데이터 노출 고려 필요) | **비교적 낮음** (기존 로컬 트랜잭션에 Outbox 테이블 추가 및 발행 프로세스 구축) |
  | **핵심 목적** | 분산 환경에서 완벽한 ACID 트랜잭션 달성 | **여러 마이크로서비스 간**의 비즈니스 워크플로우 정합성 유지 | DB 업데이트와 이벤트 발행의 원자성 확보 (**Dual Write 문제 해결**) |
  | **실무적 위치** | 사실상 사장됨 (일부 레거시 금융권, 패키지 ERP 등에서만 제한적 사용) | **현대적 MSA 분산 트랜잭션 구현의 사실상 표준** | **Saga 패턴의 신뢰성을 보장하기 위해 함께 사용하는 핵심 인프라 패턴** |


## Out Box 패턴

### 해당 패턴이 푸는 "단 하나의 문제" — Dual Write
> DB와 Kafka는 서로 다른 시스템이라 하나의 원자적 트랜잭션으로 묶을 수 없다는 문제가 있다.
```text
방법 A: DB 먼저
  seat SOLD 커밋 ✅ ──→ Kafka 발행 💥(크래시/네트워크 단절)
  결과: 좌석은 팔렸는데 정산·알림·재고는 모름 → 매출 누락, 푸시 안 감 (이벤트 유실)

방법 B: Kafka 먼저
  Kafka 발행 ✅ ──→ seat SOLD 커밋 💥(DB 롤백)
  결과: 정산·알림은 "팔렸다" 처리했는데 실제 DB엔 안 팔림 (유령 이벤트)
```

### 발상의 전환 — "메시지 발행"을 "DB 쓰기"로 순서를 바꾼다.
> 비즈니스 데이터와 **같은 DB·같은 로컬 트랜잭션 안**에서 outbox 테이블에 **INSERT** 하여 진행
```text
[비즈니스 트랜잭션 — 단일 DB, 원자적]
  ┌────────────────────────────────────┐
  │  UPDATE seat SET status='SOLD'      │
  │  INSERT INTO outbox_event (...)     │   ← 둘이 같이 커밋 (또는 같이 롤백)
  └────────────────────────────────────┘
         │ 커밋 ✅  (seat와 outbox는 절대 어긋나지 않음 — 같은 트랜잭션이니까)
         ▼
  [릴레이 - CDC] outbox 감지 → Kafka 발행 → 발행 완료 표시
         ▼
  [컨슈머] 정산 / 알림 / 재고 서비스가 수신
```

### 릴레이 방식 - CDC
>  CDC — Debezium (실무 표준)
> - DB의 트랜잭션 로그를 Debezium가 실시간으로 tailing -> outbox가 INSERT가 일어나는 순간 자동으로 Kafka 메세지 발행
> - ✏️ 규모가 작을 경우 폴링을 사용할 수도 있다. (실무 표준❌)
```text
앱: INSERT INTO outbox_event (...)   ← 앱은 이것만. Kafka를 전혀 모름
       │
       ▼ (DB가 트랜잭션 로그에 기록)
Debezium (Kafka Connect 위에서 동작)
       │  binlog/WAL을 읽어 outbox INSERT 감지
       ▼
Kafka 토픽으로 자동 발행
```
#### 단점
- 인프라가 무거움: Kafka + Kafka Connect + Debezium 운영 필요
- DB의 binlog/WAL 설정 권한·튜닝 필요
- 운영 난이도↑ (커넥터 장애, 스키마 변경, 로그 보존 등)

### 반드시 알아야 할 특성 — At-least-once & 멱등성

> Outbox는 At-least-once (정확히 1회가 아님)
> - 컨슈머는 반드시 멱등(idempotent)하게 해야함
```text
릴레이: Kafka 발행 ✅ ──→ status='SENT' 표시 직전 크래시 💥
재시작: 그 행은 여전히 status='NEW' → 다시 발행
결과: 같은 이벤트가 Kafka에 2번 들어감
```
- "유실은 없지만(at-least), 중복은 있다(once 보장 안 됨)" 를 잊으면 안된다.
- 분산 시스템에서 진짜 exactly-once는 매우 비싸기에 어렵다.
  - 현실적 표준은 `at-least-once + 멱등 컨슈머` 가 => 실질적 exactly-once 효과로 볼 수 있다.

### 결론 
> "컨슈머 멱등성은 옵션이 아니라 필수" — Outbox 도입 시 가장 먼저 합의해야 할 사항이다.
> - 먹등성을 유지하기 위해 outBox Table에 `aggregateId`를 두고 파티션 키 사용 -> **"순서는 aggregateId 단위로만 보장"**

## Saga 패턴

###  Saga 패턴이 푸는 문제 
> Outbox와 "다른 계층"이며, 이 둘은 경쟁 관계가 아니다. "층위가 달라서 보통 함께 쓰음"
> - Outbox는 메시지 한 건의 발행 신뢰성
> - Saga는 여러 단계로 이뤄진 비즈니스 흐름 전체의 정합성
```text
Outbox 패턴 : "이벤트 1건을 어떻게 안전하게 발행하나?"   (발행 신뢰성 — 점)
Saga 패턴 :   "여러 서비스에 걸친 트랜잭션을 어떻게 정합 유지하나?" (워크플로우 — 선)
```

### Saga 패턴 필수 조건
- **멱등성**: 네트워크 재시도로 같은 단계/보상이 중복 호출될 수 있음 → **각 단계와 보상 모두 멱등**이어야 함 
- **보상 가능성**: 모든 단계가 되돌려질 수 있어야 함. 되돌릴 수 없는 액션(예: "예매 완료 SMS 발송" — 이미 보낸 문자는 못 지움)은 **마지막에 배치**해서 **그 뒤에 실패가 없게 함**
  - 실무 설계 원칙: 되돌릴 수 없는 작업은 가장 마지막에

### Saga의 핵심 — 보상 트랜잭션 (Compensating Transaction)
> 글로벌 트랜잭션을 **독립적인 로컬 트랜잭션들의 체인으로 쪼개고**, 중간에 실패하면 **이미 성공한 단계들을 역순**으로 "보상(되돌리기)" 
> - 핵심은 "진짜 롤백이 아니라 보상" 이라는 점
```text
// 티켓 도메인 예매 예시 사가 패턴 (좌석 → 결제 → 발권):
[정상 흐름]
  ① 좌석 확정 (Seat 서비스: HELD→SOLD)
  ② 결제 (Payment 서비스: 카드 승인)
  ③ 티켓 발권 (Ticket 서비스: 발급)
  → 끝. 성공.

[② 결제까지 됐는데 ③ 발권 실패]
  ③ 실패 💥
  ② 보상: 결제 취소(환불)         ← 역순으로
  ① 보상: 좌석 해제(SOLD→AVAILABLE)
  → 전체적으로 "아무 일 없었던 상태"로 수렴
```

### 두 가지 구현 방식 — Choreography vs Orchestration
> 두가지를 섞어서 구축하는 방법을 많이 사용함.
> - 잃어버리면 문제가큰 트랜잭션 : Orchestration
> - 그외 트랜잭션 : Choreography

#### 코레오그래피 (Choreography) 
> 각 서비스가 이벤트를 발행하고, 다음 서비스가 그걸 구독해서 반응
> - 이벤트가 흐름을 만드는 구조

- 장점 : 서비스 간 결합 느슨, 중앙 병목 없음
- 단점 : 흐름이 코드 어디에도 안 보임 (이벤트 따라 흩어짐) → 단계 많아지면 찾기가 어려워짐

```text
Seat:   SeatSold 이벤트 발행
           ↓ (구독)
Payment: 수신 → 결제 → PaymentApproved 발행
           ↓ (구독)
Ticket:  수신 → 발권 → TicketIssued 발행

실패 시: Ticket이 TicketFailed 발행 → Payment가 구독해 환불 → ...
```

#### 구축 참고 사항
- Message Queue를 사용 다만 성공/실패 에 따른 **topic을 분리하지 않고 한개의 topic에서 처리** 필요
  - 이유 : **"순서 보장(Ordering)"** 때문이다.
```java
@KafkaListener(topics = "payment-events", groupId = "order-payment-group")
public void handlePaymentResult(PaymentEvent event) {
    
    // 결제가 실패한 경우 (보상 트랜잭션 실행)
    if (PAYMENT_FAILED == event.status()) {
        // 1. DB에서 기존 주문 데이터를 조회
        orderRepository.findById(event.orderId()).ifPresentOrElse(order -> {
            // 2. 주문 상태를 PENDING에서 CANCELLED(또는 ORDER_FAILED)로 변경
            order.changeStatus(CANCELLED); 
            orderRepository.save(order); // DB 업데이트
        }, () -> {
            // Logging
        });
    } // if 
    // 결제가 성공한 경우 (다음 정상 프로세스 진행 - 예: 배송 준비 등)
    else if (PAYMENT_COMPLETED == event.status()) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            order.changeStatus(CONFIRMED); // 주문 확정
            orderRepository.save(order);
        });
    } // if - else
}
```

#### 오케스트레이션 (Orchestration) 
> 오케스트레이터가 각 서비스를 순서대로 호출하고, 실패하면 보상을 지시
- 장점 : 흐름이 오케스트레이터에 명시적 → 추적·관리 쉬움
- 단점 : 오케스트레이터에 로직 집중(병목·SPOF 가능성)
```text
Orchestrator:
  → Seat.확정()      OK
  → Payment.결제()   OK
  → Ticket.발권()    실패 💥
  → Payment.환불()   (보상)
  → Seat.해제()      (보상)
```

### Saga의 가장 큰 함정 — 격리성(Isolation) 부재
> 순차적으로 서비스 별로 DB 내용을 변경하기 때문임
> - "커밋된 중간 상태가 외부에 노출된다" <— 해당 내용이 격리성 부재
```text
Seat: SOLD로 변경 ✅ (① 단계 커밋됨)
   │  ← 바로 이 순간, ② 결제는 아직 진행 중
   ▼
다른 사용자/서비스가 이 좌석을 조회 → "SOLD네, 끝난 거래구나" 라고 오해
   │
   ▼
그런데 ③ 발권 실패 → 보상으로 좌석 해제 → 다시 AVAILABLE
   → 방금 "팔렸다"고 본 쪽은 잘못된 중간 상태를 본 것
```

#### 격리성 부재 대응 방법
- **Semantic Lock** :  Table의 컬럼중 중간 상태에 `PENDING/SOLD_PENDING` 같은 진행 중 표시를 둬서, 다른 쪽이 "아직 확정 아님"을 알게 함
- **Commutative Updates**: 순서 무관하게 같은 결과 나오는 연산으로 설계
- **재시도/보상 자체의 멱등성**: 보상이 두 번 실행돼도 안전하게

## 요약
```text
[2PC]   강한 일관성, 동기 블로킹 → 성능👎 , 사실상 더이상 사용하지 않음

[Saga]  여러 서비스 워크플로우 정합 → 보상 트랜잭션으로 역순 취소
        · [구분] : Choreography vs Orchestration
        · 함정: 격리성 부재(중간 상태 노출) → semantic lock(중간 구분 값 추가) 로 막음
        · 필수: 멱등성 + 보상 가능성 (🔍되돌릴 수 없는 작업은 맨 끝에 둬야 함)
        
[Outbox] dual-write 해결 → DB변경 + 발행 의도 원자적 기록, 실제 발행은 at-least-once
        · Saga의 단계 이벤트 발행을 떠받치는 신뢰성 배관

관계: Saga(흐름) ──on top of── Outbox(발행 신뢰성)
```