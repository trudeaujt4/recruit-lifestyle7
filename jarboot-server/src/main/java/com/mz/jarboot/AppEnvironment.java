package com.mz.jarboot;

import com.mz.jarboot.api.constant.CommonConst;
import com.mz.jarboot.api.exception.JarbootRunException;
import com.mz.jarboot.common.AnsiLog;
import com.mz.jarboot.common.CacheDirHelper;
import com.mz.jarboot.common.utils.StringUtils;
import com.mz.jarboot.common.utils.VMUtils;
import com.mz.jarboot.common.utils.VersionUtils;
import java.io.File;

/**
 * check file is all exist and environment is jdk.
 * @author majianzheng
 */
public class AppEnvironment {
    /**
     * 初始化并检查环境
     */
    public static void initAndCheck() {
        //初始化工作目录
        String homePath = System.getenv(CommonConst.JARBOOT_HOME);
        if (null == homePath || homePath.isEmpty()) {
            homePath = System.getProperty(CommonConst.JARBOOT_HOME, null);
            if (null == homePath) {
                homePath = System.getProperty("user.home") + File.separator + CommonConst.JARBOOT_NAME;
            }
        }
        //检查安装路径是否存在空格
        if (StringUtils.containsWhitespace(homePath)) {
            AnsiLog.error("Jarboot所在目录的全路径中存在空格！");
            System.exit(-1);
        }

        if (null == System.getProperty(CommonConst.JARBOOT_HOME, null)) {
            System.setProperty(CommonConst.JARBOOT_HOME, homePath);
        }
        final String ver = "v" + VersionUtils.version;
        System.setProperty("application.version", ver);
        //derby数据库驱动的日志文件位置
        final String derbyLog = homePath + File.separator + "logs" + File.separator + "derby.log";
        System.setProperty("derby.stream.error.file", derbyLog);

        //环境检查
        try {
            checkEnvironment();
            //初始化cache目录
            CacheDirHelper.init();
        } catch (Exception e) {
            AnsiLog.error(e);
            System.exit(-1);
        }
    }

    private static void checkEnvironment() {
        String binDir = System.getProperty(CommonConst.JARBOOT_HOME) + File.separator + "bin";
        //先检查jarboot-agent.jar文件
        checkFile(binDir, CommonConst.AGENT_JAR_NAME);
        //检查jarboot-core.jar文件，该文件必须和jarboot-agent.jar处于同一目录下
        checkFile(binDir, "jarboot-core.jar");
        checkFile(binDir, "jarboot-spy.jar");

        //检查是否是jdk环境，是否存在tools.jar
        if (VMUtils.getInstance().check()) {
            return;
        }
        throw new JarbootRunException("检查环境错误，当前运行环境未安装jdk，请检查环境变量是否配置，可能是使用了jre环境。");
    }

    private static void checkFile(String dir, String fileName) {
        File file = new File(dir, fileName);
        if (!file.exists()) {
            throw new JarbootRunException("检查环境错误，未发现" + fileName);
        }
        if (!file.isFile()) {
            throw new JarbootRunException(String.format("检查环境错误，%s不是文件类型。", fileName));
        }
    }

    private AppEnvironment() { }
}
