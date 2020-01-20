# 为何你的应用老是被破解，该如何有效的做签名校验？

## 0 前言

众所周知，签名校验是防止二次打包最普遍的方式。下面是常见的签名校验方法：

```java
/**
 * 做普通的签名校验
 */
private boolean doNormalSignCheck() {
    String trueSignMD5 = "d0add9987c7c84aeb7198c3ff26ca152";
    String nowSignMD5 = "";
    try {
        // 得到签名的MD5
        PackageInfo packageInfo = getPackageManager().getPackageInfo(
                getPackageName(),
                PackageManager.GET_SIGNATURES);
        Signature[] signs = packageInfo.signatures;
        String signBase64 = Base64Util.encodeToString(signs[0].toByteArray());
        nowSignMD5 = MD5Utils.MD5(signBase64);
    } catch (PackageManager.NameNotFoundException e) {
        e.printStackTrace();
    }
    return trueSignMD5.equals(nowSignMD5);
}
```

系统将应用的签名信息封装在 PackageInfo 中，调用 PackageManager 的 getPackageInfo(String packageName, int flags) 即可获取指定包名的签名信息。这个并不用我多说了，如果没听过的话，用搜索引擎找一下「Android 签名校验」，花上几分钟就明白了，很容易的。

编译出 release 包并安装，可以看见运行效果很满意。但是事实真的如此么？下面我们让他作为受害者，被一键破解。

