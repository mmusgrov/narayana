package demo.verticle;

public class ServiceResult {
    private String verticleName;
    private String threadId;
    private int activityCount;

    public ServiceResult(String verticleName, String threadId, int activityCount) {
        this.verticleName = verticleName;
        this.threadId = threadId;
        this.activityCount = activityCount;
    }

    public String getVerticleName() {
        return verticleName;
    }

    public String getThreadId() {
        return threadId;
    }

    public int getActivityCount() {
        return activityCount;
    }
}
