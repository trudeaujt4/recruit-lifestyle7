package com.mz.jarboot.core.cmd.impl;

import com.mz.jarboot.core.basic.EnvironmentContext;
import com.mz.jarboot.core.constant.CoreConstant;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author majianzheng
 */
@SuppressWarnings("all")
class ClassDumpTransformer implements ClassFileTransformer {
    private static final Logger logger = LoggerFactory.getLogger(CoreConstant.LOG_NAME);

    private Set<Class<?>> classesToEnhance;
    private Map<Class<?>, File> dumpResult;
    private File jarbootLogHome;

    private File directory;

    public ClassDumpTransformer(Set<Class<?>> classesToEnhance) {
        this(classesToEnhance, null);
    }

    public ClassDumpTransformer(Set<Class<?>> classesToEnhance, File directory) {
        this.classesToEnhance = classesToEnhance;
        this.dumpResult = new HashMap<Class<?>, File>();
        this.jarbootLogHome = new File(EnvironmentContext.getJarbootHome() + File.separator + "logs");
        this.directory = directory;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        if (classesToEnhance.contains(classBeingRedefined)) {
            dumpClassIfNecessary(classBeingRedefined, classfileBuffer);
        }
        return null;
    }

    public Map<Class<?>, File> getDumpResult() {
        return dumpResult;
    }

    private void dumpClassIfNecessary(Class<?> clazz, byte[] data) {
        String className = clazz.getName();
        ClassLoader classLoader = clazz.getClassLoader();
        String classDumpDir = "classdump";

        // 创建类所在的包路径
        File dumpDir = null;
        if (directory != null) {
            dumpDir = directory;
        } else {
            dumpDir = new File(jarbootLogHome, classDumpDir);
        }
        if (!dumpDir.mkdirs() && !dumpDir.exists()) {
            logger.warn("create dump directory:{} failed.", dumpDir.getAbsolutePath());
            return;
        }

        String fileName;
        if (classLoader != null) {
            fileName = classLoader.getClass().getName() + "-" + Integer.toHexString(classLoader.hashCode()) +
                    File.separator + className.replace(".", File.separator) + ".class";
        } else {
            fileName = className.replace(".", File.separator) + ".class";
        }

        File dumpClassFile = new File(dumpDir, fileName);

        // 将类字节码写入文件
        try {
            FileUtils.writeByteArrayToFile(dumpClassFile, data);
            dumpResult.put(clazz, dumpClassFile);
        } catch (IOException e) {
            logger.warn("dump class:{} to file {} failed.", className, dumpClassFile, e);
        }
    }
}
