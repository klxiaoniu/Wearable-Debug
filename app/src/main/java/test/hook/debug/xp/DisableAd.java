package test.hook.debug.xp;

import com.github.kyuubiran.ezxhelper.ClassUtils;
import com.github.kyuubiran.ezxhelper.HookFactory;
import com.github.kyuubiran.ezxhelper.Log;

import java.lang.reflect.Method;
import java.util.Collections;

public class DisableAd {
    /**
     * 国际版3.33.6i出现Banner广告，拦截广告加载
     */
    public static void interceptAd(ClassLoader classLoader) {
        try {
            Class<?> impl = ClassUtils.loadClass("com.fitness.banner.export.BannerImpl", classLoader);
            for (Method method : impl.getDeclaredMethods()) {
                if (method.getName().startsWith("getBannerListAsync")) {
                    HookFactory.createMethodHook(method, hookFactory -> hookFactory.replace(methodHookParam -> null));
                } else if (method.getName().startsWith("getBannerList")) {
                    HookFactory.createMethodHook(method, hookFactory -> hookFactory.replace(methodHookParam -> Collections.emptyList()));
                }
            }
        } catch (ClassNotFoundException e) {
            Log.e("Failed to disable ad", e);
        }
    }

    public static void disableReport(ClassLoader classLoader) {
        try {
            Class<?> reportImpl = ClassUtils.loadClass("com.xiaomi.fitness.statistics.OnetrackImpl", classLoader);
            for (Method method : reportImpl.getDeclaredMethods()) {
                if (!"reportData".equals(method.getName())) {
                    continue;
                }
                HookFactory.createMethodHook(method, hookFactory -> hookFactory.replace(methodHookParam -> null));
            }

        } catch (ClassNotFoundException e) {
            Log.e("Failed to disable report", e);
        }
    }
}
