# Redis Pub/Sub
> 메세지를 받아야하는 서버가 이중화 되어야할 경우 활용
> - 단일 서버일 경우 굳이 Reids의 Pub/Sub의 강점이 없다 In-Memory 방식으로 충분히 가능

## Redis Pub/Sub 개념
> Pub/Sub 서로 같은 메세지를 받고 싶다면 양쪽 다 같은 "채널"을 바라보게 해야함
> - Redis가 메시지 브로커 역할 진행
> - Publisher와 Subscriber는 서로를 모릅니다. Channel만 알면 된다.
```text
Publisher(발행자)가 메시지를 보내면
Subscriber(구독자)가 실시간으로 수신하는 메시징 패턴

  Publisher → Channel → Subscriber A
                     → Subscriber B
                     → Subscriber C
```

## 흐름
> Kafka와 다르게 선행해서 Topic을 생성할 필요 없이 구독자/생성자 둘중 어느쪽이든 먼저 채널을 지정하면 **그 채널 이름으로 라우팅이 시작**된다.
> - 구독 정보(subscription)가 없어지면 Redis가 더 이상 그 채널을 관리하지 않기에 휘발된다.
```text
```text
[1단계] 구독자가 채널(토픽) 구독
  SUBSCRIBE {{채널}}              # 단일 채널 구독
  
  
  PSUBSCRIBE {{패턴}}             # 패턴(와일드카드)으로 여러 채널 한 번에 구독 또한 가능하다 (지정도 가능)
                                 # 예) PSUBSCRIBE chat.*  → chat.room1, chat.room2 ... 모두 수신

[2단계] 발행자가 지정 채널에 메시지 발행
  PUBLISH {{채널}} {{payload}}

[3단계] Redis가 구독자 전원에게 메시지 전달
  → Subscriber A 수신
  → Subscriber B 수신
  → ...~ Z 수신
```

## Kafka와 Redis Pub/Sub 비교
| 항목       | Redis Pub/Sub        | Kafka                 |
| -------- | -------------------- | --------------------- |
| 메시지 보존   | ❌ 발행 즉시 소멸           | ✅ 디스크에 보존 (Retention) |
| 구독자 부재 시 | 메시지 유실               | 나중에 소비 가능             |
| 전달 보장    | at-most-once (최대 1번) | 기본 at-least-once, 설정 시 exactly-once 가능 |
| 적합한 용도      | 가벼운 실시간 알림에 적합       | 대규모 이벤트 스트림에 적합       |
| 소비자 그룹   | ❌ 없음                 | ✅ Consumer Group 지원   |
| 복잡도      | 단순                   | 높음                    |

### 요약
* **Redis Pub/Sub**
    * 실시간 채팅, 알림, 간단한 이벤트 브로드캐스트에 적합
    * 메시지를 저장하지 않기 때문에 빠르고 단순함
    * 구독자가 연결되어 있지 않으면 메시지를 받을 수 없음
* **Kafka**
    * 로그 수집, 이벤트 스트리밍, 대규모 비동기 처리에 적합
    * 메시지를 디스크에 저장하므로 재처리 가능
    * Consumer Group 기반으로 확장성과 안정성이 높음

## Redis Sub/Pub 한계 및 대안
- 한계 : Redis Pub/Sub의 가장 큰 한계는 **메시지가 휘발된다**는 것이다.
    - 구독자가 연결되어 있지 않으면 그 사이 발행된 메시지는 영원히 받을 수 없다 (at-most-once).
    - Consumer Group이 없어 "누가 어디까지 처리했는가"를 추적할 수 없다.
- 대안 : Redis 기반 메시징이 필요하면 **Streams**를 쓰는 추세
    - 메시지를 **로그 형태로 보존** → 구독자가 늦게 붙어도 과거 메시지를 다시 읽을 수 있다 (휘발성 해결)
    - **Consumer Group 지원** → 여러 소비자가 메시지를 나눠서 처리 (분산 처리)
    - **ACK 기반 처리** → 처리 완료(XACK)를 명시, 미처리(PEL) 메시지는 재처리 가능 (유실 방지)
    - 즉, Pub/Sub의 "휘발성 + Consumer Group 부재"를 한 번에 메우는 선택지

| 항목 | Redis Pub/Sub | Redis Streams |
| --- | --- | --- |
| 메시지 보존 | ❌ 발행 즉시 소멸 | ✅ 로그로 보존 |
| 구독자 부재 시 | 유실 | 나중에 읽기 가능 |
| Consumer Group | ❌ 없음 | ✅ 지원 |
| 재처리 | ❌ 불가 | ✅ ACK/PEL 기반 가능 |