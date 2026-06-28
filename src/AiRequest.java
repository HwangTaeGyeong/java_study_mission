class AiRequest implements Comparable<AiRequest> {
	int id;
	String content;
	int priority;
	long timestamp;

	public AiRequest(int id, String content, int priority) {
		this.id = id;
		this.content = content;
		this.priority = priority;
		this.timestamp = System.currentTimeMillis();
	}

	@Override
	public int compareTo(AiRequest other) {
		if (this.priority == other.priority) {
			return Long.compare(this.timestamp, other.timestamp);  // 버그3 수정: long 오버플로 방지
		}
		return other.priority - this.priority;  // 버그4 수정: 높은 숫자(=VIP)가 먼저 처리
	}
}
