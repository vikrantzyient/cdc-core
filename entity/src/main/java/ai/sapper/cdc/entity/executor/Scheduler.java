package ai.sapper.cdc.entity.executor;

import ai.sapper.cdc.common.config.Config;
import ai.sapper.cdc.common.config.ConfigReader;
import ai.sapper.cdc.common.config.Settings;
import ai.sapper.cdc.common.utils.DefaultLogger;
import ai.sapper.cdc.core.BaseEnv;
import ai.sapper.cdc.core.processing.ProcessorState;
import ai.sapper.cdc.entity.model.TransactionId;
import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Getter
@Setter
@Accessors(fluent = true)
public abstract class Scheduler<T extends TransactionId> implements Closeable, Runnable, CompletionCallback<T> {
    private final ProcessorState state = new ProcessorState();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private ReentrantLock __lock = new ReentrantLock();
    private final List<Task<T, ?, ?>> tasks = new ArrayList<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Queue<Task<T, ?, ?>> taskQueue = new LinkedBlockingQueue<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Queue<Future<?>> responseQueue = new LinkedBlockingQueue<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private ThreadPoolExecutor executorService;
    @Setter(AccessLevel.NONE)
    private SchedulerSettings settings;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Thread scheduler;
    @Setter(AccessLevel.NONE)
    private BaseEnv<?> env;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private int maxExecutorQueueSize;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Thread futures;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private ThreadGroup group;

    public Scheduler<T> init(@NonNull HierarchicalConfiguration<ImmutableNode> xmlConfig,
                             @NonNull BaseEnv<?> env) throws ConfigurationException {
        try {
            this.env = env;
            ConfigReader reader = new ConfigReader(xmlConfig, SchedulerSettings.__CONFIG_PATH, SchedulerSettings.class);
            reader.read();
            settings = (SchedulerSettings) reader.settings();

            maxExecutorQueueSize = settings.getMaxPoolSize() * 4;
            executorService = new ThreadPoolExecutor(
                    settings.getCorePoolSize(),
                    settings.getMaxPoolSize(),
                    settings.getKeepAliveTime(),
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(maxExecutorQueueSize)
            );
            state.setState(ProcessorState.EProcessorState.Initialized);
            return this;
        } catch (Exception ex) {
            state.error(ex);
            throw new ConfigurationException(ex);
        }
    }

