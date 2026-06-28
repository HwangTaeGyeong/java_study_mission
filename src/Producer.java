import java.util.concurrent.PriorityBlockingQueue;

public class Producer implements Runnable{
	PriorityBlockingQueue<AiRequest> queue;

	public Producer(PriorityBlockingQueue<AiRequest> queue) {
		this.queue = queue;
	}

	private volatile boolean running = true;

	@Override
	public void run() {
		int id = 0;

		while (running) {
			// AiRequest request = new AiRequest();
			int priority = (int)(Math.random() * 10) + 1;	//1~10
			AiRequest request = new AiRequest(id++, "질문내용" + id, priority);
			queue.add(request);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
