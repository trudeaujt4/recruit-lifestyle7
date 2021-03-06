package com.mz.jarboot.core.advisor;

import com.alibaba.bytekit.asm.MethodProcessor;
import com.alibaba.bytekit.asm.interceptor.InterceptorProcessor;
import com.alibaba.bytekit.asm.interceptor.parser.DefaultInterceptorClassParser;
import com.alibaba.bytekit.asm.location.Location;
import com.alibaba.bytekit.asm.location.LocationType;
import com.alibaba.bytekit.asm.location.MethodInsnNodeWare;
import com.alibaba.bytekit.asm.location.filter.GroupLocationFilter;
import com.alibaba.bytekit.asm.location.filter.InvokeCheckLocationFilter;
import com.alibaba.bytekit.asm.location.filter.InvokeContainLocationFilter;
import com.alibaba.bytekit.asm.location.filter.LocationFilter;
import com.alibaba.bytekit.utils.AsmOpUtils;
import com.alibaba.bytekit.utils.AsmUtils;
import com.alibaba.deps.org.objectweb.asm.ClassReader;
import com.alibaba.deps.org.objectweb.asm.Opcodes;
import com.alibaba.deps.org.objectweb.asm.Type;
import com.alibaba.deps.org.objectweb.asm.tree.AbstractInsnNode;
import com.alibaba.deps.org.objectweb.asm.tree.ClassNode;
import com.alibaba.deps.org.objectweb.asm.tree.MethodInsnNode;
import com.alibaba.deps.org.objectweb.asm.tree.MethodNode;
import com.mz.jarboot.api.exception.JarbootRunException;
import com.mz.jarboot.core.GlobalOptions;
import com.mz.jarboot.core.basic.EnvironmentContext;
import com.mz.jarboot.core.utils.JarbootCheckUtils;
import com.mz.jarboot.core.utils.LogUtils;
import com.mz.jarboot.core.utils.ObjectUtils;
import com.mz.jarboot.core.utils.SearchUtils;
import com.mz.jarboot.core.utils.affect.EnhancerAffect;
import com.mz.jarboot.core.utils.matcher.Matcher;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.jarboot.SpyAPI;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.*;
import static java.lang.System.arraycopy;

/**
 * ????????????????????????
 * @author majianzheng
 * ??????????????????????????????Arthas????????????
 */
@SuppressWarnings("java:S3740")
public class ClassEnhancer implements ClassFileTransformer {
    private static final Logger logger = LogUtils.getLogger();

    private final AdviceListener listener;
    private final boolean isTracing;
    private final boolean skipJDKTrace;
    private final Matcher classNameMatcher;
    private final Matcher classNameExcludeMatcher;
    private final Matcher methodNameMatcher;
    private final EnhancerAffect affect;
    private Set<Class<?>> matchingClasses = null;

    // ????????????????????????
    private static final Map<Class<?>, Object> classBytesCache = new WeakHashMap<>();
    private static SpyImpl spyImpl = new SpyImpl();

    static {
        SpyAPI.setSpy(spyImpl);
    }


    public ClassEnhancer(AdviceListener listener, boolean isTracing, boolean skipJDKTrace, Matcher classNameMatcher,
                         Matcher classNameExcludeMatcher,
                         Matcher methodNameMatcher) {
        this.listener = listener;
        this.isTracing = isTracing;
        this.skipJDKTrace = skipJDKTrace;
        this.classNameMatcher = classNameMatcher;
        this.classNameExcludeMatcher = classNameExcludeMatcher;
        this.methodNameMatcher = methodNameMatcher;
        this.affect = new EnhancerAffect();
        affect.setListenerId(listener.id());
    }

