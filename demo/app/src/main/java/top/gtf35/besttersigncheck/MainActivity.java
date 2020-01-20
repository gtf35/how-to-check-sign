package top.gtf35.besttersigncheck;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.lang.reflect.Field;

import static android.os.Build.*;

public class MainActivity extends AppCompatActivity {

    private TextView mNormalSignCheckResultTV;
    private TextView mCheckApplicationResultTV;
    private TextView mEarlyCheckResultTV;
    private TextView mCheckPMProxyTV;
    private TextView mUseNewAPICheckTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mNormalSignCheckResultTV = findViewById(R.id.normal_result_check_result);
        mCheckApplicationResultTV = findViewById(R.id.check_application_result);
        mEarlyCheckResultTV = findViewById(R.id.early_checkc_result);
        mCheckPMProxyTV = findViewById(R.id.check_proxy);
        mUseNewAPICheckTV = findViewById(R.id.use_new_api_check);
        mNormalSignCheckResultTV.setText(doNormalSignCheck()? "通过": "未通过");
        mCheckApplicationResultTV.setText(checkApplication()? "通过": "未通过");
        mEarlyCheckResultTV.setText(MyApp.getEarlyCheckSignResult()? "通过": "未通过");
        mCheckPMProxyTV.setText(checkPMProxy()? "通过": "未通过");
        mUseNewAPICheckTV.setText(useNewAPICheck()? "通过": "未通过");
    }

    /**
     * 做普通的签名校验
     */
    @SuppressLint("PackageManagerGetSignatures")
    private boolean doNormalSignCheck() {
        String trueSignMD5 = "d0add9987c7c84aeb7198c3ff26ca152";
        String nowSignMD5 = "";
        try {
            // 得到签名MD5
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

    /**
     * 校验 application
     */
    private boolean checkApplication(){
        Application nowApplication = getApplication();
        String trueApplicationName = "MyApp";
        String nowApplicationName = nowApplication.getClass().getSimpleName();
        return trueApplicationName.equals(nowApplicationName);
    }

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
            assert mPM != null;
            nowPMName = mPM.getClass().getName();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 类名改变说明被代理了
        return truePMName.equals(nowPMName);
    }

    /**
     * 使用较新的 API 检测
     */
    @SuppressLint("PackageManagerGetSignatures")
    private boolean useNewAPICheck(){
        String trueSignMD5 = "d0add9987c7c84aeb7198c3ff26ca152";
        String nowSignMD5 = "";
        Signature[] signs = null;
        try {
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
            // 得到签名MD5
            String signBase64 = Base64Util.encodeToString(signs[0].toByteArray());
            nowSignMD5 = MD5Utils.MD5(signBase64);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return trueSignMD5.equals(nowSignMD5);
    }
}
