package com.mz.jarboot.core.session;

import com.mz.jarboot.api.cmd.session.CommandSession;
import com.mz.jarboot.common.utils.StringUtils;
import com.mz.jarboot.core.advisor.AdviceListener;
import com.mz.jarboot.core.cmd.model.ResultModel;

import java.lang.instrument.ClassFileTransformer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author majianzheng
 */
public abstract class AbstractCommandSession implements CommandSession {
    protected boolean running = false;
    protected volatile String jobId = StringUtils.EMPTY;

    /**
     * 每执行一次命令生成一个唯一id
     * @return job id
     */
    @Override
    public String getJobId() {
        return jobId;
    }

    /**
     * 是否运行中
     * @return 是否在允许
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 开始执行
     */
    public abstract void setRunning();

    /**
     * 返回执行结果
     * @param resultModel 执行结果
     */
    public abstract void appendResult(ResultModel resultModel);

    /**
     * 注册监视器
     * @param adviceListener 监视器
     * @param transformer transformer
     */
    public abstract void register(AdviceListener adviceListener, ClassFileTransformer transformer);

    /**
     * 次数
     * @return 次数
     */
    public abstract AtomicInteger times();
}
