package org.apd.executor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadPool {
    private final int numberOfThreads;
    private final PoolWorker[] workers;
    private final BlockingQueue<Runnable> taskQueue;
    private AtomicBoolean isStopped = new AtomicBoolean(false);

    public ThreadPool(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        this.taskQueue = new LinkedBlockingQueue<>();
        this.workers = new PoolWorker[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++) {
            workers[i] = new PoolWorker();
            workers[i].start();
        }
    }

    public synchronized void addTask(Runnable task) {
        if (isStopped.get()) throw new IllegalStateException("ThreadPool has been stopped.");
        taskQueue.add(task);
    }

    public synchronized void stop() {
        isStopped = new AtomicBoolean(true);
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                worker.interrupt();
            }
        }
    }

    private class PoolWorker extends Thread {
        public void run() {
            while (!isStopped.get() || !taskQueue.isEmpty()) {
                try {
                    Runnable task = taskQueue.take();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
