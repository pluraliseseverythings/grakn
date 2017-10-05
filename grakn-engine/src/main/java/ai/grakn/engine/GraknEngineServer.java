/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */
package ai.grakn.engine;

import static ai.grakn.engine.GraknEngineConfig.QUEUE_CONSUMERS;
import static ai.grakn.engine.GraknEngineConfig.REDIS_HOST;
import static ai.grakn.engine.GraknEngineConfig.REDIS_POOL_SIZE;
import static ai.grakn.engine.GraknEngineConfig.REDIS_SENTINEL_HOST;
import static ai.grakn.engine.GraknEngineConfig.REDIS_SENTINEL_MASTER;
import ai.grakn.engine.controller.CommitLogController;
import ai.grakn.engine.controller.ConceptController;
import ai.grakn.engine.controller.DashboardController;
import ai.grakn.engine.controller.GraqlController;
import ai.grakn.engine.controller.SystemController;
import ai.grakn.engine.controller.TasksController;
import ai.grakn.engine.controller.api.AttributeController;
import ai.grakn.engine.controller.api.RelationshipTypeController;
import ai.grakn.engine.controller.api.RoleController;
import ai.grakn.engine.controller.api.RuleController;
import ai.grakn.engine.controller.exception.GraknBackendExceptionMapper;
import ai.grakn.engine.controller.exception.GraknServerExceptionMapper;
import ai.grakn.engine.controller.exception.GraknTxOperationExceptionMapper;
import ai.grakn.engine.controller.exception.GraqlQueryExceptionMapper;
import ai.grakn.engine.controller.exception.GraqlSyntaxExceptionMapper;
import ai.grakn.engine.controller.exception.InvalidKBExceptionMapper;
import ai.grakn.engine.data.RedisWrapper;
import ai.grakn.engine.data.RedisWrapper.Builder;
import ai.grakn.engine.factory.EngineGraknTxFactory;
import ai.grakn.engine.lock.JedisLockProvider;
import ai.grakn.engine.lock.LockProvider;
import ai.grakn.engine.lock.ProcessWideLockProvider;
import ai.grakn.engine.session.RemoteSession;
import ai.grakn.engine.tasks.connection.RedisCountStorage;
import ai.grakn.engine.tasks.manager.StandaloneTaskManager;
import ai.grakn.engine.tasks.manager.TaskManager;
import ai.grakn.engine.tasks.manager.redisqueue.RedisTaskManager;
import ai.grakn.engine.util.EngineID;
import static ai.grakn.util.ErrorMessage.VERSION_MISMATCH;
import ai.grakn.util.GraknVersion;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import static com.codahale.metrics.MetricRegistry.name;
import com.codahale.metrics.jvm.CachedThreadStatesGaugeSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.google.common.base.Stopwatch;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.websockets.WebsocketBundle;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import static org.apache.commons.lang.exception.ExceptionUtils.getFullStackTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;

/**
 * Main class in charge to start a web server and all the REST controllers.
 *
 * @author Marco Scoppetta
 */
public class GraknEngineServer extends Application<GraknEngineServerConfiguration> implements AutoCloseable {

    private static final String REDIS_VERSION_KEY = "info:version";

    private static final String LOAD_SYSTEM_SCHEMA_LOCK_NAME = "load-system-schema";
    private static final Logger LOG = LoggerFactory.getLogger(GraknEngineServer.class);

    private final GraknEngineConfig prop;
    private final EngineID engineId = EngineID.me();
    private final TaskManager taskManager;
    private final EngineGraknTxFactory factory;
    private final MetricRegistry metricRegistry;
    private final LockProvider lockProvider;
    private final GraknEngineStatus graknEngineStatus = new GraknEngineStatus();
    private final RedisWrapper redisWrapper;
    private final ExecutorService taskExecutor;

    public GraknEngineServer(GraknEngineConfig prop, RedisWrapper redisWrapper) {
        this.prop = prop;
        // Metrics
        this.metricRegistry = new MetricRegistry();
        // Redis connection pool
        this.redisWrapper = redisWrapper;
        // Lock provider
        String taskManagerClassName = prop.getProperty(GraknEngineConfig.TASK_MANAGER_IMPLEMENTATION);
        boolean inMemoryQueue = !taskManagerClassName.contains("RedisTaskManager");
        this.lockProvider = inMemoryQueue ? new ProcessWideLockProvider()
                : new JedisLockProvider(redisWrapper.getJedisPool());
        this.factory = EngineGraknTxFactory.create(prop.getProperties());
        // Task manager
        this.taskManager = makeTaskManager(inMemoryQueue, redisWrapper.getJedisPool(), lockProvider);
        this.taskExecutor = TasksController.taskExecutor();
    }

