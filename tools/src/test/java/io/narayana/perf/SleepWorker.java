package io.narayana.perf;

public class SleepWorker implements Worker {
    @Override
    public Object doWork(Object context, int batchSize, Measurement config) {
        for (int i = 0; i < batchSize; i++) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                config.incrementErrorCount(1);
            }
        }
        return null;
    }

    @Override
    public Object doWork(Object context, int batchSize, Result config) {
        for (int i = 0; i < batchSize; i++) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                config.incrementErrorCount();
            }
        }
        return null;
    }

    @Override
    public void init() {}

    @Override
    public void fini() {}
}
