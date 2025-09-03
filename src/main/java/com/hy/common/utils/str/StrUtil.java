package com.hy.common.utils.str;

import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StrUtil {
    public static final Pattern NUM_PATTERN = Pattern.compile("[0-9]*");

    public static String getRandom(int num) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= num; i++) {
            builder.append((int) (Math.random() * 10));
        }
        return builder.toString();
    }

    public static String getUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 可以通过修改正则表达式实现校验负数，将正则表达式修改为“^-?[0-9]+”即可，修改为“-?[0-9]+.?[0-9]+”即可匹配所有数字。
     *
     * @param str
     * @return
     */
    public static boolean isNumeric(String str) {
        Matcher isNum = NUM_PATTERN.matcher(str.replaceAll("\\s*", ""));
        if (!isNum.matches()) {
            return false;
        }
        return true;
    }

    public static Integer StrToInt(String str) {
        return Integer.valueOf(str.replaceAll("\\s*", ""));
    }

    /**
     * 判断是否为空 字符串
     *
     * @param str
     * @return
     */
    public static boolean isNull(String str) {
        return (str == null || str.trim().isEmpty());
    }

    public static String getCode() {
        Integer num = (int) (Math.random() * (9999 - 1000 + 1)) + 1000;
        return num.toString();
    }

    public static Long generateRandomNumber(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("随机数位数必须大于0");
        }
        return (long) (Math.random() * 9 * Math.pow(10, n - 1)) + (long) Math.pow(10, n - 1);
    }


    public static boolean isPhone(String phone) {
        String regex = "^((13[0-9])|(14[5,7,9])|(15([0-3]|[5-9]))|(166)|(17[0,1,3,5,6,7,8])|(18[0-9])|(19[8|9]))\\d{8}$";
        if (phone.length() != 11) {
            return false;
        } else {
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(phone);
            boolean isMatch = m.matches();
            return isMatch;
        }
    }

    /**
     * 正则替换
     *
     * @param url
     * @param key
     * @param value
     * @return
     */
    public static String replaceGetUrlParamsReg(String url, String key, String value) {
        if (!isNull(key) && !isNull(value)) {
            url = url.replaceAll("(" + key + "=[^&]*)", key + "=" + value);
        }
        return url;
    }


    //生成随机用户名，数字和字母组成,
    public static String getStringRandom(int length) {
        String val = "";
        Random random = new Random();
        //参数length，表示生成几位随机数
        for (int i = 0; i < length; i++) {
            String charOrNum = random.nextInt(2) % 2 == 0 ? "char" : "num";
            //输出字母还是数字
            if ("char".equalsIgnoreCase(charOrNum)) {
                //输出是大写字母还是小写字母
                int temp = random.nextInt(2) % 2 == 0 ? 65 : 97;
                val += (char) (random.nextInt(26) + temp);
            } else if ("num".equalsIgnoreCase(charOrNum)) {
                val += String.valueOf(random.nextInt(10));
            }
        }
        return val;
    }

    public static boolean isHttpUrl(String input) {
        if (input == null) return false;
        return input.startsWith("http://") || input.startsWith("https://");
    }

}
