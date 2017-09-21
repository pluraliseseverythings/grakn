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
import static ai.grakn.engine.GraknEngineConfig.WEBSOCKET_TIMEOUT;
import ai.grakn.engine.controller.AuthController;
import ai.grakn.engine.controller.CommitLogController;
import ai.grakn.engine.controller.ConceptController;
import ai.grakn.engine.controller.DashboardController;
import ai.grakn.engine.controller.GraqlController;
import ai.grakn.engine.controller.SystemController;
import ai.grakn.engine.controller.TasksController;
import ai.grakn.engine.controller.UserController;
import ai.grakn.engine.controller.api.EntityController;
import ai.grakn.engine.controller.api.EntityTypeController;
import ai.grakn.engine.controller.api.RelationshipController;
import ai.grakn.engine.controller.api.RelationshipTypeController;
import ai.grakn.engine.controller.api.AttributeController;
import ai.grakn.engine.controller.api.AttributeTypeController;
import ai.grakn.engine.controller.api.RoleController;
import ai.grakn.engine.controller.api.RuleController;
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
import ai.grakn.engine.user.UsersHandler;
import ai.grakn.engine.util.EngineID;
import ai.grakn.engine.util.JWTHandler;
import ai.grakn.exception.GraknBackendException;
import ai.grakn.exception.GraknServerException;
import static ai.grakn.util.ErrorMessage.VERSION_MISMATCH;
import ai.grakn.util.GraknVersion;
import ai.grakn.util.REST;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import static com.codahale.metrics.MetricRegistry.name;
import com.codahale.metrics.jvm.CachedThreadStatesGaugeSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.google.common.base.Stopwatch;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import javax.annotation.Nullable;
import mjson.Json;
import static org.apache.commons.lang.exception.ExceptionUtils.getFullStackTrace;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * Main class in charge to start a web server and all the REST controllers.
 *
 * @author Marco Scoppetta
 */
public class GraknEngineServer implements AutoCloseable {

    private static final String REDIS_VERSION_KEY = "info:version";

    private static final String LOAD_SYSTEM_SCHEMA_LOCK_NAME = "load-system-schema";
    private static final Logger LOG = LoggerFactory.getLogger(GraknEngineServer.class);
    private static final Set<String> unauthenticatedEndPoints = new HashSet<>(Arrays.asList(
            REST.WebPath.NEW_SESSION_URI,
            REST.WebPath.REMOTE_SHELL_URI,
            REST.WebPath.System.CONFIGURATION,
            REST.WebPath.IS_PASSWORD_PROTECTED_URI));

    private final GraknEngineConfig prop;
    private final EngineID engineId = EngineID.me();
    private final Service spark = Service.ignite();
    private final TaskManager taskManager;
    private final EngineGraknTxFactory factory;
    private final MetricRegistry metricRegistry;
    private final LockProvider lockProvider;
    private final GraknEngineStatus graknEngineStatus = new GraknEngineStatus();
    private final RedisWrapper redisWrapper;

    private GraknEngineServer(GraknEngineConfig prop, RedisWrapper redisWrapper) {
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
    }

    public static GraknEngineServer create(GraknEngineConfig prop) {
        return create(prop, instantiateRedis(prop));
    }

    public static GraknEngineServer create(GraknEngineConfig prop, RedisWrapper redisWrapper) {
        return new GraknEngineServer(prop, redisWrapper);
    }

