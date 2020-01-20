package top.gtf35.besttersigncheck;

import android.util.Base64;


/*
* gtf352019/05/12备注：
* 我fuck，没这么气人的
* 在使用过程出现了莫名其妙的错误，发现字符串过长（一般超过76）时会自动在中间加一个换行符，
* 要使用NO_WRAP参数
* 我fuck
*
 */
public class Base64Util {
    /**
     * 二进制数组 编码成 二进制数组
     * @param input
     * @return
     */
    public static byte[] encodeToBytes(byte[] input) {
        return Base64.encode(input, Base64.NO_WRAP);
    }

    /**
     * 二进制数组 编码成 字符串
     * @param input
     * @return
     */
    public static String encodeToString(byte[] input) {
        return Base64.encodeToString(input, Base64.NO_WRAP);
    }

    /**
     * 二进制数组 解码成 二进制数组
     * @param input
     * @return
     */
    public static byte[] decode(byte[] input) {
        return Base64.decode(input, Base64.NO_WRAP);
    }

    /**
     * 字符串 解码成 二进制数组
     * @return
     */
    public static byte[] decode(String str) {
        return Base64.decode(str, Base64.NO_WRAP);
    }
}