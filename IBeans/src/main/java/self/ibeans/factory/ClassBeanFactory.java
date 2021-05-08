package self.ibeans.factory;

import self.ibeans.anno.AutoSet;
import self.ibeans.anno.BeanType;
import self.ibeans.anno.Work;
import self.ibeans.factory.transaction.ProxyFactory;
import self.ibeans.reflex.PackageUtil;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Y-cs
 * @date 2021/4/19 14:13
 */
public class ClassBeanFactory extends BeanFactory {

    private final Map<String, Object> beanMap = new HashMap<>();
    private final Map<Class<?>, List<Object>> beanClassMap = new HashMap<>();

    private Set<Class<?>> clazzSet;
    private LinkedBlockingQueue<Class<?>> createQueue = new LinkedBlockingQueue<Class<?>>();
    private Set<String> finishBeanName = new HashSet<>();

    private ProxyFactory transactionProxyFactory;

    public ClassBeanFactory(Class<?> clazz) {
        setClasses(clazz);
    }

    private void setClasses(Class<?> clazz) {
        try {
            clazzSet = PackageUtil.getInstance().getClassFromPackage(clazz.getPackage().getName(), (type) -> !type.isInterface() && type.getAnnotation(BeanType.class) != null);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("create factory error " + e);
        }
    }