    public static void main(String[] args) {
        try {
            GraknEngineConfig prop = GraknEngineConfig.create();
            // Start Engine
            GraknEngineServer graknEngineServer = create(prop);
            graknEngineServer.start();
            // close GraknEngineServer on SIGTERM
            Thread closeThread = new Thread(graknEngineServer::close, "GraknEngineServer-shutdown");
            Runtime.getRuntime().addShutdownHook(closeThread);
        } catch (Exception e) {
            LOG.error("An exception has occurred", e);
        }
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
            startHTTP();
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
            stopHTTP();
            redisWrapper.close();
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
        metricRegistry.register(name(GraknEngineServer.class, "jedis", "idle"), (Gauge<Integer>) jedisPool::getNumIdle);
        metricRegistry.register(name(GraknEngineServer.class, "jedis", "active"), (Gauge<Integer>) jedisPool::getNumActive);
        metricRegistry.register(name(GraknEngineServer.class, "jedis", "waiters"), (Gauge<Integer>) jedisPool::getNumWaiters);
        metricRegistry.register(name(GraknEngineServer.class, "jedis", "borrow_wait_time_ms", "max"), (Gauge<Long>) jedisPool::getMaxBorrowWaitTimeMillis);
        metricRegistry.register(name(GraknEngineServer.class, "jedis", "borrow_wait_time_ms", "mean"), (Gauge<Long>) jedisPool::getMeanBorrowWaitTimeMillis);

        metricRegistry.register(name(GraknEngineServer.class, "System", "gc"), new GarbageCollectorMetricSet());
        metricRegistry.register(name(GraknEngineServer.class, "System", "threads"), new CachedThreadStatesGaugeSet(15, TimeUnit.SECONDS));
        metricRegistry.register(name(GraknEngineServer.class, "System", "memory"), new MemoryUsageGaugeSet());

        if (!inMemoryQueue) {
            Optional<String> consumers = prop.tryProperty(QUEUE_CONSUMERS);
            taskManager = consumers
                    .map(s -> new RedisTaskManager(engineId, prop, jedisPool, Integer.parseInt(s), factory, lockProvider, metricRegistry))
                    .orElseGet(() -> new RedisTaskManager(engineId, prop, jedisPool, factory, lockProvider, metricRegistry));
        } else  {
            LOG.info("Task queue in memory");
            // Redis storage for counts, in the RedisTaskManager it's created in consumers
            RedisCountStorage redisCountStorage = RedisCountStorage.create(jedisPool, metricRegistry);
            taskManager = new StandaloneTaskManager(engineId, prop, redisCountStorage, factory, lockProvider, metricRegistry);
        }
        return taskManager;
    }

    public void startHTTP() {
        boolean passwordProtected = prop.getPropertyAsBool(GraknEngineConfig.PASSWORD_PROTECTED_PROPERTY, false);

        // TODO: Make sure controllers handle the null case
        Optional<String> secret = prop.tryProperty(GraknEngineConfig.JWT_SECRET_PROPERTY);
        @Nullable JWTHandler jwtHandler = secret.map(JWTHandler::create).orElse(null);
        UsersHandler usersHandler = UsersHandler.create(prop.getProperty(GraknEngineConfig.ADMIN_PASSWORD_PROPERTY), factory);

        configureSpark(spark, prop, jwtHandler);

        // Start the websocket for Graql
        RemoteSession graqlWebSocket = passwordProtected ? RemoteSession.passwordProtected(usersHandler) : RemoteSession.create();
        spark.webSocket(REST.WebPath.REMOTE_SHELL_URI, graqlWebSocket);

        int postProcessingDelay = prop.getPropertyAsInt(GraknEngineConfig.POST_PROCESSING_TASK_DELAY);

        // Start all the controllers
        new GraqlController(factory, spark, metricRegistry);
        new ConceptController(factory, spark, metricRegistry);
        new DashboardController(factory, spark);
        new SystemController(factory, spark, graknEngineStatus, metricRegistry);
        new AuthController(spark, passwordProtected, jwtHandler, usersHandler);
        new UserController(spark, usersHandler);
        new CommitLogController(spark, postProcessingDelay, taskManager);
        new TasksController(spark, taskManager, metricRegistry);
        new EntityController(factory, spark);
        new EntityTypeController(factory, spark);
        new RelationshipController(factory, spark);
        new RelationshipTypeController(factory, spark);
        new AttributeController(factory, spark);
        new AttributeTypeController(factory, spark);
        new RoleController(factory, spark);
        new RuleController(factory, spark);

        // This method will block until all the controllers are ready to serve requests
        spark.awaitInitialization();
    }

