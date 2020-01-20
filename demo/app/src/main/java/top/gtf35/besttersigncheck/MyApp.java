package top.gtf35.besttersigncheck;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

public class MyApp extends Application {

    private static boolean sEarlyCheckSignResult = false;
    public static boolean getEarlyCheckSignResult(){ return sEarlyCheckSignResult; }

    public MyApp() {
        sEarlyCheckSignResult = earlyCheckSign();
    }

    boolean earlyCheckSign(){
        String trueSignMD5 = "d0add9987c7c84aeb7198c3ff26ca152";
        String nowSignMD5 = "";
        try {
            // 获取新的 Context
            Context context = ContextUtils.getContext();
            //得到签名hashcode
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(),
                    PackageManager.GET_SIGNATURES);
            Signature[] signs = packageInfo.signatures;
            String signBase64 = Base64Util.encodeToString(signs[0].toByteArray());
            nowSignMD5 = MD5Utils.MD5(signBase64);

        } catch (Exception e){
            e.printStackTrace();
        }
        return trueSignMD5.equals(nowSignMD5);
    }
}
