import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

public class Scheduler {
	public static void main(String[] args) {
		// System.out.println("API KEY: " + System.getenv("GEMINI_API_KEY"));

		PriorityBlockingQueue<AiRequest> queue = new PriorityBlockingQueue<>();
		Producer producer = new Producer(queue);
		Worker worker = new Worker(queue);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		executor.submit(producer);
		executor.submit(worker);
	}
}
