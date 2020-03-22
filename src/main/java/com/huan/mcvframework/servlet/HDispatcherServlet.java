package com.huan.mcvframework.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.huan.mcvframework.HAutowired;
import com.huan.mcvframework.HController;
import com.huan.mcvframework.HRequestMapping;
import com.huan.mcvframework.HService;

public class HDispatcherServlet extends HttpServlet {
    private static final long serialVersionUID = -4311757187376881941L;
    private static final String LOCATION = "contextConfigLocation";
    private static final String SCANPACKAGE = "scan.package";
    private Properties p = new Properties();
    private List<String> className = new ArrayList<String>();
    private Map<String, Object> ioc = new HashMap<String, Object>();
    private Map<String, Method> handerMapping = new HashMap<String, Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispather(req, resp);
    }

    private void doDispather(HttpServletRequest req, HttpServletResponse resp) {
        if (this.handerMapping.isEmpty()) {
            return;
        }
        try {
            String url = req.getRequestURI();
            String contentPath = req.getContextPath();
            url = url.replace(contentPath, "").replaceAll("/+", "/");

            if (!this.handerMapping.containsKey(url)) {
                resp.getWriter().println(" 404 not found ");
                return;
            }
            Method method = this.handerMapping.get(url);
            // 获取方法的所有的参数类型
            Class<?>[] parameterTypes = method.getParameterTypes();
            // 获取请求所有参数
            Map<String, String[]> parameterMap = req.getParameterMap();
            // new 执行方法的参数数组ֵ
            Object[] params = new Object[parameterTypes.length];
            // 根据方法的参数类型给参数赋值
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                if (parameterType == HttpServletRequest.class) {
                    params[i] = req;
                    continue;
                } else if (parameterType == HttpServletResponse.class) {
                    params[i] = resp;
                    continue;
                } else if (parameterType == String.class) {
                    Set<Entry<String, String[]>> entrySet = parameterMap.entrySet();
                    for (Entry<String, String[]> entry : entrySet) {
                        String value = Arrays.toString(entry.getValue()).replaceAll("\\[\\]", "").replaceAll("\\s", "");
                        params[i] = value;
                    }

                }
            }
            String beanName = toLowerCase(method.getDeclaringClass().getSimpleName());
            // 通过反射执行方法
            method.invoke(ioc.get(beanName), params);
        } catch (Exception e) {
        }

    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));

        // 扫描指定的包
        doScanner(p.getProperty(SCANPACKAGE));

        // 实例化指定包下面的类
        doInstance();

        // 实现依赖注入
        doAutowired();

        // 实现handerMapping
        initHanderMapping();

        // disPatherServlet初始化完毕;
        System.out.println("custom mvcFrameWork is inited");

        // 执行请求 doget dopost

    }

    private void initHanderMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        Set<Entry<String, Object>> set = ioc.entrySet();
        for (Entry<String, Object> entry : set) {
            Class<? extends Object> cls = entry.getValue().getClass();
            if (!cls.isAnnotationPresent(HController.class)) {
                continue;
            }
            String mappingPath = "";
            if (cls.isAnnotationPresent(HRequestMapping.class)) {
                HRequestMapping requestMapping = cls.getAnnotation(HRequestMapping.class);
                mappingPath = requestMapping.value().trim();
            }
            Method[] methods = cls.getDeclaredMethods();
            for (Method method : methods) {
                HRequestMapping requestMapping = method.getAnnotation(HRequestMapping.class);
                String url = ("/" + mappingPath + "/" + requestMapping.value().trim()).replaceAll("/+", "/");
                handerMapping.put(url, method);
                System.out.println("mpped : " + url + "->" + method);
            }
        }
    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        Set<Entry<String, Object>> set = ioc.entrySet();
        for (Entry<String, Object> entry : set) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(HAutowired.class)) {
                    continue;
                }
                field.setAccessible(true);
                HAutowired hAutowired = field.getAnnotation(HAutowired.class);
                String beanName = hAutowired.value().trim();
                if ("".equals(beanName)) {
                    // 根据类型注入
                    beanName = toLowerCase(field.getType().getSimpleName());
                }
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    continue;
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }

    }

    private void doInstance() {
        if (className.isEmpty())
            return;
        for (String classname : className) {
            try {
                Class<?> cls = Class.forName(classname);
                String clsName = toLowerCase(cls.getSimpleName());

                if (cls.isAnnotationPresent(HController.class)) {
                    ioc.put(clsName, cls.newInstance());
                }

                if (cls.isAnnotationPresent(HService.class)) {
                    HService hService = cls.getAnnotation(HService.class);
                    String hValue = hService.value();
                    if ("".equals(hValue)) {
                        ioc.put(clsName, cls.newInstance());
                    } else {
                        ioc.put(hValue, cls.newInstance());
                    }
                    if (cls.isInterface()) {
                        Class<?>[] classes = cls.getInterfaces();
                        for (Class<?> cls1 : classes) {
                            ioc.put(cls1.getName(), cls1.newInstance());
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private String toLowerCase(String simpleName) {
        char[] charArray = simpleName.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource(scanPackage.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                className.add(scanPackage + "." + file.getName().replace(".class", "").trim());
            }
        }
    }

    private void doLoadConfig(String path) {
        InputStream in = null;
        try {
            in = this.getClass().getClassLoader().getResourceAsStream(path);
            p.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
