package self.ibeans.factory.transaction;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import self.ibeans.anno.Work;
import self.ibeans.util.TransactionManager;

/**
 * @author Y-cs
 * @date 2021/4/21 18:34
 */
public class CgLibProxyBeanFactory implements ProxyFactory {

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    private TransactionManager transactionManager ;

    private CgLibProxyBeanFactory() {
    }

    static class ProxyBeanFactoryInstance {
        private static CgLibProxyBeanFactory proxyBeanFactory = new CgLibProxyBeanFactory();
    }

    public static CgLibProxyBeanFactory getInstance() {
        return ProxyBeanFactoryInstance.proxyBeanFactory;
    }


    @Override
    public Object getProxy(Object obj) {
        return Enhancer.create(obj.getClass(), (MethodInterceptor) (o, method, objects, methodProxy) -> {
            Object result = null;
            Work annotation = method.getAnnotation(Work.class);
            if (annotation != null) {
                try {
                    // 开启事务(关闭事务的自动提交)
                    transactionManager.beginTransaction();
                    result = method.invoke(obj, objects);
                    // 提交事务
                    transactionManager.commit();
                } catch (Exception e) {
                    e.printStackTrace();
                    // 回滚事务
                    transactionManager.rollback();
                    // 抛出异常便于上层servlet捕获
                    throw e;
                }
            }else{
                result = method.invoke(obj, objects);
            }
            return result;
        });
    }


}
