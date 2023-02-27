package com.mz.jarboot.demo.cmd;

import com.mz.jarboot.api.cmd.annotation.*;
import com.mz.jarboot.api.cmd.session.CommandSession;
import com.mz.jarboot.api.cmd.spi.CommandProcessor;
import com.mz.jarboot.demo.DemoServerApplication;

import java.util.concurrent.TimeUnit;

/**
 * pow算法命令
 * @author jianzhengma
 */
@Name("pow")
@Summary("The pow command summary")
@Description(" pow 2 2 \n fib -n 1000 2 2\n fib -n 1000 -i 100 2 2")
public class PowCommandProcessor implements CommandProcessor {
    private int number = 1;
    private int interval = 0;
    private double x = 1;
    private int y = 1;

    @Option(shortName = "n", longName = "number")
    @Description("执行次数")
    public void setNumber(int n) {
        this.number = n;
    }
    @Option(shortName = "i", longName = "interval")
    @Description("执行间隔时间（ms）")
    public void setInterval(int i) {
        this.interval = i;
    }
    @Argument(argName = "x", index = 0)
    @Description("基")
    public void setX(double v) {
        this.x = v;
    }
    @Argument(argName = "y", index = 1)
    @Description("幂")
    public void setY(int v) {
        this.y = v;
    }

    @Override
    public String process(CommandSession session, String[] args) {
        session.console("开始执行pow算法>>>");
        StringBuilder sb = new StringBuilder();
        sb
                .append("执行次数:")
                .append(number)
                .append(", 执行间隔:")
                .append(interval);
        session.console(sb.toString());
        double result = 0;
        long b = System.currentTimeMillis();
        for (int i = 0; i < this.number; ++i) {
            if (this.interval > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(this.interval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            result = DemoServerApplication.pow(this.x, this.y);
        }
        DemoServerApplication.notice("计算完成", session.getSessionId());
        return "计算结果：" + result + ", 耗时(ms)：" + (System.currentTimeMillis() - b);
    }

    @Override
    public void afterProcess(String result, Throwable e) {
        this.interval = 0;
        this.number = 1;
        this.x = 1f;
        this.y = 1;
    }
}
