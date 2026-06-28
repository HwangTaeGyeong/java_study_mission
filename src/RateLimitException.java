public class RateLimitException extends RuntimeException {
	RateLimitException(String message) {
		super(message);
	}
}
