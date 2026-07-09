package test.hook.debug.xp;

import android.view.View;

import com.github.kyuubiran.ezxhelper.ClassUtils;
import com.github.kyuubiran.ezxhelper.HookFactory;
import com.github.kyuubiran.ezxhelper.Log;

import java.lang.reflect.Method;
import java.util.Collections;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

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
            Log.ex("Failed to disable ad", e);
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
            Log.ex("Failed to disable report", e);
        }
    }

    /**
     * 隐藏蚂蚁阿福和健康问诊横幅
     *
     * @param classLoader
     */
    public static void hideAqView(ClassLoader classLoader) {
        Class<?> AqViewClass = null;
        Class<?> HealthBannerCardSetViewClass = null;
        try {
            AqViewClass = classLoader.loadClass("com.xiaomi.fitness.view.AqView");
        } catch (ClassNotFoundException e) {
        }
        try {
            HealthBannerCardSetViewClass = classLoader.loadClass("com.xiaomi.fitness.view.HealthBannerCardSetView");
        } catch (ClassNotFoundException e) {
        }
        Class<?> finalAqViewClass = AqViewClass;
        Class<?> finalHealthBannerCardSetViewClass = HealthBannerCardSetViewClass;
        XposedHelpers.findAndHookMethod("com.xiaomi.fitness.util.ExtUtilKt", classLoader, "visible", android.view.View.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                View view = (View) param.args[0];
                Class<?> clazz = view.getClass();
                if (clazz.equals(finalAqViewClass) || clazz.equals(finalHealthBannerCardSetViewClass)) {
                    param.setResult(null);
                    view.setVisibility(View.GONE);
                }
            }
        });
    }

    public static void disableFaceEntranceConfig(ClassLoader classLoader) {
        // 红点
        try {
            XposedHelpers.findAndHookMethod("com.xiaomi.fitness.device.manager.ui.tab.DeviceFaceEntranceViewV4", classLoader, "showRedPoint", boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    param.args[0] = false;
                }
            });
        } catch (NoSuchMethodError | Exception e) {
            Log.ex("Failed to disable face entrance red point", e);
        }
        // 标题、背景等云控
        try {
            XposedHelpers.findAndHookMethod("com.xiaomi.fitness.device.manager.ui.tab.DeviceFaceEntranceViewV4", classLoader, "setOperationConfig", classLoader.loadClass("com.xiaomi.fitness.watch.face.data.OperationConfig"), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    param.args[0] = null;
                }
            });
        } catch (NoSuchMethodError | Exception e) {
            Log.ex("Failed to clear face entrance operation config", e);
        }
        // 最近上新 X 款
        try {
            XposedHelpers.findAndHookMethod("com.xiaomi.fitness.device.manager.ui.tab.DeviceFaceEntranceViewV4", classLoader, "setRecentOnlineCount", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    param.args[0] = 0;
                }
            });
        } catch (NoSuchMethodError | Exception e) {
            Log.ex("Failed to clear face entrance recent online count", e);
        }
    }
}