    public void scan() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        //首次对bean对象创建    构建   包括构建注入
        this.firstCreate();
        //重试不成功   重试构建    包括构建注入
        this.doReplay();
        //set注入或者  参数注入
        this.doSetBean();
    }

    public void setTransactionProxyFactory(ProxyFactory transactionProxyFactory) {
        this.transactionProxyFactory = transactionProxyFactory;
    }

    private void doSetBean() throws IllegalAccessException, InvocationTargetException {
        for (String beanName : beanMap.keySet()) {
            Object o = beanMap.get(beanName);
            Method[] methods = o.getClass().getMethods();
            //方法注入
            for (Method method : methods) {
                AutoSet autoSet = method.getAnnotation(AutoSet.class);
                if (autoSet != null) {
                    doSetMethod(o, method);
                }
            }
            //属性注入declaredFields = {Field[19]@1175}
            Field[] declaredFields = o.getClass().getDeclaredFields();
            for (Field declaredField : declaredFields) {
                AutoSet autoSet = declaredField.getAnnotation(AutoSet.class);
                if (autoSet != null) {
                    doField(o, declaredField);
                }
            }
            if (!this.isFinish(beanName)) {
                throw new RuntimeException("we can't injection " + beanName);
            }
        }
    }

    private void doField(Object o, Field declaredField) throws IllegalAccessException {
        declaredField.setAccessible(true);
        String fieldName = getFirstLowClassName(declaredField.getType());
        Object fieldObj = beanMap.get(fieldName);
        if (fieldObj == null) {
            throw new RuntimeException("this error is field obj not create");
        }
        declaredField.set(o, fieldObj);
    }

    private void doSetMethod(Object o, Method method) throws InvocationTargetException, IllegalAccessException {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] objs = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object obj = beanMap.get(this.getFirstLowClassName(parameterTypes[i]));
            if (obj != null) {
                objs[i] = obj;
            } else {
                throw new RuntimeException("this error is field obj not create");
            }
        }
        method.invoke(o, objs);
    }

    private void firstCreate() throws InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> clazz : clazzSet) {
            BeanType beanType = clazz.getAnnotation(BeanType.class);
            if (beanType != null) {
                Object obj = getBean(clazz);
                if (obj == null) {
                    continue;
                }
                this.registerBean(clazz, obj);
            }
        }
    }

    private void doReplay() throws InvocationTargetException, InstantiationException, IllegalAccessException {
        int size = createQueue.size();
        int leftSize;
        //循环队列长度的次数
        for (int i = 0; i < size; i++) {
            //长度为0时所有对象都已经构建
            leftSize = createQueue.size();
            if (createQueue.size() == 0) {
                break;
            }
            //便利队列全部对象
            for (int j = 0; j < createQueue.size(); j++) {
                //取出一个
                Class<?> clazz = createQueue.poll();
                //重试一次
                Object obj = getBean(clazz);
                if (obj == null) {
                    //如果没有成功 则跳出
                    continue;
                }
                //成功则注册
                this.registerBean(clazz, obj);
            }
            //结束后比较两次长度变化  没有变化则证明存在无法构建的对象
            if (createQueue.size() == leftSize) {
                throw new RuntimeException("place check you AutoSet , we can't create bean " + createQueue);
            }
        }
    }

    private Object getBean(Class<?> clazz) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Object obj;
        //获取构造方法
        Constructor<?> autoSetConstructor = null;
        Constructor<?>[] constructors = clazz.getConstructors();
        for (Constructor<?> constructor : constructors) {
            AutoSet autoSet = constructor.getAnnotation(AutoSet.class);
            if (autoSet != null) {
                if (autoSetConstructor == null) {
                    autoSetConstructor = constructor;
                } else {
                    throw new RuntimeException(clazz + " have many @AutoSet constructor");
                }
            }
        }
        //有没有构造方法注入
        if (autoSetConstructor != null) {
            Class<?>[] parameterTypes = autoSetConstructor.getParameterTypes();
            //是否满足所有bean都有了
            if (canRegister(parameterTypes)) {
                obj = createBean(autoSetConstructor, parameterTypes);
            } else {
                //不满足则放入待处理队列
                createQueue.offer(clazz);
                return null;
            }
        } else {
            //使用无参构造生成对象
            try {
                Constructor<?> constructor = clazz.getConstructor();
                obj = constructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(clazz + " not have @AutoSet constructor and not have no args constructors,place check it");
            }
        }

        //代理
        for (Method method : clazz.getMethods()) {
            Work work = method.getAnnotation(Work.class);
            if (work != null) {
                obj = transactionProxyFactory.getProxy(obj);
                break;
            }
        }

        return obj;
    }

    private void registerBean(Class<?> clazz, Object obj) throws IllegalAccessException {

        //id->bean map
        BeanType beanType = clazz.getAnnotation(BeanType.class);
        String beanName = beanType.value();
        if ("".equals(beanName)) {
            beanName = this.getFirstLowClassName(clazz);
        }
        //判断是否完成注入
        if (beanMap.put(beanName, obj) != null) {
            throw new RuntimeException("bean name is repeat " + beanName);
        }
        //class->bean map
        if (beanClassMap.get(clazz) != null) {
            beanClassMap.get(clazz).add(obj);
        } else {
            beanClassMap.put(clazz, new ArrayList<Object>() {{
                add(obj);
            }});
        }
        this.isFinish(beanName);
    }

    private boolean isFinish(String beanName) throws IllegalAccessException {
        Object o = beanMap.get(beanName);
        Field[] declaredFields = o.getClass().getDeclaredFields();
        for (Field declaredField : declaredFields) {
            AutoSet autoSet = declaredField.getAnnotation(AutoSet.class);
            if (autoSet != null) {
                declaredField.setAccessible(true);
                if (Objects.isNull(declaredField.get(o))) {
                    return false;
                }
            }
        }
        finishBeanName.add(beanName);
        return true;
    }

    private boolean canRegister(Class<?>[] parameterTypes) {
        if (parameterTypes.length != 0) {
            for (Class<?> parameterType : parameterTypes) {
                if (!clazzSet.contains(parameterType)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Object createBean(Constructor<?> autoSetConstructor, Class<?>[] objs) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Object[] objects = new Object[objs.length];
        for (int i = 0; i < objs.length; i++) {
            objects[i] = beanClassMap.get(objs[i]).get(0);
        }
        return autoSetConstructor.newInstance(objects);
    }

    private String getFirstLowClassName(Class<?> clazz) {
        return clazz.getSimpleName().replaceFirst(".", clazz.getSimpleName().substring(0, 1).toLowerCase(Locale.ROOT));
    }

    public Object getBean(String name) {
        return this.beanMap.get(name);
    }

}

