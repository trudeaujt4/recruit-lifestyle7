package com.mz.jarboot.core.stream;

import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 标准输出流实现类
 * @author majianzheng
 */
public class StdConsoleOutputStream extends OutputStream {
    /** 最小打印字符长度 */
    private static final int MIN_PRINT_UNIT = 12;
    /** IO刷新阈值 */
    private static final int  FLUSH_THRESHOLD = (MIN_PRINT_UNIT * 256);
    /** ANSI控制符结束位校验阈值 */
    private static final int  ANSI_CHECK_THRESHOLD = (FLUSH_THRESHOLD  + MIN_PRINT_UNIT * 62);
    /** buffer起始的无效索引 */
    private static final int NO_BUFFER_OFFSET = -1;
    /** 退格键 */
    private static final byte BACKSPACE = '\b';
    /** ANSI控制符 */
    private static final String ANSI_BEGIN = "\003[";
    /** IO 字符缓存 */
    private final byte[] buffer = new byte[FLUSH_THRESHOLD + MIN_PRINT_UNIT * 64];
    /** buffer当前索引位置 */
    private int offset = NO_BUFFER_OFFSET;
    /** 退格的计数值 */
    private final AtomicInteger backspaceNum = new AtomicInteger(0);
    /** 文本处理接口 */
    private StdPrintHandler printHandler;
    /** 退格处理接口 */
    private StdBackspaceHandler backspaceHandler;
    /** IO 唤醒接口 */
    private final Runnable wakeup;

    /**
     * 设置唤醒接口
     * @param wakeup 唤醒接口
     */
    public StdConsoleOutputStream(Runnable wakeup) {
        this.wakeup = wakeup;
    }

    /**
     * 设置文本处理接口
     * @param printHandler 文本处理接口
     */
    public void setPrintHandler(StdPrintHandler printHandler) {
        this.printHandler = printHandler;
    }

    /**
     * 设置退格处理接口
     * @param handler 退格处理接口
     */
    public void setBackspaceHandler(StdBackspaceHandler handler) {
        this.backspaceHandler = handler;
    }

    /**
     * 重写write
     * @param b byte字节
     */
    @Override
    public void write(int b) {
        byte c = (byte) b;
        if (BACKSPACE == c) {
            this.backspaceNum.incrementAndGet();
            wakeup.run();
            return;
        }
        //int的高24位是无效的，实际只用到8位
        buffer[++offset] = c;
        if (offset > FLUSH_THRESHOLD) {
            this.flush();
        } else {
            wakeup.run();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if (b == null || len == 0) {
            return;
        }
        if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = 0 ; i < len ; i++) {
            byte c = b[off + i];
            if (BACKSPACE == c) {
                this.backspaceNum.incrementAndGet();
            } else {
                //int的高24位是无效的，实际只用到8位
                buffer[++offset] = c;
                if (offset > FLUSH_THRESHOLD) {
                    this.flush();
                }
            }
        }
        wakeup.run();
    }

    /**
     * IO 刷新
     */
    @Override
    public void flush() {
        this.backspaceHandler.handle(this.backspaceNum.getAndSet(0));
        //打印
        this.print();
    }

    /**
     * 打印文本
     */
    private void print() {
        //一行，清空buffer，打印一行
        if (offset > 0) {
            String text = new String(buffer, 0, offset + 1);
            int index = text.indexOf(ANSI_BEGIN);
            if (offset < (ANSI_CHECK_THRESHOLD)) {
                while (-1 != index) {
                    //控制符未完成
                    index = text.indexOf('m', index);
                    if (-1 == index) {
                        return;
                    }
                    index = text.indexOf(ANSI_BEGIN, index + ANSI_BEGIN.length());
                }
            }
            offset = NO_BUFFER_OFFSET;
            printHandler.handle(text);
        }
    }
}
