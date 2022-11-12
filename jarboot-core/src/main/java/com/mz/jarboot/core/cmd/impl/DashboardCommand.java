package com.mz.jarboot.core.cmd.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mz.jarboot.common.utils.JsonUtils;
import com.mz.jarboot.common.utils.NetworkUtils;
import com.mz.jarboot.core.cmd.AbstractCommand;
import com.mz.jarboot.api.cmd.annotation.Description;
import com.mz.jarboot.api.cmd.annotation.Name;
import com.mz.jarboot.api.cmd.annotation.Option;
import com.mz.jarboot.api.cmd.annotation.Summary;
import com.mz.jarboot.core.cmd.model.*;
import com.mz.jarboot.core.constant.CoreConstant;
import com.mz.jarboot.core.session.CommandCoreSession;
import com.mz.jarboot.core.utils.LogUtils;
import com.mz.jarboot.common.utils.StringUtils;
import com.mz.jarboot.core.utils.ThreadUtil;
import com.mz.jarboot.core.utils.metrics.SumRateCounter;
import org.slf4j.Logger;

import java.lang.management.*;
import java.util.*;

/**
 * @author majianzheng
 */
@SuppressWarnings("all")
@Name("dashboard")
@Summary("Overview of target jvm's thread, memory, gc, vm, tomcat info.")
@Description(CoreConstant.EXAMPLE +
        "  dashboard\n" +
        "  dashboard -n 10\n" +
        "  dashboard -i 2000\n" +
        CoreConstant.WIKI + CoreConstant.WIKI_HOME + "dashboard")
public class DashboardCommand extends AbstractCommand {
    private static final Logger logger = LogUtils.getLogger();

    private SumRateCounter tomcatRequestCounter = new SumRateCounter();
    private SumRateCounter tomcatErrorCounter = new SumRateCounter();
    private SumRateCounter tomcatReceivedBytesCounter = new SumRateCounter();
    private SumRateCounter tomcatSentBytesCounter = new SumRateCounter();

    private int numOfExecutions = Integer.MAX_VALUE;

    private long interval = 5000;

    private volatile long count = 0;
    private volatile Timer timer = null;

    @Option(shortName = "n", longName = "number-of-execution")
    @Description("The number of times this command will be executed.")
    public void setNumOfExecutions(int numOfExecutions) {
        this.numOfExecutions = numOfExecutions;
    }

    @Option(shortName = "i", longName = "interval")
    @Description("The interval (in ms) between two executions, default is 5000 ms.")
    public void setInterval(long interval) {
        this.interval = interval;
    }

    @Override
    public void cancel() {
        stop();
    }

    @Override
    public void run() {
        timer = new Timer("Timer-for-jarboot-dashboard-" + session.getSessionId(), true);

        // start the timer
        timer.scheduleAtFixedRate(new DashboardTimerTask(session), 0, getInterval());
    }

    public synchronized void stop() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    public synchronized void restart() {
        if (timer == null) {
            timer = new Timer("Timer-for-jarboot-dashboard-" + session.getSessionId(), true);
            timer.scheduleAtFixedRate(new DashboardTimerTask(session), 0, getInterval());
        }
    }

    public int getNumOfExecutions() {
        return numOfExecutions;
    }

    public long getInterval() {
        return interval;
    }

    private static String beautifyName(String name) {
        return name.replace(' ', '_').toLowerCase();
    }