    public static void configureSpark(Service spark, GraknEngineConfig prop, @Nullable JWTHandler jwtHandler) {
        configureSpark(spark, 
                       prop.getProperty(GraknEngineConfig.SERVER_HOST_NAME),
                       Integer.parseInt(prop.getProperty(GraknEngineConfig.SERVER_PORT_NUMBER)),
                       prop.getPath(GraknEngineConfig.STATIC_FILES_PATH),
                       prop.getPropertyAsBool(GraknEngineConfig.PASSWORD_PROTECTED_PROPERTY, false),
                       prop.tryIntProperty(GraknEngineConfig.WEBSERVER_THREADS, 64),
                       jwtHandler);
    }
    
    public static void configureSpark(Service spark, 
                                      String hostName, 
                                      int port, 
                                      String staticFolder,
                                      boolean passwordProtected,
                                      int maxThreads,
                                      @Nullable JWTHandler jwtHandler){
        // Set host name
        spark.ipAddress(hostName);

        // Set port
        spark.port(port);

        // Set the external static files folder
        spark.staticFiles.externalLocation(staticFolder);

        spark.threadPool(maxThreads);
        spark.webSocketIdleTimeoutMillis(WEBSOCKET_TIMEOUT);

        // Register filter to check authentication token in each request
        if (passwordProtected) {
            spark.before((req, res) -> checkAuthorization(spark, req, jwtHandler));
        }

        //Register exception handlers
        spark.exception(GraknServerException.class, (e, req, res) -> {
            assert e instanceof GraknServerException; // This is guaranteed by `spark#exception`
            handleGraknServerError((GraknServerException) e, res);
        });

        spark.exception(Exception.class, (e, req, res) -> handleInternalError(e, res));
    }

    public void stopHTTP() {
        spark.stop();

        // Block until server is truly stopped
        // This occurs when there is no longer a port assigned to the Spark server
        boolean running = true;
        while (running) {
            try {
                spark.port();
            }
            catch(IllegalStateException e){
                LOG.debug("Spark server has been stopped");
                running = false;
            }
        }
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

    /**
     * If authorization is enabled, check the client has correct JWT Token before allowing
     * access to specific endpoints.
     * @param request request information from the client
     */
    private static void checkAuthorization(Service spark, Request request, JWTHandler jwtHandler) throws HaltException {
        //we dont check authorization token if the path requested is one of the unauthenticated ones
        if (!unauthenticatedEndPoints.contains(request.pathInfo())) {
            //add check to see if string contains substring "Bearer ", for now a lot of optimism here
            boolean authenticated;
            try {
                if (request.headers("Authorization") == null || !request.headers("Authorization").startsWith("Bearer ")) {
                    throw GraknServerException.authenticationFailure();
                }

                String token = request.headers("Authorization").substring(7);
                authenticated = jwtHandler.verifyJWT(token);
                request.attribute(REST.Request.USER_ATTR, jwtHandler.extractUserFromJWT(token));
            }
            catch (GraknBackendException e) {
                throw e;
            }
            catch (Exception e) {
                //request is malformed, return 400
                throw GraknServerException.serverException(400, e);
            }
            if (!authenticated) {
                throw spark.halt(401, "User not authenticated.");
            }
        }
    }

    /**
     * Handle any {@link GraknBackendException} that are thrown by the server. Configures and returns
     * the correct JSON response.
     *
     * @param exception exception thrown by the server
     * @param response response to the client
     */
    private static void handleGraknServerError(GraknServerException exception, Response response){
        LOG.error("REST error", exception);
        response.status(exception.getStatus());
        response.body(Json.object("exception", exception.getMessage()).toString());
        response.type(ContentType.APPLICATION_JSON.getMimeType());
    }

    /**
     * Handle any exception thrown by the server
     * @param exception Exception by the server
     * @param response response to the client
     */
    private static void handleInternalError(Exception exception, Response response){
        LOG.error("REST error", exception);
        response.status(500);
        response.body(Json.object("exception", exception.getMessage()).toString());
        response.type(ContentType.APPLICATION_JSON.getMimeType());
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
}
