import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.PriorityBlockingQueue;

public class Worker implements Runnable {
	private static final int MAX_RETRY = 5;
	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))  // 버그1 수정: 커넥션 타임아웃
		.build();

	PriorityBlockingQueue<AiRequest> queue;
	String apiKey = System.getenv("GEMINI_API_KEY");

	public Worker(PriorityBlockingQueue<AiRequest> queue) {
		this.queue = queue;
	}

	private volatile boolean running = true;

	@Override
	public void run() {
		while (running) {
			AiRequest request;
			try {
				request = queue.take();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}

			int retryCount = 0;  // 버그2 수정: retryCount를 요청 단위로 초기화

			while (true) {  // 버그2 수정: 같은 request를 재시도하는 내부 루프
				try {
					HttpRequest httpRequest = HttpRequest.newBuilder()
						.uri(URI.create("https://generativelanguage.googleapis.com/v1beta/interactions"))
						.header("Content-Type", "application/json")
						.header("x-goog-api-key", apiKey)
						.timeout(Duration.ofSeconds(10))  // 버그1 수정: 요청 타임아웃
						.POST(HttpRequest.BodyPublishers.ofString(
							"{\"model\": \"gemini-3.5-flash\", \"input\": \"" + request.content + "\"}"
						))
						.build();

					HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

					if (response.statusCode() == 429) {
						throw new RateLimitException("Rate limit 초과");
					}

					System.out.println("응답 [요청" + request.id + "]: " + response.body());
					break;  // 성공 시 재시도 루프 탈출

				} catch (RateLimitException e) {
					if (retryCount >= MAX_RETRY) {
						System.out.println("최대 재시도 초과, 요청 포기: " + request.id);
						break;
					}
					long waitTime = 1000L * (long) Math.pow(2, retryCount++);
					System.out.println("Rate limit, " + waitTime + "ms 후 재시도 [" + retryCount + "/" + MAX_RETRY + "]");
					try {
						Thread.sleep(waitTime);
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						return;
					}

				} catch (IOException e) {
					System.out.println("IO 에러 [요청" + request.id + "]: " + e.getMessage());
					break;

				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}
}
