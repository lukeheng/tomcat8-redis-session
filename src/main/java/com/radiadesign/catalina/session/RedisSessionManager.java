package com.radiadesign.catalina.session;

/**
 * Created by lenovo on 2016/9/21.
 */

import org.apache.catalina.*;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

public class RedisSessionManager extends ManagerBase implements Lifecycle {
    protected byte[] NULL_SESSION = "null".getBytes();
    private final Log log = LogFactory.getLog(RedisSessionManager.class);
    protected String host = "localhost";
    protected int port = 6379;
    protected int database = 0;
    protected String password = null;
    protected int timeout = 2000;
    protected JedisPool connectionPool;
    protected RedisSessionHandlerValve handlerValve;
    protected ThreadLocal<RedisSession> currentSession = new ThreadLocal();
    protected ThreadLocal<String> currentSessionId = new ThreadLocal();
    protected ThreadLocal<Boolean> currentSessionIsPersisted = new ThreadLocal();
    protected Serializer serializer;
    protected static String name = "RedisSessionManager";
    protected String serializationStrategyClass = "com.radiadesign.catalina.session.JavaSerializer";
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);

    public RedisSessionManager() {
    }

    public String getHost() {
        return this.host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getDatabase() {
        return this.database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setSerializationStrategyClass(String strategy) {
        this.serializationStrategyClass = strategy;
    }

    public int getRejectedSessions() {
        return 0;
    }

    public void setRejectedSessions(int i) {
    }

    protected Jedis acquireConnection() {
        Jedis jedis = this.connectionPool.getResource();
        if (this.getDatabase() != 0) {
            jedis.select(this.getDatabase());
        }

        return jedis;
    }

    protected void returnConnection(Jedis jedis, Boolean error) {
        if (error.booleanValue()) {
            this.connectionPool.returnBrokenResource(jedis);
        } else {
            this.connectionPool.returnResource(jedis);
        }

    }

    protected void returnConnection(Jedis jedis) {
        this.returnConnection(jedis, Boolean.valueOf(false));
    }

    public void load() throws ClassNotFoundException, IOException {
    }

    public void unload() throws IOException {
    }

    public void addLifecycleListener(LifecycleListener listener) {
        this.lifecycle.addLifecycleListener(listener);
    }

    public LifecycleListener[] findLifecycleListeners() {
        return this.lifecycle.findLifecycleListeners();
    }

    public void removeLifecycleListener(LifecycleListener listener) {
        this.lifecycle.removeLifecycleListener(listener);
    }

    protected synchronized void startInternal() throws LifecycleException {
        super.startInternal();
        this.setState(LifecycleState.STARTING);
        Boolean attachedToValve = Boolean.valueOf(false);
        Valve[] var5;
        int var4 = (var5 = this.getContext().getPipeline().getValves()).length;

        for (int var3 = 0; var3 < var4; ++var3) {
            Valve e = var5[var3];
            if (e instanceof RedisSessionHandlerValve) {
                this.handlerValve = (RedisSessionHandlerValve) e;
                this.handlerValve.setRedisSessionManager(this);
                this.log.info("Attached to RedisSessionHandlerValve");
                attachedToValve = Boolean.valueOf(true);
                break;
            }
        }

        if (!attachedToValve.booleanValue()) {
            String var9 = "Unable to attach to session handling valve; sessions cannot be saved after the request without the valve starting properly.";
            this.log.fatal(var9);
            throw new LifecycleException(var9);
        } else {
            try {
                this.initializeSerializer();
            } catch (ClassNotFoundException var6) {
                this.log.fatal("Unable to load serializer", var6);
                throw new LifecycleException(var6);
            } catch (InstantiationException var7) {
                this.log.fatal("Unable to load serializer", var7);
                throw new LifecycleException(var7);
            } catch (IllegalAccessException var8) {
                this.log.fatal("Unable to load serializer", var8);
                throw new LifecycleException(var8);
            }

            this.log.info("Will expire sessions after " + this.getMaxInactiveInterval() + " seconds");
            this.initializeDatabaseConnection();
            this.setDistributable(true);
        }
    }

    protected synchronized void stopInternal() throws LifecycleException {
        if (this.log.isDebugEnabled()) {
            this.log.debug("Stopping");
        }

        this.setState(LifecycleState.STOPPING);

        try {
            this.connectionPool.destroy();
        } catch (Exception var2) {
            ;
        }

        super.stopInternal();
    }

    public Session createSession(String sessionId) {
        RedisSession session = (RedisSession) this.createEmptySession();
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(this.getMaxInactiveInterval());
        String jvmRoute = this.getJvmRoute();
        Boolean error = Boolean.valueOf(true);
        Jedis jedis = null;

        try {
            jedis = this.acquireConnection();

            do {
                if (sessionId == null) {
                    sessionId = this.generateSessionId();
                }

                if (jvmRoute != null) {
                    sessionId = sessionId + '.' + jvmRoute;
                }
            } while (jedis.setnx(sessionId.getBytes(), this.NULL_SESSION).longValue() == 1L);

            error = Boolean.valueOf(false);
            session.setId(sessionId);
            session.tellNew();
            this.currentSession.set(session);
            this.currentSessionId.set(sessionId);
            this.currentSessionIsPersisted.set(Boolean.valueOf(false));
            return session;
        } finally {
            if (jedis != null) {
                this.returnConnection(jedis, error);
            }

        }
    }

    public Session createEmptySession() {
        return new RedisSession(this);
    }

    public void add(Session session) {
        try {
            this.save(session);
        } catch (IOException var3) {
            this.log.warn("Unable to add to session manager store: " + var3.getMessage());
            throw new RuntimeException("Unable to add to session manager store.", var3);
        }
    }

    public Session findSession(String id) throws IOException {
        RedisSession session;
        if (id == null) {
            session = null;
            this.currentSessionIsPersisted.set(Boolean.valueOf(false));
        } else if (id.equals(this.currentSessionId.get())) {
            session = (RedisSession) this.currentSession.get();
        } else {
            session = this.loadSessionFromRedis(id);
            if (session != null) {
                this.currentSessionIsPersisted.set(Boolean.valueOf(true));
            }
        }

        this.currentSession.set(session);
        this.currentSessionId.set(id);
        return session;
    }

    public void clear() {
        Jedis jedis = null;
        Boolean error = Boolean.valueOf(true);

        try {
            jedis = this.acquireConnection();
            jedis.flushDB();
            error = Boolean.valueOf(false);
        } finally {
            if (jedis != null) {
                this.returnConnection(jedis, error);
            }

        }

    }

    public int getSize() throws IOException {
        Jedis jedis = null;
        Boolean error = Boolean.valueOf(true);

        int var5;
        try {
            jedis = this.acquireConnection();
            int size = jedis.dbSize().intValue();
            error = Boolean.valueOf(false);
            var5 = size;
        } finally {
            if (jedis != null) {
                this.returnConnection(jedis, error);
            }

        }

        return var5;
    }

    public String[] keys() throws IOException {
        Jedis jedis = null;
        Boolean error = Boolean.valueOf(true);

        String[] var5;
        try {
            jedis = this.acquireConnection();
            Set keySet = jedis.keys("*");
            error = Boolean.valueOf(false);
            var5 = (String[]) keySet.toArray(new String[keySet.size()]);
        } finally {
            if (jedis != null) {
                this.returnConnection(jedis, error);
            }

        }

        return var5;
    }

    public RedisSession loadSessionFromRedis(String id) throws IOException {
        Jedis jedis = null;
        Boolean error = Boolean.valueOf(true);

        RedisSession var9;
        try {
            this.log.trace("Attempting to load session " + id + " from Redis");
            jedis = this.acquireConnection();
            byte[] ex = jedis.get(id.getBytes());
            error = Boolean.valueOf(false);
            RedisSession session;
            if (ex == null) {
                this.log.trace("Session " + id + " not found in Redis");
                session = null;
            } else {
                if (Arrays.equals(this.NULL_SESSION, ex)) {
                    jedis.del(id);
                    jedis.setex(id, 60, "jedis");
                    ex = jedis.get(id.getBytes());
                    if (Arrays.equals(this.NULL_SESSION, ex)) {
                        throw new IllegalStateException("Race condition encountered: attempted to load session错误!!![" + id + "] which has been created but not yet serialized.");
                    }
                }

                this.log.trace("Deserializing session " + id + " from Redis");
                session = (RedisSession) this.createEmptySession();
                this.serializer.deserializeInto(ex, session);
                session.setId(id);
                session.setNew(false);
                session.setMaxInactiveInterval(this.getMaxInactiveInterval() * 1000);
                session.access();
                session.setValid(true);
                session.resetDirtyTracking();
                if (this.log.isTraceEnabled()) {
                    this.log.trace("Session Contents [" + id + "]:");
                    Iterator var7 = Collections.list(session.getAttributeNames()).iterator();

                    while (var7.hasNext()) {
                        Object name = var7.next();
                        this.log.trace("  " + name);
                    }
                }
            }

            var9 = session;
        } catch (IOException var13) {
            this.log.fatal(var13.getMessage());
            throw var13;
        } catch (ClassNotFoundException var14) {
            this.log.fatal("Unable to deserialize into session", var14);
            throw new IOException("Unable to deserialize into session", var14);
        } finally {
            if (jedis != null) {
                this.returnConnection(jedis, error);
            }

        }

        return var9;
    }

    public void save(Session session) throws IOException {
        Jedis jedis = null;
        Boolean error = Boolean.valueOf(true);

        try {
            this.log.trace("Saving session " + session + " into Redis");
            RedisSession e = (RedisSession) session;
            if (this.log.isTraceEnabled()) {
                this.log.trace("Session Contents [" + e.getId() + "]:");
                Iterator binaryId = Collections.list(e.getAttributeNames()).iterator();

                while (binaryId.hasNext()) {
                    Object sessionIsDirty = binaryId.next();
                    this.log.trace("  " + sessionIsDirty);
                }
            }

            Boolean sessionIsDirty1 = e.isDirty();
            e.resetDirtyTracking();
            byte[] binaryId1 = e.getId().getBytes();
            jedis = this.acquireConnection();
            if (sessionIsDirty1.booleanValue() || !((Boolean) this.currentSessionIsPersisted.get()).booleanValue()) {
                jedis.set(binaryId1, this.serializer.serializeFrom(e));
            }

            this.currentSessionIsPersisted.set(Boolean.valueOf(true));
            this.log.trace("Setting expire timeout on session [" + e.getId() + "] to " + this.getMaxInactiveInterval());
            jedis.expire(binaryId1, this.getMaxInactiveInterval());
            error = Boolean.valueOf(false);
        } catch (IOException var10) {
            this.log.error(var10.getMessage());
            throw var10;
        } finally {
            if (jedis != null) {
                this.returnConnection(jedis, error);
            }

        }

    }

    public void remove(Session session) {
        Jedis jedis = null;
        Boolean error = Boolean.valueOf(true);
        this.log.trace("Removing session ID : " + session.getId());

        try {
            jedis = this.acquireConnection();
            jedis.del(session.getId());
            error = Boolean.valueOf(false);
        } finally {
            if (jedis != null) {
                this.returnConnection(jedis, error);
            }

        }

    }

    public void afterRequest() {
        RedisSession redisSession = (RedisSession) this.currentSession.get();
        if (redisSession != null) {
            this.currentSession.remove();
            this.currentSessionId.remove();
            this.currentSessionIsPersisted.remove();
            this.log.trace("Session removed from ThreadLocal :" + redisSession.getIdInternal());
        }

    }

    public void processExpires() {
    }

    private void initializeDatabaseConnection() throws LifecycleException {
        try {
            this.connectionPool = new JedisPool(new JedisPoolConfig(), this.getHost(), this.getPort(), this.getTimeout(), this.getPassword());
        } catch (Exception var2) {
            var2.printStackTrace();
            throw new LifecycleException("Error Connecting to Redis", var2);
        }
    }

    private void initializeSerializer() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        this.log.info("Attempting to use serializer :" + this.serializationStrategyClass);
        this.serializer = (Serializer) Class.forName(this.serializationStrategyClass).newInstance();
        Loader loader = null;
        Context context = this.getContext();
        if (context != null) {
            loader = context.getLoader();
        }

        ClassLoader classLoader = null;
        if (loader != null) {
            classLoader = loader.getClassLoader();
        }

        this.serializer.setClassLoader(classLoader);
    }
}