    public GraknEngineServer() {
        this(GraknEngineConfig.create());
    }

    public GraknEngineServer(GraknEngineConfig graknEngineConfig) {
        this(graknEngineConfig, instantiateRedis(graknEngineConfig));
    }

    public static GraknEngineServer create(GraknEngineConfig prop) {
        return create(prop, instantiateRedis(prop));
    }

    public static GraknEngineServer create(GraknEngineConfig prop, RedisWrapper redisWrapper) {
        return new GraknEngineServer(prop, redisWrapper);
    }

    public void start() {
        redisWrapper.testConnection();
        LOG.info("Starting task manager {}", taskManager.getClass().getCanonicalName());
        taskManager.start();
        Stopwatch timer = Stopwatch.createStarted();
        logStartMessage(
                prop.getProperty(GraknEngineConfig.SERVER_HOST_NAME),
                prop.getProperty(GraknEngineConfig.SERVER_PORT_NUMBER));
        synchronized (this){
            checkVersion();
            lockAndInitializeSystemSchema();
        }
        graknEngineStatus.setReady(true);
        LOG.info("Grakn started in {}", timer.stop());
    }

    private void checkVersion() {
        Jedis jedis = redisWrapper.getJedisPool().getResource();
        String storedVersion = jedis.get(REDIS_VERSION_KEY);
        if (storedVersion == null) {
            jedis.set(REDIS_VERSION_KEY, GraknVersion.VERSION);
        } else if (!storedVersion.equals(GraknVersion.VERSION)) {
            LOG.warn(VERSION_MISMATCH.getMessage(GraknVersion.VERSION, storedVersion));
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            stopTaskManager();
            redisWrapper.close();
            taskExecutor.shutdown();
        }
    }

    private void lockAndInitializeSystemSchema() {
        try {
            Lock lock = lockProvider.getLock(LOAD_SYSTEM_SCHEMA_LOCK_NAME);
            if (lock.tryLock(60, TimeUnit.SECONDS)) {
                loadAndUnlock(lock);
            } else {
                LOG.info("{} found system schema lock already acquired by other engine", this.engineId);
            }
        } catch (InterruptedException e) {
            LOG.warn("{} was interrupted while initializing system schema", this.engineId);
        }
    }

