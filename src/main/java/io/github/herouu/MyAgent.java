package io.github.herouu;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.watch.SimpleWatcher;
import cn.hutool.core.io.watch.WatchMonitor;
import cn.hutool.core.io.watch.watchers.DelayWatcher;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import javassist.*;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MyAgent implements ClassFileTransformer {

    private static final ConcurrentHashMap<ClassLoader, ClassPool> CLASS_POOLS = new ConcurrentHashMap<>();

    private final Map<String, Object> args;

    public MyAgent(Map<String, Object> args) {
        this.args = args;
    }

    private static final String REGISTER_WITH_EUREKA = "eureka.client.register-with-eureka";
    private static final String ACTIVE = "spring.profiles.active";

    public static void premain(String agentArgs, Instrumentation inst) {
        if (StrUtil.isEmpty(System.getProperty(REGISTER_WITH_EUREKA))) {
            System.setProperty(REGISTER_WITH_EUREKA, "false");
        }
        if (StrUtil.isEmpty(System.getProperty(ACTIVE))) {
            System.setProperty(ACTIVE, "uat");
        }
        Console.log("{}={}", REGISTER_WITH_EUREKA, System.getProperty(REGISTER_WITH_EUREKA));
        Console.log("{}={}", ACTIVE, System.getProperty(ACTIVE));
        System.setProperty("eureka.client.register-with-eureka", "false");
        Console.log("feign url转换agent已加载！！！ 启动参数：{}", agentArgs);
        Map<String, Object> map = transArgs(agentArgs);
        String configFilePath = Convert.toStr(map.get("configFile"));
        // 初始化
        AnotherClass.refreshYamlConfig(configFilePath);
        // 文件修改刷新
        monitor(configFilePath);
        inst.addTransformer(new MyAgent(map));

    }

    private static Map<String, Object> transArgs(String agentArgs) {
        Map<String, Object> map = new HashMap<>();
        List<String> split = StrUtil.split(agentArgs, StrUtil.COMMA);
        for (String s : split) {
            String key = StrUtil.subBefore(s, "=", false);
            String value = StrUtil.subAfter(s, "=", false);
            map.put(key, value);
        }
        return map;
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
    }

    private static void monitor(String filePath) {
        File file = FileUtil.file(filePath);
        // 这里只监听文件或目录的修改事件
        WatchMonitor watchMonitor = WatchMonitor.create(file, WatchMonitor.ENTRY_MODIFY);

        SimpleWatcher simpleWatcher = new SimpleWatcher() {

            @Override
            public void onModify(WatchEvent<?> event, Path currentPath) {
                Console.log("文件：{} 已变更!", filePath);
                AnotherClass.refreshYamlConfig(filePath);
            }
        };
        watchMonitor.setWatcher(new DelayWatcher(simpleWatcher, 500));
        watchMonitor.start();
    }

    private ClassPool getClassPool(ClassLoader loader) {
        return CLASS_POOLS.computeIfAbsent(loader, k -> ClassPool.getDefault());
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {

        try {
            return trans(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
        } catch (NotFoundException e) {
            Console.log(e, "NotFoundException");
        } catch (CannotCompileException e) {
            Console.log(e, "CannotCompileException");
        } catch (IOException e) {
            Console.log(e, "IOException");
        }
        return classfileBuffer;
    }


    private byte[] trans(ClassLoader loader, String className,
                         Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                         byte[] classfileBuffer) throws NotFoundException, CannotCompileException, IOException {
        ClassPool classPool = getClassPool(loader);

        classPool.importPackage("io.github.herouu");
        String replace = className.replace('/', '.');

        if (StrUtil.equals(replace, "org.springframework.cloud.openfeign.FeignClientsRegistrar")) {
            CtClass cc = classPool.get(replace);
            String configFile = Convert.toStr(args.get("configFile"));
            CtField field = new CtField(classPool.get("java.lang.String"), "configFile", cc);
            field.setModifiers(Modifier.PRIVATE);
            cc.addField(field, CtField.Initializer.constant(configFile));

            for (CtMethod m : cc.getDeclaredMethods()) {
                if ("getUrl".equals(m.getMethodInfo().getName())) {

                    int modifiers = m.getModifiers();
                    if ((modifiers & Modifier.STATIC) == 0) {
                        CtClass[] parameterTypes = m.getParameterTypes();
                        if (ArrayUtil.length(parameterTypes) == 1) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("{");
                            sb.append("return AnotherClass.exec(configFile,$1,$_);");
                            sb.append("}");
                            m.insertAfter(sb.toString());
                        } else {
                            StringBuilder sb = new StringBuilder();
                            sb.append("{");
                            sb.append("return AnotherClass.exec(configFile,$2,$_);");
                            sb.append("}");
                            m.insertAfter(sb.toString());
                        }
                        return cc.toBytecode();
                    }
                }
            }
        }

        if (StrUtil.startWith(replace, "feign.Target$HardCodedTarget")) {
            CtClass cc = classPool.get(replace);
            String configFile = Convert.toStr(args.get("configFile"));
            CtField field = new CtField(classPool.get("java.lang.String"), "configFile", cc);
            field.setModifiers(Modifier.PRIVATE);
            cc.addField(field, CtField.Initializer.constant(configFile));

            for (CtMethod m : cc.getDeclaredMethods()) {
                if ("url".equals(m.getMethodInfo().getName())) {
                    m.insertAfter("{return AnotherClass.currentUrl(configFile,name,$_);}");
                    return cc.toBytecode();
                }
            }
        }

        if (StrUtil.startWith(replace, "feign.Feign$Builder")) {
            CtClass cc = classPool.get(replace);
            String configFile = Convert.toStr(args.get("configFile"));
            CtField field = new CtField(classPool.get("java.lang.String"), "configFile", cc);
            field.setModifiers(Modifier.PRIVATE);
            cc.addField(field, CtField.Initializer.constant(configFile));
            for (CtMethod m : cc.getDeclaredMethods()) {
                if ("build".equals(m.getMethodInfo().getName())) {
                    Console.log("注入feign.Feign$Builder.build方法");
                    StringBuilder sb = new StringBuilder();
                    sb.append("{");
                    sb.append("client = new feign.okhttp.OkHttpClient(new okhttp3.OkHttpClient.Builder().dns(new CustomDns(configFile)).build());");
                    sb.append("}");
                    m.insertBefore(sb.toString());
                    return cc.toBytecode();
                }
            }
            return cc.toBytecode();
        }
        return classfileBuffer;
    }

}