package org.apd.executor;

import org.apd.storage.EntryResult;
import org.apd.storage.SharedDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

/* DO NOT MODIFY THE METHODS SIGNATURES */
public class TaskExecutor {
    private final SharedDatabase sharedDatabase;

    public TaskExecutor(int storageSize, int blockSize, long readDuration, long writeDuration) {
        sharedDatabase = new SharedDatabase(storageSize, blockSize, readDuration, writeDuration);
    }

    public List<EntryResult> ExecuteWork(int numberOfThreads, List<StorageTask> tasks, LockType lockType) {
        List<EntryResult> results = Collections.synchronizedList(new ArrayList<>());
        ThreadPool threadPool = new ThreadPool(numberOfThreads);

        final int storageSize = sharedDatabase.getSize();
        final Semaphore[] readWrite = new Semaphore[storageSize];
        final Semaphore[] mutexNumberOfReaders = new Semaphore[storageSize];
        final int[] readers = new int[storageSize];
        final Semaphore[] sem_writer = new Semaphore[storageSize];
        final Semaphore[] sem_reader = new Semaphore[storageSize];
        final int[] writers = new int[storageSize];
        final int[] waiting_writers = new int[storageSize];
        final int[] waiting_readers = new int[storageSize];
        final Semaphore[] enter = new Semaphore[storageSize];

        for (int i = 0; i < storageSize; i++) {
            readWrite[i] = new Semaphore(1);
            mutexNumberOfReaders[i] = new Semaphore(1);
            readers[i] = 0;
            sem_writer[i] = new Semaphore(0);
            sem_reader[i] = new Semaphore(0);
            writers[i] = 0;
            waiting_writers[i] = 0;
            waiting_readers[i] = 0;
            enter[i] = new Semaphore(1);
        }

        tasks.forEach(task -> threadPool.addTask(() -> {
            try {
                int index = task.index();
                if (lockType == LockType.ReaderPreferred) {
                    if (task.isWrite()) {
                        readWrite[index].acquire();
                        try {
                            EntryResult result = sharedDatabase.addData(index, task.data());
                            results.add(result);
                        } finally {
                            readWrite[index].release();
                        }
                    } else {
                        mutexNumberOfReaders[index].acquire();
                        readers[index]++;
                        if (readers[index] == 1) {
                            readWrite[index].acquire();
                        }
                        mutexNumberOfReaders[index].release();
                        try {
                            EntryResult result = sharedDatabase.getData(index);
                            results.add(result);
                        } finally {
                            mutexNumberOfReaders[index].acquire();
                            readers[index]--;
                            if (readers[index] == 0) {
                                readWrite[index].release();
                            }
                            mutexNumberOfReaders[index].release();
                        }
                    }
                } else if (lockType == LockType.WriterPreferred1) {
                    if (task.isWrite()) {
                        enter[index].acquire();

                        if (readers[index] > 0 || writers[index] > 0) {
                            waiting_writers[index]++;
                            enter[index].release();
                            sem_writer[index].acquire();
                        }

                        writers[index]++;
                        enter[index].release();

                        try {
                            EntryResult result = sharedDatabase.addData(index, task.data());
                            results.add(result);
                        } finally {
                            enter[index].acquire();
                            writers[index]--;

                            if (waiting_writers[index] > 0) {
                                waiting_writers[index]--;
                                sem_writer[index].release();
                            } else if (waiting_readers[index] > 0) {
                                waiting_readers[index]--;
                                sem_reader[index].release();
                            } else {
                                enter[index].release();
                            }
                        }
                    } else {
                        enter[index].acquire();

                        if (writers[index] > 0 || waiting_writers[index] > 0) {
                            waiting_readers[index]++;
                            enter[index].release();
                            sem_reader[index].acquire();
                        }

                        readers[index]++;
                        if (waiting_readers[index] > 0) {
                            waiting_readers[index]--;
                            sem_reader[index].release();
                        } else {
                            enter[index].release();
                        }

                        try {
                            EntryResult result = sharedDatabase.getData(index);
                            results.add(result);
                        } finally {
                            enter[index].acquire();
                            readers[index]--;
                            if (readers[index] == 0 && waiting_writers[index] > 0) {
                                waiting_writers[index]--;
                                sem_writer[index].release();
                            } else {
                                enter[index].release();
                            }
                        }
                    }
                } else if (lockType == LockType.WriterPreferred2) {
                    if (task.isWrite()) {
                        try {
                            synchronized (enter[index]) {
                                while (readers[index] > 0 || writers[index] > 0) {
                                    waiting_writers[index]++;
                                    enter[index].wait();
                                    waiting_writers[index]--;
                                }
                                writers[index]++;
                            }

                            EntryResult result = sharedDatabase.addData(index, task.data());
                            results.add(result);

                        } finally {
                            synchronized (enter[index]) {
                                writers[index]--;

                                if (waiting_writers[index] > 0) {
                                    enter[index].notifyAll();
                                } else if (waiting_readers[index] > 0) {
                                    enter[index].notifyAll();
                                }
                            }
                        }
                    } else {
                        try {
                            synchronized (enter[index]) {
                                while (writers[index] > 0 || waiting_writers[index] > 0) {
                                    waiting_readers[index]++;
                                    enter[index].wait();
                                    waiting_readers[index]--;
                                }
                                readers[index]++;
                            }

                            EntryResult result = sharedDatabase.getData(index);
                            results.add(result);

                        } finally {
                            synchronized (enter[index]) {
                                readers[index]--;

                                if (readers[index] == 0 && waiting_writers[index] > 0) {
                                    enter[index].notifyAll();
                                }
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Task interrupted: " + e.getMessage());
            }
        }));
        threadPool.stop();
        return results;
    }
}