![目前看起来很好](https://github.com/gtf35/how-to-check-sign/blob/master/pic/image-20200119192020020.png)

## 1 使用通用工具去除我们先前的校验

很多人可能不知道，去除简单的签名校验连小朋友都能做到！

![太可怕](https://github.com/gtf35/how-to-check-sign/blob/master/pic/image-20200119193534667.png)

请看具有「安全性测试」功能的「M* 管理器」上场，一键去除我们上文准备好的受害者的签名校验：

![一键去除签名校验演示](https://github.com/gtf35/how-to-check-sign/blob/master/pic/killsign.gif)

神奇的一幕发生了，居然还是通过，也就是我们刚才的操作形同虚设，我们把被破解后的安装包传回 PC，准备下一步分析

## 2 JADX 上场

为了知道他做了什么，我们需要逆向出目前受害者的代码。这里我们使用开源项目「[jadx](https://github.com/skylot/jadx)」来完成。

打开 jadx 之后会直接弹出「打开」对话框，选取被破解的 apk 即可：

![JADX 打开文件](https://github.com/gtf35/how-to-check-sign/blob/master/pic/image-20200119200522083.png)

简单对比下可以发现，多了一个「HookApplication」类

![对比代码](https://github.com/gtf35/how-to-check-sign/blob/master/pic/image-20200119200808336.png)

点击进去即可直接看见源代码：

```java
package bin.mt.apksignaturekillerplus;

public class HookApplication extends Application implements InvocationHandler {
    private static final int GET_SIGNATURES = 64;
    private String appPkgName = BuildConfig.FLAVOR;
    private Object base;
    private byte[][] sign;

    private void hook(Context context) {
        try {
            DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(Base64.decode("省略很长的签名 base64", 0)));
            byte[][] bArr = new byte[(dataInputStream.read() & 255)][];
            for (int i = 0; i < bArr.length; i++) {
                bArr[i] = new byte[dataInputStream.readInt()];
                dataInputStream.readFully(bArr[i]);
            }
            Class cls = Class.forName("android.app.ActivityThread");
            Object invoke = cls.getDeclaredMethod("currentActivityThread", new Class[0]).invoke(null, new Object[0]);
            Field declaredField = cls.getDeclaredField("sPackageManager");
            declaredField.setAccessible(true);
            Object obj = declaredField.get(invoke);
            Class cls2 = Class.forName("android.content.pm.IPackageManager");
            this.base = obj;
            this.sign = bArr;
            this.appPkgName = context.getPackageName();
            Object newProxyInstance = Proxy.newProxyInstance(cls2.getClassLoader(), new Class[]{cls2}, this);
            declaredField.set(invoke, newProxyInstance);
            PackageManager packageManager = context.getPackageManager();
            Field declaredField2 = packageManager.getClass().getDeclaredField("mPM");
            declaredField2.setAccessible(true);
            declaredField2.set(packageManager, newProxyInstance);
            System.out.println("PmsHook success.");
        } catch (Exception e) {
            System.err.println("PmsHook failed.");
            e.printStackTrace();
        }
    }

    /* access modifiers changed from: protected */
    public void attachBaseContext(Context context) {
        hook(context);
        super.attachBaseContext(context);
    }

    public Object invoke(Object obj, Method method, Object[] objArr) throws Throwable {
        if ("getPackageInfo".equals(method.getName())) {
            String str = objArr[0];
            if ((objArr[1].intValue() & 64) != 0 && this.appPkgName.equals(str)) {
                PackageInfo packageInfo = (PackageInfo) method.invoke(this.base, objArr);
                packageInfo.signatures = new Signature[this.sign.length];
                for (int i = 0; i < packageInfo.signatures.length; i++) {
                    packageInfo.signatures[i] = new Signature(this.sign[i]);
                }
                return packageInfo;
            }
        }
        return method.invoke(this.base, objArr);
    }
}
```

有点长，但是也不是很费解。

他继承自 Application，重写了 attachBaseContext 来调用 hook(context) ，在里面做了 IPackageManager 的动态代理，实现在调用 getPackageInfo 方法的时候，修改 signatures[] 为在破解之前计算好的数值。这就是为什么我们的检测手段无效了。

所谓的知己知彼，百战不殆，我们先来分析下他做了什么：

1.  替换掉原来的 Application
2.  在 attachBaseContext 里初始化 hook
3.  动态代理 IPackageManager
4.  hook 替换掉 signatures 的值

所以应对方案也就水到渠成：

1.  检查 Application
2.  在调用 attachBaseContext  之前检测签名
3.  检查 IPackageManager  有没有被动态代理
4.  使用别的 API 去获取



## 3 反抗-检查 Application

他替换掉了 Application 为他自己的，那么变化的太多了，Application 的类名 / 方法数 / 字段数 / AndroidManifast 中 Application 节点的 name，都会变。我们这里以检查 Application 的类名为例：

```java
/**
 * 校验 application
 */
private boolean checkApplication(){
    Application nowApplication = getApplication();
    String trueApplicationName = "MyApp";
    String nowApplicationName = nowApplication.getClass().getSimpleName();
    return trueApplicationName.equals(nowApplicationName);
}
```

-   先定义我们自己的 Application ——「MyApp」
-   然后通过 getApplication() 获取到 Application 实例
-   然后通过 getClass() 获取到类信息
-   然后通过 getSimpleName() 获取到类名
-   与正确的值比对然后返回

![可以检测出被二改](https://github.com/gtf35/how-to-check-sign/blob/master/pic/checkApp.gif)

可以看到可以检测出被二次打包

## 4 反抗-在调用 attachBaseContext  之前检测签名

只要我们检测的够早，他就追不上我们。不，他会 hook 到我们的几率就越小

A: 要有多早？

B: emm，就在 Application 的构造方法里检测吧

A: 那，，，没 context 呀

B: 那就自己造一个 context！

A: 你放屁！

B: 走你

通过学习 Application 的创建流程可知，Context  是通过 LoadedApk 调用 createAppContext 方法实现的

```java
// LoadedApk.java
package android.app;
ContextImpl appContext = ContextImpl.createAppContext(mActivityThread, this);
```

函数原型为

```java
// ContextImpl.java
package android.app;

@UnsupportedAppUsage
static ContextImpl createAppContext(ActivityThread mainThread, LoadedApk packageInfo) {
    return createAppContext(mainThread, packageInfo, null);
}
```

第一个参数好说，因为这是个单例类，调用 currentActivityThread 即可获取 ActivityThread 对象

```java
// ActivityThread.java
package android.app;

@UnsupportedAppUsage
private static volatile ActivityThread sCurrentActivityThread;

@UnsupportedAppUsage
public static ActivityThread currentActivityThread() {
    return sCurrentActivityThread;
}
```

但是需要注意的是有 「@UnsupportedAppUsage」修饰，需要反射调用。在学习 Application 的创建流程的时候可知(其实是我不会上网找的流程)，另一个 LoadedApk  对象是通过 getPackageInfoNoCheck 方法创建的。

```java
// ActivityThread.java
package android.app;

@Override
@UnsupportedAppUsage
public final LoadedApk getPackageInfoNoCheck(ApplicationInfo ai,
            CompatibilityInfo compatInfo) {
    return getPackageInfo(ai, compatInfo, null, false, true, false);
}
```

这个值保存在 ActivityThread 实例的 mBoundApplication.info 变量里。

```java
// ActivityThread.java
package android.app;

@UnsupportedAppUsage
AppBindData mBoundApplication;

@UnsupportedAppUsage
private void handleBindApplication(AppBindData data) {
    // 省略无关代码
    mBoundApplication = data;
    // 省略无关代码
    data.info = getPackageInfoNoCheck(data.appInfo, data.compatInfo);
    // 省略无关代码
}
```

mBoundApplication 虽然不是静态变量，但是因为我们之前已经获取到了 ActivityThread  实例，所以不耽误我们反射获取。现在我们调用 ContextImpl.createAppContext 的条件已经满足了，反射调用即可。

ContextUtils 最终实现代码如下：

```java
public class ContextUtils {

    /**
     * 手动构建 Context
     */
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
```

后面的事就好办多了，就是在 Application 的构造函数里用我们手动构造的 context 去获取签名(这个时候还没有 context)

```java
public class MyApp extends Application {

    private static boolean sEarlyCheckSignResult = false;
    public static boolean getEarlyCheckSignResult(){ return sEarlyCheckSignResult;}

    public MyApp() {
    	// 在构造函数里提早检测
        sEarlyCheckSignResult = earlyCheckSign();
    }

    boolean earlyCheckSign(){
        // 手动构造 context
		Context context = ContextUtils.getContext();
		// 省略用新 context 校验签名的过程(正常的检测一样)
        return 检测结果;
    }
}
```

效果也很棒：

![可以检测出被二改](https://github.com/gtf35/how-to-check-sign/blob/master/pic/earlyCheck.gif)

## 5 反抗-检查 IPackageManager  有没有被动态代理

动态代理的原理是系统动态的为我们创建了一个代理类，所以检测 IPackageManager 的类名即可发现端倪：

```java
    /**
     * 检测 PM 代理
     */
    @SuppressLint("PrivateApi")
    private boolean checkPMProxy(){
        String truePMName = "android.content.pm.IPackageManager$Stub$Proxy";
        String nowPMName = "";
        try {
            // 被代理的对象是 PackageManager.mPM
            PackageManager packageManager = getPackageManager();
            Field mPMField = packageManager.getClass().getDeclaredField("mPM");
            mPMField.setAccessible(true);
            Object mPM = mPMField.get(packageManager);
            // 取得类名
            nowPMName = mPM.getClass().getName();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 类名改变说明被代理了
        return truePMName.equals(nowPMName);
    }
```

相当简单，因为 IPackageManager 的实例存在于 PackageManager 实例的 mPM 字段里，所以我们反射他获取即可拿到。拿到后可以判断类名，正常的类名是 「android.content.pm.IPackageManager$Stub$Proxy」。因为是远端对象的缘故，会有 $Stub$Proxy 后缀。如果他被动态被代理了，应该是类似「$Proxy0」这种类名，效果图如下：

![可以检测出被二改](https://github.com/gtf35/how-to-check-sign/blob/master/pic/checkProxy.gif)

## 6 反抗-使用别的 API 去获取

不是到你有没有发现，他 hook 的 API 其实是过时的，也就是我们用新的 API 的话，有可能一些老牌的自动化工具无法处理到，我们试一试：

```java
/**
 * 使用较新的 API 检测
 */
@SuppressLint("PackageManagerGetSignatures")
private boolean useNewAPICheck(){
    String trueSignMD5 = "d0add9987c7c84aeb7198c3ff26ca152";
    String nowSignMD5 = "";
    Signature[] signs = null;
    try {
        // 得到签名 MD5
        if (VERSION.SDK_INT >= 28) {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(
                    getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            signs = packageInfo.signingInfo.getApkContentsSigners();
        } else {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(
                    getPackageName(),
                    PackageManager.GET_SIGNATURES);
            signs = packageInfo.signatures;
        }
        String signBase64 = Base64Util.encodeToString(signs[0].toByteArray());
        nowSignMD5 = MD5Utils.MD5(signBase64);
    } catch (PackageManager.NameNotFoundException e) {
        e.printStackTrace();
    }
    return trueSignMD5.equals(nowSignMD5);
}
```

用 API28 以上的设备运行，事实证明是可以的：

![可以检测出被二改](https://github.com/gtf35/how-to-check-sign/blob/master/pic/useNewAPICheck.gif)

使用过时 API 应该是每个开发者都应该尽力避免的，但是关键性的代码确实是可以通过不走寻常路来调用，只要破解者想不到，我们就成功了。比如....校验类的签名，方法数，等等，这里我不操作了，留给读者自己思考，反破解方法往往越没人知道就越有效，我在此公开更多特殊方法校验签名的细节其实并不是一件好事，抛砖引玉即可

## 思考

我今天从现实中的场景 - “拿到了破解者的 apk ”， 去分析他做了什么破解，然后通过对破解者的行为特征进行分析，来进一步给予反击。

demo 中因为硬编码了一些签名，所以也附上我文中使用的签名，所有信息都是 「demodemo」，用我的签名编译安装即可正常运行。

本文中提到的自动化工具，作者已经将这一部分[开源](https://github.com/L-JINBIN/ApkSignatureKiller)，而且也很久没更新了，这里仅仅只是用它当例子。目前高端的破解往往采取了 hook 的方式。很多人不关心这种方式，我今天就写了一篇文章来讲解。因为他 IPackageManager 全局只有一个，他 hook 掉之后，就算是在 native 去校验，那他也是错的。

要做到坚不可摧，用一种保护手段往往是不够的，混淆和花代码也是必不可少的，毕竟保护和隐藏好自己的反破解代码更是一门学问，但是那就是另外一个故事了...

## For me

gtf35

Email: gtfdeyouxiang@gmail.com

[Blog](https://blog.gtf35.top/): https://blog.gtf35.top/how_to_check_sign/