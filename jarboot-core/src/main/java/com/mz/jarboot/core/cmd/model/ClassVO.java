package com.mz.jarboot.core.cmd.model;

/**
 * @author majianzheng
 * 以下代码基于开源项目Arthas适配修改
 */
public class ClassVO {

    private String name;
    private String[] classloader;
    private String classLoaderHash;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getClassloader() {
        return classloader;
    }

    public void setClassloader(String[] classloader) {
        this.classloader = classloader;
    }

    public String getClassLoaderHash() {
        return classLoaderHash;
    }

    public void setClassLoaderHash(String classLoaderHash) {
        this.classLoaderHash = classLoaderHash;
    }
}