    private static void addMemoryInfo(DashboardModel dashboardModel) {
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        Map<String, List<MemoryEntryVO>> memoryInfoMap = new LinkedHashMap<String, List<MemoryEntryVO>>();
        dashboardModel.setMemoryInfo(memoryInfoMap);

        //heap
        MemoryUsage heapMemoryUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        List<MemoryEntryVO> heapMemEntries = new ArrayList<MemoryEntryVO>();
        heapMemEntries.add(createMemoryEntryVO(MemoryEntryVO.TYPE_HEAP, MemoryEntryVO.TYPE_HEAP, heapMemoryUsage));
        for (MemoryPoolMXBean poolMXBean : memoryPoolMXBeans) {
            if (MemoryType.HEAP.equals(poolMXBean.getType())) {
                MemoryUsage usage = poolMXBean.getUsage();
                String poolName = beautifyName(poolMXBean.getName());
                heapMemEntries.add(createMemoryEntryVO(MemoryEntryVO.TYPE_HEAP, poolName, usage));
            }
        }
        memoryInfoMap.put(MemoryEntryVO.TYPE_HEAP, heapMemEntries);

        //non-heap
        MemoryUsage nonHeapMemoryUsage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        List<MemoryEntryVO> nonheapMemEntries = new ArrayList<MemoryEntryVO>();
        nonheapMemEntries.add(createMemoryEntryVO(MemoryEntryVO.TYPE_NON_HEAP, MemoryEntryVO.TYPE_NON_HEAP, nonHeapMemoryUsage));
        for (MemoryPoolMXBean poolMXBean : memoryPoolMXBeans) {
            if (MemoryType.NON_HEAP.equals(poolMXBean.getType())) {
                MemoryUsage usage = poolMXBean.getUsage();
                String poolName = beautifyName(poolMXBean.getName());
                nonheapMemEntries.add(createMemoryEntryVO(MemoryEntryVO.TYPE_NON_HEAP, poolName, usage));
            }
        }
        memoryInfoMap.put(MemoryEntryVO.TYPE_NON_HEAP, nonheapMemEntries);

        addBufferPoolMemoryInfo(memoryInfoMap);
    }

    private static void addBufferPoolMemoryInfo(Map<String, List<MemoryEntryVO>> memoryInfoMap) {
        try {
            List<MemoryEntryVO> bufferPoolMemEntries = new ArrayList<MemoryEntryVO>();
            @SuppressWarnings("rawtypes")
            Class bufferPoolMXBeanClass = Class.forName("java.lang.management.BufferPoolMXBean");
            @SuppressWarnings("unchecked")
            List<BufferPoolMXBean> bufferPoolMXBeans = ManagementFactory.getPlatformMXBeans(bufferPoolMXBeanClass);
            for (BufferPoolMXBean mbean : bufferPoolMXBeans) {
                long used = mbean.getMemoryUsed();
                long total = mbean.getTotalCapacity();
                bufferPoolMemEntries.add(new MemoryEntryVO(MemoryEntryVO.TYPE_BUFFER_POOL, mbean.getName(), used, total, Long.MIN_VALUE));
            }
            memoryInfoMap.put(MemoryEntryVO.TYPE_BUFFER_POOL, bufferPoolMemEntries);
        } catch (ClassNotFoundException e) {
            // ignore
        }
    }

    private static void addRuntimeInfo(DashboardModel dashboardModel) {
        RuntimeInfoVO runtimeInfo = new RuntimeInfoVO();
        runtimeInfo.setOsName(System.getProperty("os.name"));
        runtimeInfo.setOsVersion(System.getProperty("os.version"));
        runtimeInfo.setJavaVersion(System.getProperty("java.version"));
        runtimeInfo.setJavaHome(System.getProperty("java.home"));
        runtimeInfo.setSystemLoadAverage(ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage());
        runtimeInfo.setProcessors(Runtime.getRuntime().availableProcessors());
        runtimeInfo.setUptime(ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        runtimeInfo.setTimestamp(new Date().getTime());
        dashboardModel.setRuntimeInfo(runtimeInfo);
    }

    private static MemoryEntryVO createMemoryEntryVO(String type, String name, MemoryUsage memoryUsage) {
        return new MemoryEntryVO(type, name, memoryUsage.getUsed(), memoryUsage.getCommitted(), memoryUsage.getMax());
    }

    private static void addGcInfo(DashboardModel dashboardModel) {
        List<GcInfoVO> gcInfos = new ArrayList<GcInfoVO>();
        dashboardModel.setGcInfos(gcInfos);

        List<GarbageCollectorMXBean> garbageCollectorMxBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcMXBean : garbageCollectorMxBeans) {
            String name = gcMXBean.getName();
            gcInfos.add(new GcInfoVO(beautifyName(name), gcMXBean.getCollectionCount(), gcMXBean.getCollectionTime()));
        }
    }

