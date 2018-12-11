package com.spontaneously.servlet;

import com.spontaneously.annotation.*;
import com.spontaneously.controller.IndexController;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class MyDispatcherServlet extends HttpServlet {

    /**
     * 用来模仿Spring IOC容器中存储bean的名字
     */
    private static List<String> classNames = new ArrayList<>();

    /**
     * 这里是真正用来存储根据上面的className上实例化出来的bean
     */
    private static Map<String, Object> beans = new HashMap<>();

    /**
     * 存放URL和方法的映射
     */
    private static Map<String, Object> handlerMap = new HashMap<>();

    public void init(ServletConfig config) {
        //把所有的bean扫描出来，就是扫描所有的class文件，扫描出来之后放入classNames中的都是com.spontaneously.controller.IndexController.class这种字符串
        scanPackage("com.spontaneously");
        //根据上面扫描出来的全类名进行实例化对象
        doNewInstance();
        //处理上面扫描的类中的注入依赖关系
        doIOC();
        //把方法和URL建立起来连接，这样在访问URL的时候就能找到方法
        buildUrlMapping();
    }

    private void buildUrlMapping() {
        if (beans.size() <= 0) {
            System.out.println("没有实例化一个类");
        }
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object instance = entry.getValue();
            Class<?> clazz = instance.getClass();
            if (clazz.isAnnotationPresent(MyController.class)) {
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                String classPath = requestMapping.value();

                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(MyRequestMapping.class)) {
                        MyRequestMapping methodMapping = method.getAnnotation(MyRequestMapping.class);
                        String methodPath = methodMapping.value();

                        handlerMap.put(classPath + methodPath, method);
                    }
                }
            }
        }
    }

    private void doIOC() {
        if (beans.size() <= 0) {
            System.out.println("没有一个实例化的类");
        }
        //遍历beans中所有的类，然后看其中的属性需不需要诸如
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object instance = entry.getValue();
            //获取到Class对象才能对Class中的成员变量或者方法做操作
            Class<?> clazz = instance.getClass();
            //因为在这个案例中我只在controller中注入了service，所有我这里做了一次判断看是不是controller，如果是才处理
            if (clazz.isAnnotationPresent(MyController.class)) {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    if (field.isAnnotationPresent(MyAutowired.class)) {
                        MyAutowired auto = field.getAnnotation(MyAutowired.class);
                        String key = auto.value();
                        //因为controller中的成员变量都是私有的，所以需要设置权限
                        field.setAccessible(true);
                        //给instance设置其中的service值
                        try {
                            field.set(instance, beans.get(key));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private void doNewInstance() {
        if (classNames.size() <= 0) {
            System.out.println("包扫描失败");
        }
        //对classNames中存储的class进行实例化
        for (String className : classNames) {
            //因为从classNames中取出来的都是类似com.spontaneously.controller.IndexController.class的文件名，所以首先要去掉.class后缀
            String clazzName = className.replace(".class", "");
            try {
                Class<?> clazz = Class.forName(clazzName);
                Object instance = clazz.newInstance();
                if (clazz.isAnnotationPresent(MyController.class)) {
                    MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                    String rmValue = requestMapping.value();
                    beans.put(rmValue, instance);
                } else if (clazz.isAnnotationPresent(MyService.class)) {
                    MyService service = clazz.getAnnotation(MyService.class);
                    beans.put(service.value(), instance);
                }
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

    }

    private void scanPackage(String basePackage) {
        //后面有替换的原因是因为我们在扫描的是传入的是包的名字，其中带有点，而在真正的生产环境中是不存在点号的，都是斜杠表示的目录
        URL url = this.getClass().getClassLoader().getResource("/" + basePackage.replaceAll("\\.", "/"));
        //加下来是拿到com/spontaneously目录下的所有文件，可能是文件，也可能是文件夹
        String fileStr = Objects.requireNonNull(url).getFile();
        File file = new File(fileStr);
        String[] filesStr = file.list();
        //拿到扫描的路径下的File，然后判断是文件还是目录，如果是目录就递归调用scanPackage，如果是文件，就加入到一个List中，这个
        //List模仿的就是Spring IOC中的核心组件，用来存储生成的Bean的地方
        assert filesStr != null;
        for (String path : filesStr) {
            File filePath = new File(fileStr + path);
            if (filePath.isDirectory()) {
                scanPackage(basePackage + "." + path);
            } else {
                //加入到List中去，这里加入的其实就是全类名，因为只有全类名才可以通过反射获取实例对象
                classNames.add(basePackage + "." + filePath.getName());
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //此处获得的URI应该是/my-springmvc/index/query，其中包含了web.xml中设置的display-name的值my-springmvc
        String uri = req.getRequestURI();
        //获得在web.xml中设置的contextPath，即display-name值my-springmvc
        String contextPath = req.getContextPath();
        String path = uri.replace(contextPath, "");

        Method method = (Method) handlerMap.get(path);

        //根据key=index去找controller，因为在初始化bean的时候，使用了注解中的value来作为key
        IndexController instance = (IndexController) beans.get("/" + path.split("/")[1]);

        //接下来要拿到参数
        Object[] args = hand(req, resp, method);
        try {
            method.invoke(instance, args);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    /**
     * 这里也可以使用策略模式
     * 因为SpringMVC就是使用策略模式来编写的
     *
     * @param request
     * @param response
     * @return
     */
    private static Object[] hand(HttpServletRequest request, HttpServletResponse response, Method method) {
        //拿到当前待执行的方法有哪些参数
        Class<?>[] paramClazzs = method.getParameterTypes();
        //根据参数的个数，new一个参数的数组，将方法里的所有参数赋值到args里面
        Object[] args = new Object[paramClazzs.length];

        int args_i = 0;
        int index = 0;
        for (Class<?> paramClazz : paramClazzs) {
            if (ServletRequest.class.isAssignableFrom(paramClazz)) {
                args[args_i++] = request;
            }
            if (ServletResponse.class.isAssignableFrom(paramClazz)) {
                args[args_i++] = response;
            }
            //从0-3判断有没有RequestParam注解，很明显paramClazz为0和1时，不是，当为2和3是为RequestParam住家，需要解析
            Annotation[] paramAns = method.getParameterAnnotations()[index];
            if (paramAns.length > 0) {
                for (Annotation paramAn : paramAns) {
                    if (MyRequestParam.class.isAssignableFrom(paramAn.getClass())) {
                        MyRequestParam rp = (MyRequestParam) paramAn;
                        //找到注解中的name和age
                        args[args_i++] = request.getParameter(rp.value());
                    }
                }
            }
            index++;
        }
        return args;
    }
}