    private void loadAndUnlock(Lock lock) {
        try {
            LOG.info("{} is checking the system schema", this.engineId);
            factory.systemKeyspace().loadSystemSchema();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check in with the properties file to decide which type of task manager should be started
     * and return the TaskManager
     * @param inMemoryQueue         True if running in memory
     * @param jedisPool
     */
    private TaskManager makeTaskManager(
            final boolean inMemoryQueue,
            final Pool<Jedis> jedisPool,
            final LockProvider lockProvider) {
        TaskManager taskManager;
        metricRegistry.register(name(GraknEngineServer.class, "jedis", "idle"),
                (Gauge<Integer>) jedisPool::getNumIdle);
        metricRegistry.register(name(GraknEngineServer.class, "jedis", "active"),
                (Gauge<Integer>) jedisPool::getNumActive);
        metricRegistry.register(name(GraknEngineServer.class, "jedis", "waiters"),
                (Gauge<Integer>) jedisPool::getNumWaiters);
        metricRegistry
                .register(name(GraknEngineServer.class, "jedis", "borrow_wait_time_ms", "max"),
                        (Gauge<Long>) jedisPool::getMaxBorrowWaitTimeMillis);
        metricRegistry
                .register(name(GraknEngineServer.class, "jedis", "borrow_wait_time_ms", "mean"),
                        (Gauge<Long>) jedisPool::getMeanBorrowWaitTimeMillis);

        metricRegistry.register(name(GraknEngineServer.class, "System", "gc"),
                new GarbageCollectorMetricSet());
        metricRegistry.register(name(GraknEngineServer.class, "System", "threads"),
                new CachedThreadStatesGaugeSet(15, TimeUnit.SECONDS));
        metricRegistry.register(name(GraknEngineServer.class, "System", "memory"),
                new MemoryUsageGaugeSet());

        if (!inMemoryQueue) {
            Optional<String> consumers = prop.tryProperty(QUEUE_CONSUMERS);
            taskManager = consumers
                    .map(s -> new RedisTaskManager(engineId, prop, jedisPool, Integer.parseInt(s),
                            factory, lockProvider, metricRegistry))
                    .orElseGet(() -> new RedisTaskManager(engineId, prop, jedisPool, factory,
                            lockProvider, metricRegistry));
        } else {
            LOG.info("Task queue in memory");
            // Redis storage for counts, in the RedisTaskManager it's created in consumers
            RedisCountStorage redisCountStorage = RedisCountStorage
                    .create(jedisPool, metricRegistry);
            taskManager = new StandaloneTaskManager(engineId, prop, redisCountStorage, factory,
                    lockProvider, metricRegistry);
        }
        return taskManager;
    }

    private void stopTaskManager() {
        try {
            taskManager.close();
        } catch (Exception e){
            LOG.error(getFullStackTrace(e));
        }
    }

    public TaskManager getTaskManager(){
        return taskManager;
    }

    public EngineGraknTxFactory factory() {
        return factory;
    }

    public GraknEngineStatus getGraknEngineStatus() {
        return graknEngineStatus;
    }

    private static RedisWrapper instantiateRedis(GraknEngineConfig prop) {
        List<String> redisUrl = GraknEngineConfig.parseCSValue(prop.tryProperty(REDIS_HOST).orElse("localhost:6379"));
        List<String> sentinelUrl = GraknEngineConfig.parseCSValue(prop.tryProperty(REDIS_SENTINEL_HOST).orElse(""));
        int poolSize = prop.tryIntProperty(REDIS_POOL_SIZE, 32);
        boolean useSentinel = !sentinelUrl.isEmpty();
        Builder builder = RedisWrapper.builder()
                .setUseSentinel(useSentinel)
                .setPoolSize(poolSize)
                .setURI((useSentinel ? sentinelUrl : redisUrl));
        if (useSentinel) {
            builder.setMasterName(prop.tryProperty(REDIS_SENTINEL_MASTER).orElse("graknmaster"));
        }
        return builder.build();
    }

    private void logStartMessage(String host, String port) {
        String address = "http://" + host + ":" + port;
        LOG.info("\n==================================================");
        LOG.info("\n" + String.format(GraknEngineConfig.GRAKN_ASCII, address));
        LOG.info("\n==================================================");
    }

    @Override
    public String getName() {
        return "Grakn";
    }

    @Override
    public void initialize(Bootstrap<GraknEngineServerConfiguration> bootstrap) {
        WebsocketBundle websocketBundle = new WebsocketBundle(RemoteSession.class);
        bootstrap.addBundle(websocketBundle);
    }

    @Override
    public void run(
            final GraknEngineServerConfiguration graknEngineServerConfiguration,
            final Environment environment) throws Exception {

        int postProcessingDelay = prop.getPropertyAsInt(GraknEngineConfig.POST_PROCESSING_TASK_DELAY);

        environment.jersey().register(new GraqlQueryExceptionMapper(environment.metrics()));
        environment.jersey().register(new GraqlSyntaxExceptionMapper(environment.metrics()));
        environment.jersey().register(new GraknTxOperationExceptionMapper(environment.metrics()));
        environment.jersey().register(new InvalidKBExceptionMapper(environment.metrics()));
        environment.jersey().register(new GraknServerExceptionMapper(environment.metrics()));
        environment.jersey().register(new GraknBackendExceptionMapper(environment.metrics()));

        // Start all the controllers
        environment.jersey().register(new CommitLogController(postProcessingDelay, taskManager));
        environment.jersey().register(new GraqlController(factory, metricRegistry));
        environment.jersey().register(new ConceptController(factory, metricRegistry));
        environment.jersey().register(new DashboardController(factory));
        environment.jersey().register(new SystemController(factory, graknEngineStatus, metricRegistry));
        environment.jersey().register(new TasksController(taskManager, metricRegistry));
//        environment.jersey().register(new EntityController(factory));
//        environment.jersey().register(new EntityTypeController(factory));
//        environment.jersey().register(new RelationshipController(factory));
        environment.jersey().register(new RelationshipTypeController(factory));
        environment.jersey().register(new AttributeController(factory));
//        environment.jersey().register(new AttributeTypeController(factory));
        environment.jersey().register(new RoleController(factory));
        environment.jersey().register(new RuleController(factory));
        start();
    }

    public static void main(String[] args) {
        try {
            GraknEngineServer graknEngineServer = new GraknEngineServer();
            Thread closeThread = new Thread(graknEngineServer::close, "GraknEngineServer-shutdown");
            Runtime.getRuntime().addShutdownHook(closeThread);
            graknEngineServer.run(args);
        } catch (Exception e) {
            LOG.error("An exception has occurred while starting engine", e);
        }
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }
}