    public void start() throws Exception {
        if (state.isRunning()) return;
        if (!state.isInitialized()) {
            throw new Exception(String.format("Scheduler not in initialized state. [state=%s]", state.getState().name()));
        }
        state.setState(ProcessorState.EProcessorState.Running);
        __lock.lock();
        try {
            for (Task<T, ?, ?> task : tasks) {
                queue(task, ETaskState.WAITING);
            }
            group = new ThreadGroup("SCHEDULER");
            scheduler = new Thread(group, this, "MAIN");
            scheduler.start();
            futures = new Thread(group, () -> {
                while (state.isRunning()) {
                    while (true) {
                        try {
                            Future<?> response = responseQueue.poll();
                            if (response == null) break;
                            Object o = response.get();
                            if (o != null) {
                                DefaultLogger.warn(
                                        String.format("Response is not null. [type=%s]", o.getClass().getCanonicalName()));
                            }
                        } catch (Exception ex) {
                            DefaultLogger.stacktrace(ex);
                            DefaultLogger.error(ex.getLocalizedMessage());
                        }
                    }
                    synchronized (responseQueue) {
                        try {
                            responseQueue.wait(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            System.err.println("Thread Interrupted");
                        }
                    }
                }
            }, "FUTURES");
            futures.start();
        } finally {
            __lock.unlock();
        }
    }

    public void stop() {
        __lock.lock();
        try {
            if (state.isRunning()) {
                state.setState(ProcessorState.EProcessorState.Stopped);
            }
            if (executorService != null) {
                try {
                    executorService.shutdown();
                    while (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        DefaultLogger.info("Waiting another 5 seconds for the embedded engine to shut down");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    DefaultLogger.error(
                            String.format("Error terminating executors: [%s]", e.getLocalizedMessage()));
                }
                executorService = null;
            }
            scheduler.join();
            futures.join();
        } catch (Exception ex) {
            DefaultLogger.stacktrace(ex);
            DefaultLogger.error(ex.getLocalizedMessage());
        } finally {
            __lock.unlock();
        }
    }

    public void add(@NonNull Task<T, ?, ?> task) {
        Preconditions.checkArgument(state.isInitialized() || state.isRunning());
        __lock.lock();
        try {
            tasks.add(task.withCallback(this));
            if (state.isRunning())
                queue(task, ETaskState.INITIALIZED);
        } finally {
            __lock.unlock();
        }
    }

    private void queue(Task<T, ?, ?> task, ETaskState taskState) {
        if (!state.isRunning()) return;
        while (true) {
            task.state().setState(taskState);
            if (!taskQueue.offer(task)) {
                synchronized (taskQueue) {
                    try {
                        taskQueue.wait(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Thread Interrupted");
                    }
                }
            } else {
                synchronized (taskQueue) {
                    taskQueue.notifyAll();
                }
                break;
            }
        }
    }

    /**
     * @param task
     */
    @Override
    public void finished(@NonNull Task<T, ?, ?> task) {
        // DefaultLogger.LOGGER.debug(String.format("Finished task: [id=%s]", task.id()));
        queue(task, ETaskState.WAITING);
        notifyFinished();
    }

    /**
     * @param task
     * @param error
     */
    @Override
    public void error(@NonNull Task<T, ?, ?> task, @NonNull Throwable error) {
        //DefaultLogger.LOGGER.debug(String.format("Finished task with error: [id=%s]", task.id()));
        if (error instanceof FatalError) {
            try {
                stop();
            } catch (Exception ex) {
                DefaultLogger.stacktrace(ex);
                DefaultLogger.error(ex.getLocalizedMessage());
            }
        } else {
            task.state().error(error);
            notifyFinished();
        }
    }

    private void notifyFinished() {
        synchronized (taskQueue) {
            taskQueue.notifyAll();
        }
        synchronized (responseQueue) {
            responseQueue.notifyAll();
        }
    }

    /**
     *
     */
    @Override
    public void run() {
        try {
            while (state.isRunning()) {
                int size = maxExecutorQueueSize - executorService.getQueue().size();
                while (size > 0) {
                    Task<T, ?, ?> task = taskQueue.poll();
                    if (task == null) break;
                    synchronized (taskQueue) {
                        taskQueue.notifyAll();
                    }
                    boolean execute = false;
                    ETaskState taskState = ETaskState.UNKNOWN;
                    if (task.state().getState() == ETaskState.INITIALIZED) {
                        taskState = ETaskState.INITIALIZED;
                        execute = true;
                    } else if (task.state().getState() == ETaskState.ERROR) {
                        continue;
                    } else if (task.state().getState() == ETaskState.STOPPED) {
                        tasks.remove(task);
                        continue;
                    } else {
                        if (schedule(task)) {
                            execute = true;
                        } else {
                            queue(task, ETaskState.WAITING);
                        }
                    }
                    if (execute) {
                        size--;
                        //DefaultLogger.LOGGER.debug(String.format("Scheduling task. [id=%s]", task.id()));
                        if (!submit(task)) {
                            queue(task, taskState);
                            break;
                        }
                    }
                }
                synchronized (taskQueue) {
                    try {
                        taskQueue.wait(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Thread Interrupted");
                    }
                }
            }
        } catch (Exception ex) {
            state.error(ex);
            DefaultLogger.stacktrace(ex);
            DefaultLogger.error(ex.getLocalizedMessage());
        } finally {
            try {
                close();
            } catch (Exception ex) {
                DefaultLogger.stacktrace(ex);
                DefaultLogger.error(ex.getLocalizedMessage());
            }
        }
    }

    public abstract boolean schedule(@NonNull Task<T, ?, ?> task);

    private boolean submit(Task<T, ?, ?> task) {
        try {
            task.state().setState(ETaskState.QUEUED);
            Future<?> response = executorService.submit(task);
            responseQueue.add(response);
        } catch (RejectedExecutionException re) {
            return false;
        }
        return true;
    }
}
