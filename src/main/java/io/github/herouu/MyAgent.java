package io.github.herouu;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentHashMap;

import javassist.*;

public class MyAgent implements ClassFileTransformer {

    private static final ConcurrentHashMap<ClassLoader, ClassPool> classPools = new ConcurrentHashMap<>();

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("MethodTraceAgent is loaded!");
        inst.addTransformer(new MyAgent());

    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
            ClassPool pool = getClassPool(loader);
            String replace = className.replace('/', '.');
            if (replace.equals("org.springframework.cloud.openfeign.FeignClientsRegistrar")) {
                CtClass cc = pool.get(replace);
                for (CtMethod m : cc.getDeclaredMethods()) {
                    if (m.getMethodInfo().getName().equals("getUrl")) {
                        int modifiers = m.getModifiers();
                        if ((modifiers & Modifier.STATIC) == 0) {
                            System.out.println(m.getMethodInfo() + " is not static.");

                            m.insertAfter("{ System.out.println(\"方法执行完成\");" +
                                    "return \"https://www.baidu.com\"; }");
                            return cc.toBytecode();
                        }
                    }
                }
            }

            if (replace.equals("io.github.herouu.hellotest.HelloController")) {
                CtClass cc = pool.get(replace);
                for (CtMethod m : cc.getDeclaredMethods()) {
                    m.insertBefore("System.out.println(\"Called method: \" + this.getClass().getName() + \".\" + $sig + \" in class: \" + this.getClass().getName());");
                }
                return cc.toBytecode();
            }
        } catch (NotFoundException | CannotCompileException | IOException e) {
            throw new RuntimeException(e);
        }
        return classfileBuffer;
    }

    private ClassPool getClassPool(ClassLoader loader) {
        return classPools.computeIfAbsent(loader, k -> ClassPool.getDefault());
    }
}