    @SuppressWarnings({"squid:S1181", "java:S3776", "squid:S1141", "squid:S1168", "squid:S135", "java:S1066", "PointlessBooleanExpression"})
    @Override
    public byte[] transform(final ClassLoader inClassLoader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
            // ??????classloader??????????????? SpyAPI?????????????????????????????????
            try {
                if (inClassLoader != null) {
                    inClassLoader.loadClass(SpyAPI.class.getName());
                }
            } catch (Throwable e) {
                logger.error("the classloader can not load SpyAPI, ignore it. classloader: {}, className: {}",
                        inClassLoader.getClass().getName(), className, e);
                return null;
            }

            // ??????????????????????????????????????????????????????????????????????????????
            if (matchingClasses != null && !matchingClasses.contains(classBeingRedefined)) {
                return null;
            }

            ClassNode classNode = new ClassNode(Opcodes.ASM9);
            ClassReader classReader = AsmUtils.toClassNode(classfileBuffer, classNode);
            classNode = AsmUtils.removeJSRInstructions(classNode);

            // ?????????????????????
            DefaultInterceptorClassParser defaultInterceptorClassParser = new DefaultInterceptorClassParser();

            final List<InterceptorProcessor> interceptorProcessors = new ArrayList<>();

            interceptorProcessors.addAll(defaultInterceptorClassParser.parse(SpyInterceptors.SpyInterceptor1.class));
            interceptorProcessors.addAll(defaultInterceptorClassParser.parse(SpyInterceptors.SpyInterceptor2.class));
            interceptorProcessors.addAll(defaultInterceptorClassParser.parse(SpyInterceptors.SpyInterceptor3.class));

            if (this.isTracing) {
                if (this.skipJDKTrace) {
                    interceptorProcessors.addAll(defaultInterceptorClassParser.parse(SpyInterceptors.SpyTraceExcludeJDKInterceptor1.class));
                    interceptorProcessors.addAll(defaultInterceptorClassParser.parse(SpyInterceptors.SpyTraceExcludeJDKInterceptor2.class));
                    interceptorProcessors.addAll(defaultInterceptorClassParser.parse(SpyInterceptors.SpyTraceExcludeJDKInterceptor3.class));
                } else {
                    interceptorProcessors.addAll(defaultInterceptorClassParser.parse(SpyInterceptors.SpyTraceInterceptor1.class));
                    interceptorProcessors.addAll(defaultInterceptorClassParser.parse(SpyInterceptors.SpyTraceInterceptor2.class));
                    interceptorProcessors.addAll(defaultInterceptorClassParser.parse(SpyInterceptors.SpyTraceInterceptor3.class));
                }
            }

            List<MethodNode> matchedMethods = new ArrayList<>();
            for (MethodNode methodNode : classNode.methods) {
                if (!isIgnore(methodNode, methodNameMatcher)) {
                    matchedMethods.add(methodNode);
                }
            }

            if (AsmUtils.isEnhancerByCGLIB(className)) {
                for (MethodNode methodNode : matchedMethods) {
                    if (AsmUtils.isConstructor(methodNode)) {
                        AsmUtils.fixConstructorExceptionTable(methodNode);
                    }
                }
            }

            // ?????????????????????????????? spy???????????????????????????????????????
            GroupLocationFilter groupLocationFilter = new GroupLocationFilter();

            LocationFilter enterFilter = new InvokeContainLocationFilter(Type.getInternalName(SpyAPI.class), "atEnter",
                    LocationType.ENTER);
            LocationFilter existFilter = new InvokeContainLocationFilter(Type.getInternalName(SpyAPI.class), "atExit",
                    LocationType.EXIT);
            LocationFilter exceptionFilter = new InvokeContainLocationFilter(Type.getInternalName(SpyAPI.class),
                    "atExceptionExit", LocationType.EXCEPTION_EXIT);

            groupLocationFilter.addFilter(enterFilter);
            groupLocationFilter.addFilter(existFilter);
            groupLocationFilter.addFilter(exceptionFilter);

            LocationFilter invokeBeforeFilter = new InvokeCheckLocationFilter(Type.getInternalName(SpyAPI.class),
                    "atBeforeInvoke", LocationType.INVOKE);
            LocationFilter invokeAfterFilter = new InvokeCheckLocationFilter(Type.getInternalName(SpyAPI.class),
                    "atInvokeException", LocationType.INVOKE_COMPLETED);
            LocationFilter invokeExceptionFilter = new InvokeCheckLocationFilter(Type.getInternalName(SpyAPI.class),
                    "atInvokeException", LocationType.INVOKE_EXCEPTION_EXIT);
            groupLocationFilter.addFilter(invokeBeforeFilter);
            groupLocationFilter.addFilter(invokeAfterFilter);
            groupLocationFilter.addFilter(invokeExceptionFilter);

            for (MethodNode methodNode : matchedMethods) {
                if (AsmUtils.isNative(methodNode)) {
                    final String method = AsmUtils.methodDeclaration(Type.getObjectType(classNode.name), methodNode);
                    logger.info("ignore native method: {}", method);
                    continue;
                }
                // ?????????????????? atBeforeInvoke ???????????????????????????????????????trace???????????????????????????????????????????????? listener
                if(AsmUtils.containsMethodInsnNode(methodNode, Type.getInternalName(SpyAPI.class), "atBeforeInvoke")) {
                    for (AbstractInsnNode insnNode = methodNode.instructions.getFirst(); insnNode != null; insnNode = insnNode
                            .getNext()) {
                        if (insnNode instanceof MethodInsnNode) {
                            final MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                            if(this.skipJDKTrace) {
                                if(methodInsnNode.owner.startsWith("java/")) {
                                    continue;
                                }
                            }
                            // ???????????????box????????????????????????
                            if(AsmOpUtils.isBoxType(Type.getObjectType(methodInsnNode.owner))) {
                                continue;
                            }
                            AdviceListenerManager.registerTraceAdviceListener(inClassLoader, className,
                                    methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc, listener);
                        }
                    }
                } else {
                    MethodProcessor methodProcessor = new MethodProcessor(classNode, methodNode, groupLocationFilter);
                    for (InterceptorProcessor interceptor : interceptorProcessors) {
                        try {
                            List<Location> locations = interceptor.process(methodProcessor);
                            for (Location location : locations) {
                                if (location instanceof MethodInsnNodeWare) {
                                    MethodInsnNodeWare methodInsnNodeWare = (MethodInsnNodeWare) location;
                                    MethodInsnNode methodInsnNode = methodInsnNodeWare.methodInsnNode();

                                    AdviceListenerManager.registerTraceAdviceListener(inClassLoader, className,
                                            methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc, listener);
                                }
                            }

                        } catch (Exception e) {
                            logger.error("enhancer error, class: {}, method: {}, interceptor: {}",
                                    classNode.name, methodNode.name, interceptor.getClass().getName(), e);
                        }
                    }
                }

                // enter/exist ??????????????? listener
                AdviceListenerManager.registerAdviceListener(inClassLoader, className, methodNode.name, methodNode.desc,
                        listener);
                affect.addMethodAndCount(inClassLoader, className, methodNode.name, methodNode.desc);
            }

            if (AsmUtils.getMajorVersion(classNode.version) < 49) {
                classNode.version = AsmUtils.setMajorVersion(classNode.version, 49);
            }

            byte[] enhanceClassByteArray = AsmUtils.toBytes(classNode, inClassLoader, classReader);

            // ????????????????????????
            classBytesCache.put(classBeingRedefined, new Object());

            // dump the class
            dumpClassIfNecessary(className, enhanceClassByteArray, affect);

            // ????????????
            affect.cCnt(1);

            return enhanceClassByteArray;
        } catch (Exception t) {
            logger.warn("transform loader[{}]:class[{}] failed.", inClassLoader, className, t);
            affect.setThrowable(t);
        }

