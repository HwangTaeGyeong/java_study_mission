# java_study_mission

# 🤖 고동시성 AI 요청 비동기 스케줄링 큐

> 멀티스레드 환경에서 AI API Rate Limit을 지수 백오프로 처리하는 우선순위 기반 스케줄러

---

## 📌 미션 요약

외부 AI API의 Rate Limit(분당 요청 제한)이 걸린 상황에서,  
멀티스레드로 쏟아지는 유저 요청을 **우선순위 큐**로 정렬하고,  
**데드락 없이** 안전하게 소비하는 **지수 백오프 기반 스케줄러** 구현.

---

## 🧠 핵심 개념

### 1. 큐잉 (Queuing)
요청을 바로 처리 못할 때 줄 세워두고 순서대로 꺼내 처리하는 구조.  
우선순위가 높은 요청일수록 먼저 처리되며, 같은 우선순위면 먼저 들어온 순서로 처리.

### 2. 데드락 (Deadlock)
두 스레드가 서로 상대방이 가진 락을 기다리며 영원히 멈추는 상태.

```
스레드 A: queueLock 보유 → rateLimitLock 대기
스레드 B: rateLimitLock 보유 → queueLock 대기
→ 둘 다 영원히 멈춤
```

**방지법: 락 순서 고정 (Lock Ordering)**  
모든 스레드가 항상 동일한 순서로 락을 잡도록 컨벤션으로 강제.  
`PriorityBlockingQueue`는 내부적으로 thread-safe하게 설계되어 있어 별도 락 없이 사용 가능.

### 3. 지수 백오프 (Exponential Backoff)
API가 429(Rate Limit 초과)를 반환할 때, 실패 횟수에 따라 대기 시간을 2배씩 늘려 재시도.

```
대기시간 = 1000ms × 2^(실패횟수)

1번 실패 → 1초 대기
2번 실패 → 2초 대기
3번 실패 → 4초 대기
4번 실패 → 8초 대기
최대 5번 초과 시 해당 요청 포기
```

---

## 🏗️ 전체 구조

```
Producer → PriorityBlockingQueue → Worker → Gemini API
                                     ↑
                              실패 시 Backoff 재시도
```

| 클래스 | 역할 |
|--------|------|
| `AiRequest` | 요청 데이터 그릇 (id, content, priority, timestamp) |
| `Producer` | 요청을 생성해서 큐에 넣는 스레드 |
| `Worker` | 큐에서 꺼내 Gemini API 호출 + Backoff 처리 |
| `Scheduler` | 큐 생성 후 Producer/Worker 조립 및 실행 |
| `RateLimitException` | 429 응답 시 발생하는 커스텀 예외 |

---

## 💻 주요 코드

### AiRequest - 우선순위 정렬
```java
class AiRequest implements Comparable<AiRequest> {
    int id;
    String content;
    int priority;
    long timestamp;

    @Override
    public int compareTo(AiRequest other) {
        if (this.priority == other.priority) {
            return Long.compare(this.timestamp, other.timestamp); // 같은 우선순위면 먼저 들어온 순
        }
        return other.priority - this.priority; // 숫자 높을수록 우선순위 높음
    }
}
```

### Producer - 요청 생성 및 큐 삽입
```java
public void run() {
    int id = 0;
    while (running) {
        int priority = (int)(Math.random() * 10) + 1;
        AiRequest request = new AiRequest(id++, "질문내용" + id, priority);
        queue.add(request);
        Thread.sleep(500); // 과부하 방지
    }
}
```

### Worker - API 호출 + 지수 백오프
```java
public void run() {
    while (running) {
        AiRequest request = queue.take(); // 큐가 빌 때까지 대기
        int retryCount = 0;

        while (true) {
            try {
                // Gemini API 호출
                HttpResponse<String> response = client.send(httpRequest, ...);

                if (response.statusCode() == 429) throw new RateLimitException("Rate limit 초과");

                retryCount = 0;
                System.out.println("응답: " + response.body());
                break;

            } catch (RateLimitException e) {
                if (retryCount >= MAX_RETRY) { break; } // 최대 재시도 초과 시 포기
                long waitTime = 1000L * (long) Math.pow(2, retryCount++);
                Thread.sleep(waitTime); // 지수 백오프 대기
            }
        }
    }
}
```

### Scheduler - 전체 조립
```java
public static void main(String[] args) {
    PriorityBlockingQueue<AiRequest> queue = new PriorityBlockingQueue<>();
    Producer producer = new Producer(queue);
    Worker worker = new Worker(queue);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    executor.submit(producer);
    executor.submit(worker);
}
```

---

## 🔑 환경 설정

Gemini API 키 발급: [Google AI Studio](https://aistudio.google.com)

**IntelliJ 환경변수 설정**
```
Run > Edit Configurations > Environment variables
GEMINI_API_KEY=발급받은_키_입력
```

---

## 📚 배운 점

- `PriorityBlockingQueue`는 thread-safe하여 멀티스레드 환경에서 별도 락 없이 사용 가능
- `take()`는 큐가 빌 때 자동으로 대기 (`poll()`은 null 반환 후 바로 넘어감)
- `volatile`은 변수 변경을 모든 스레드가 즉시 감지하도록 메인 메모리에서 읽도록 강제
- `Runnable`이 `Thread` 상속보다 유연 (Java는 단일 상속만 가능하기 때문)
