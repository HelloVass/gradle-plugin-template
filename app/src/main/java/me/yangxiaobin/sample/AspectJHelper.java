package me.yangxiaobin.sample;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
class AspectJHelper {

    @Before("execution(public * *(..))")
    public void beforeExecute() {
        System.out.println("---> beforeExecute ");
    }

//    public void afterExecute() {
//        System.out.println("---> afterExecute");
//    }

}