        return null;
    }

    /**
     * ??????????????????
     */
    private boolean isAbstract(int access) {
        return (Opcodes.ACC_ABSTRACT & access) == Opcodes.ACC_ABSTRACT;
    }

    /**
     * ??????????????????
     */
    private boolean isIgnore(MethodNode methodNode, Matcher methodNameMatcher) {
        return null == methodNode ||
                isAbstract(methodNode.access) ||
                !methodNameMatcher.matching(methodNode.name) ||
                JarbootCheckUtils.isEquals(methodNode.name, "<clinit>");
    }

    /**
     * dump class to file
     */
    private static void dumpClassIfNecessary(String className, byte[] data, EnhancerAffect affect) {
        if (!GlobalOptions.isDump) {
            return;
        }
        final File dumpClassFile = new File("./jarboot-class-dump/" + className + ".class");
        final File classPath = new File(dumpClassFile.getParent());

        // ???????????????????????????
        if (!classPath.mkdirs() && !classPath.exists()) {
            logger.warn("create dump classpath:{} failed.", classPath);
            return;
        }

        // ???????????????????????????
        try {
            FileUtils.writeByteArrayToFile(dumpClassFile, data);
            affect.addClassDumpFile(dumpClassFile);
            if (GlobalOptions.verbose) {
                logger.info("dump enhanced class: {}, path: {}", className, dumpClassFile);
            }
        } catch (IOException e) {
            logger.warn("dump class:{} to file {} failed.", className, dumpClassFile, e);
        }

    }

    /**
     * ????????????????????????
     *
     * @param classes ?????????
     */
    private void filter(Set<Class<?>> classes) {
        classes.removeIf(clazz -> null == clazz ||
                isSelf(clazz) ||
                isUnsafeClass(clazz) ||
                isUnsupportedClass(clazz) ||
                isExclude(clazz));
    }

    private boolean isExclude(Class<?> clazz) {
        if (this.classNameExcludeMatcher != null) {
            return classNameExcludeMatcher.matching(clazz.getName());
        }
        return false;
    }

    /**
     * ??????????????????????????????
     */
    private static boolean isSelf(Class<?> clazz) {
        return null != clazz && JarbootCheckUtils.isEquals(clazz.getClassLoader(), ClassEnhancer.class.getClassLoader());
    }

    /**
     * ????????????unsafe???
     */
    private static boolean isUnsafeClass(Class<?> clazz) {
        return !GlobalOptions.isUnsafe && clazz.getClassLoader() == null;
    }

    /**
     * ????????????????????????????????????
     */
    private static boolean isUnsupportedClass(Class<?> clazz) {
        return clazz.isArray() || (clazz.isInterface() && !GlobalOptions.isSupportDefaultMethod) || clazz.isEnum()
                || clazz.equals(Class.class) || clazz.equals(Integer.class) || clazz.equals(Method.class) || ObjectUtils.isLambdaClass(clazz);
    }


    @SuppressWarnings({"squid:S1141", "squid:S1193", "java:S3776", "unchecked"})
    public synchronized EnhancerAffect enhance(final Instrumentation inst) throws UnmodifiableClassException {
        // ??????????????????????????????
        this.matchingClasses = GlobalOptions.isDisableSubClass
                ? SearchUtils.searchClass(inst, classNameMatcher)
                : SearchUtils.searchSubClass(inst, SearchUtils.searchClass(inst, classNameMatcher));

        // ??????????????????????????????
        filter(matchingClasses);

        logger.info("enhance matched classes: {}", matchingClasses);

        affect.setTransformer(this);

        try {
            EnvironmentContext.getTransformerManager().addTransformer(this, isTracing);

            // ????????????
            if (GlobalOptions.isBatchReTransform) {
                final int size = matchingClasses.size();
                final Class<?>[] classArray = new Class<?>[size];
                arraycopy(matchingClasses.toArray(), 0, classArray, 0, size);
                if (classArray.length > 0) {
                    inst.retransformClasses(classArray);
                    final String classes = Arrays.toString(classArray);
                    logger.info("Success to batch transform classes: {}", classes);
                }
            } else {
                // for each ??????
                for (Class<?> clazz : matchingClasses) {
                    try {
                        inst.retransformClasses(clazz);
                        logger.info("Success to transform class: {}", clazz);
                    } catch (Exception t) {
                        logger.warn("retransform {} failed.", clazz, t);
                        if (t instanceof UnmodifiableClassException) {
                            throw (UnmodifiableClassException) t;
                        } else if (t instanceof RuntimeException) {
                            throw (RuntimeException) t;
                        } else {
                            throw new JarbootRunException(t);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Enhancer error, matchingClasses: {}", matchingClasses, e);
            affect.setThrowable(e);
        }

        return affect;
    }

    /**
     * ???????????????Class
     *
     * @param inst             inst
     * @param classNameMatcher ????????????
     * @return ??????????????????
     * @throws UnmodifiableClassException Unmodifiable class exception
     */
    public static synchronized EnhancerAffect reset(final Instrumentation inst, final Matcher classNameMatcher)
            throws UnmodifiableClassException {

        final EnhancerAffect affect = new EnhancerAffect();
        final Set<Class<?>> enhanceClassSet = new HashSet<>();

        for (Class<?> classInCache : classBytesCache.keySet()) {
            if (classNameMatcher.matching(classInCache.getName())) {
                enhanceClassSet.add(classInCache);
            }
        }

        try {
            enhance(inst, enhanceClassSet);
            logger.info("Success to reset classes: {}", enhanceClassSet);
        } finally {
            for (Class<?> resetClass : enhanceClassSet) {
                classBytesCache.remove(resetClass);
                affect.cCnt(1);
            }
        }

        return affect;
    }

    // ????????????
    private static void enhance(Instrumentation inst, Set<Class<?>> classes)
            throws UnmodifiableClassException {
        int size = classes.size();
        Class<?>[] classArray = new Class<?>[size];
        arraycopy(classes.toArray(), 0, classArray, 0, size);
        if (classArray.length > 0) {
            inst.retransformClasses(classArray);
        }
    }
}
