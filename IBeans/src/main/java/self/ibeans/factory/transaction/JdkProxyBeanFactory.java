package self.ibeans.factory.transaction;

import self.ibeans.anno.Work;
import self.ibeans.util.TransactionManager;

import java.lang.reflect.Proxy;

/**
 * @author Y-cs
 * @date 2021/4/21 18:34
 */
public class JdkProxyBeanFactory implements ProxyFactory {

    private TransactionManager transactionManager;

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    private JdkProxyBeanFactory() {
    }

    static class ProxyBeanFactoryInstance {
        private static JdkProxyBeanFactory proxyBeanFactory = new JdkProxyBeanFactory();
    }

    public static ProxyFactory getInstance() {
        return ProxyBeanFactoryInstance.proxyBeanFactory;
    }

    @Override
    public Object getProxy(Object obj) {
        // 获取代理对象
        return Proxy.newProxyInstance(obj.getClass().getClassLoader(), obj.getClass().getInterfaces(),
                (proxy, method, args) -> {
                    Object result = null;
                    Work annotation = method.getAnnotation(Work.class);
                    if (annotation != null) {
                        try {
                            // 开启事务(关闭事务的自动提交)
                            transactionManager.beginTransaction();
                            result = method.invoke(obj, args);
                            // 提交事务
                            transactionManager.commit();
                        } catch (Exception e) {
                            e.printStackTrace();
                            // 回滚事务
                            transactionManager.rollback();
                            // 抛出异常便于上层servlet捕获
                            throw e;
                        }
                    } else {
                        result = method.invoke(obj, args);
                    }
                    return result;
                });
    }
}
