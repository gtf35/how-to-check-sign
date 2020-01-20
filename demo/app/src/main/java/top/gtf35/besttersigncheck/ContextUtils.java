package top.gtf35.besttersigncheck;

import android.annotation.SuppressLint;
import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ContextUtils {

    /*
    * 手动构建 Context
    * */
    @SuppressLint({"DiscouragedPrivateApi","PrivateApi"})
    public static Context getContext() throws ClassNotFoundException,
            NoSuchMethodException,
            InvocationTargetException,
            IllegalAccessException,
            NoSuchFieldException,
            NullPointerException{

        // 反射获取 ActivityThread 的 currentActivityThread 获取 mainThread
        Class activityThreadClass = Class.forName("android.app.ActivityThread");
        Method currentActivityThreadMethod =
                activityThreadClass.getDeclaredMethod("currentActivityThread");
        currentActivityThreadMethod.setAccessible(true);
        Object mainThreadObj = currentActivityThreadMethod.invoke(null);

        // 反射获取 mainThread 实例中的 mBoundApplication 字段
        Field mBoundApplicationField = activityThreadClass.getDeclaredField("mBoundApplication");
        mBoundApplicationField.setAccessible(true);
        Object mBoundApplicationObj = mBoundApplicationField.get(mainThreadObj);

        // 获取 mBoundApplication 的 packageInfo 变量
        if (mBoundApplicationObj == null) throw new NullPointerException("mBoundApplicationObj 反射值空");
        Class mBoundApplicationClass = mBoundApplicationObj.getClass();
        Field infoField = mBoundApplicationClass.getDeclaredField("info");
        infoField.setAccessible(true);
        Object packageInfoObj = infoField.get(mBoundApplicationObj);

        // 反射调用 ContextImpl.createAppContext(ActivityThread mainThread, LoadedApk packageInfo)
        if (mainThreadObj == null) throw new NullPointerException("mainThreadObj 反射值空");
        if (packageInfoObj == null) throw new NullPointerException("packageInfoObj 反射值空");
        Method createAppContextMethod = Class.forName("android.app.ContextImpl").getDeclaredMethod(
                "createAppContext",
                mainThreadObj.getClass(),
                packageInfoObj.getClass());
        createAppContextMethod.setAccessible(true);
        return (Context) createAppContextMethod.invoke(null, mainThreadObj, packageInfoObj);

    }
}