    private void addTomcatInfo(DashboardModel dashboardModel) {
        // 如果请求tomcat信息失败，则不显示tomcat信息
        if (!NetworkUtils.isHostConnectable("127.0.0.1", 8006)) {
            return;
        }

        TomcatInfoVO tomcatInfoVO = new TomcatInfoVO();
        dashboardModel.setTomcatInfo(tomcatInfoVO);
        String threadPoolPath = "http://localhost:8006/connector/threadpool";
        String connectorStatPath = "http://localhost:8006/connector/stats";
        NetworkUtils.Response connectorStatResponse = NetworkUtils.request(connectorStatPath);
        if (connectorStatResponse.isSuccess()) {
            List<TomcatInfoVO.ConnectorStats> connectorStats = new ArrayList<TomcatInfoVO.ConnectorStats>();
            JsonNode tomcatConnectorStats = JsonUtils.readAsJsonNode(connectorStatResponse.getContent());
            for (JsonNode stat : tomcatConnectorStats) {
                String connectorName = stat.get("name").asText(StringUtils.EMPTY).replace("\"", "");
                long bytesReceived = stat.get("bytesReceived").asLong(0);
                long bytesSent = stat.get("bytesSent").asLong(0);
                long processingTime = stat.get("processingTime").asLong(0);
                long requestCount = stat.get("requestCount").asLong(0);
                long errorCount = stat.get("errorCount").asLong(0);

                tomcatRequestCounter.update(requestCount);
                tomcatErrorCounter.update(errorCount);
                tomcatReceivedBytesCounter.update(bytesReceived);
                tomcatSentBytesCounter.update(bytesSent);

                double qps = tomcatRequestCounter.rate();
                double rt = processingTime / (double) requestCount;
                double errorRate = tomcatErrorCounter.rate();
                long receivedBytesRate = new Double(tomcatReceivedBytesCounter.rate()).longValue();
                long sentBytesRate = new Double(tomcatSentBytesCounter.rate()).longValue();

                TomcatInfoVO.ConnectorStats connectorStat = new TomcatInfoVO.ConnectorStats();
                connectorStat.setName(connectorName);
                connectorStat.setQps(qps);
                connectorStat.setRt(rt);
                connectorStat.setError(errorRate);
                connectorStat.setReceived(receivedBytesRate);
                connectorStat.setSent(sentBytesRate);
                connectorStats.add(connectorStat);
            }
            tomcatInfoVO.setConnectorStats(connectorStats);
        }

        NetworkUtils.Response threadPoolResponse = NetworkUtils.request(threadPoolPath);
        if (threadPoolResponse.isSuccess()) {
            List<TomcatInfoVO.ThreadPool> threadPools = new ArrayList<TomcatInfoVO.ThreadPool>();
            JsonNode threadPoolInfos = JsonUtils.readAsJsonNode(threadPoolResponse.getContent());
            for (JsonNode info : threadPoolInfos) {
                String name = info.get("name").asText(StringUtils.EMPTY).replace("\"", "");
                long busy = info.get("threadBusy").asLong(0);
                long total = info.get("threadCount").asLong(0);
                threadPools.add(new TomcatInfoVO.ThreadPool(name, busy, total));
            }
            tomcatInfoVO.setThreadPools(threadPools);
        }
    }

    private class DashboardTimerTask extends TimerTask {
        private CommandCoreSession process;
        private ThreadSampler threadSampler;

        public DashboardTimerTask(CommandCoreSession process) {
            this.process = process;
            this.threadSampler = new ThreadSampler();
        }

        @Override
        public void run() {
            try {
                if (count >= getNumOfExecutions()) {
                    // stop the timer
                    timer.cancel();
                    timer.purge();
                    process.end(true, "Process ends after " + getNumOfExecutions() + " time(s).");
                    return;
                }

                DashboardModel dashboardModel = new DashboardModel();

                //thread sample
                List<ThreadVO> threads = ThreadUtil.getThreads();
                dashboardModel.setThreads(threadSampler.sample(threads));

                //memory
                addMemoryInfo(dashboardModel);

                //gc
                addGcInfo(dashboardModel);

                //runtime
                addRuntimeInfo(dashboardModel);

                //tomcat
                try {
                    addTomcatInfo(dashboardModel);
                } catch (Throwable e) {
                    logger.error("try to read tomcat info error", e);
                }

                process.appendResult(dashboardModel);

                count++;
                process.times().incrementAndGet();
            } catch (Throwable e) {
                String msg = "process dashboard failed: " + e.getMessage();
                logger.error(msg, e);
                process.end(false, msg);
            }
        }
    }

}
