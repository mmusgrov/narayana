package demo.verticle;

public class ServiceResult {
    private String serviceName;
    private String threadId;
    private int activityCount;

    public ServiceResult(String serviceName, String threadId, int activityCount) {
        this.serviceName = serviceName;
        this.threadId = threadId;
        this.activityCount = activityCount;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getThreadId() {
        return threadId;
    }

    public int getActivityCount() {
        return activityCount;
    }
}
