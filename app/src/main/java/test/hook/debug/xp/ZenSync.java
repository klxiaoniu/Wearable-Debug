package test.hook.debug.xp;

import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.github.kyuubiran.ezxhelper.EzXHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import test.hook.debug.xp.utils.Log;

/**
 * 让新版勿扰同步支持非小米手机，且只看全局开关状态
 * hook后就不会再走旧版同步的逻辑了，所以要使用支持新版同步的手环ROM
 */
public class ZenSync {
    private static final String CLS_ZEN_SYNC_HELPER =
            "com.xiaomi.fitness.devicesettings.common.zenmode.ZenModeSyncHelper";
    private static final String CLS_ZEN_RULE = "mnq";
    private ContentObserver mZenObserver;

    public static ZenSync INSTANCE = new ZenSync();

    public void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.processName.endsWith(":device")) return;

        Log.ix("=== ZenMode Hook Start ===");

        // 调用isSupportZenSyncRom（里面会调用RomUtils.isXiaomi），如果是true，说明是支持小米勿扰的系统，不再继续hook
//        Object instance = getStaticField(
//                lpparam.classLoader, CLS_ZEN_SYNC_HELPER, "INSTANCE");
//        boolean isSupportZenSyncRom = (boolean) XposedHelpers.callMethod(instance,
//                "isSupportZenSyncRom", EzXHelper.getAppContext());
//        if (isSupportZenSyncRom) {
//            Log.ix("isSupportZenSyncRom = true, skip ZenSync hooks");
//            return;
//        }
        // ================================================================
        // Hook 1: isSupportZenSyncRom() → true
        // ================================================================
        try {
            XposedHelpers.findAndHookMethod(CLS_ZEN_SYNC_HELPER, lpparam.classLoader,
                    "isSupportZenSyncRom", Context.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return true;
                        }
                    });
            Log.ix("✓ Hook 1: isSupportZenSyncRom → true");
        } catch (Throwable t) {
            Log.ex("✗ Hook 1 failed", t);
        }
        // ================================================================
        // Hook 2: isManualRuleActiveWithReflection() → Boolean
        // 获取全局勿扰开关状态
        // ================================================================
        try {
            XposedHelpers.findAndHookMethod(CLS_ZEN_SYNC_HELPER, lpparam.classLoader,
                    "isManualRuleActiveWithReflection",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                NotificationManager nm = (NotificationManager)
                                        EzXHelper.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
                                int filter = nm.getCurrentInterruptionFilter();
                                // INTERRUPTION_FILTER_ALL = 1 表示勿扰关闭
                                boolean active = (filter != NotificationManager.INTERRUPTION_FILTER_ALL);
                                param.setResult(active);
                            } catch (Throwable t) {
                                param.setResult(false);
                            }
                        }
                    });
            Log.ix("✓ Hook 2: isManualRuleActiveWithReflection → NM API");
        } catch (Throwable t) {
            Log.ex("✗ Hook 2 failed", t);
        }
        // ================================================================
        // Hook 3: getMiuiZenRules(Context) → 返回空列表
        // 忽略规则相关逻辑
        // ================================================================
        try {
            XposedHelpers.findAndHookMethod(CLS_ZEN_SYNC_HELPER, lpparam.classLoader,
                    "getMiuiZenRules", Context.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return new ArrayList<>();
                        }
                    });
            Log.ix("✓ Hook 3: getMiuiZenRules → empty (skip auto rules)");
        } catch (Throwable t) {
            Log.ex("✗ Hook 3 failed", t);
        }
        // ================================================================
        // Hook 4: updatePhoneZenRules(Context, boolean, long, List)
        // 用 setInterruptionFilter 替代 HyperOS 隐藏 API
        // ================================================================
        try {
            XposedHelpers.findAndHookMethod(CLS_ZEN_SYNC_HELPER, lpparam.classLoader,
                    "updatePhoneZenRules",
                    Context.class, boolean.class, long.class, List.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            boolean manualState = (boolean) param.args[1];
                            try {
                                NotificationManager nm = (NotificationManager)
                                        ((Context) param.args[0]).getSystemService(
                                                Context.NOTIFICATION_SERVICE);
                                nm.setInterruptionFilter(manualState
                                        ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                                        : NotificationManager.INTERRUPTION_FILTER_ALL);
                                Log.dx("setInterruptionFilter: "
                                        + (manualState ? "PRIORITY" : "ALL"));
                            } catch (Throwable t) {
                                Log.ex("updatePhoneZenRules error", t);
                            }
                            param.setResult(null);
                        }
                    });
            Log.ix("✓ Hook 4: updatePhoneZenRules → setInterruptionFilter");
        } catch (Throwable t) {
            Log.ex("✗ Hook 4 failed", t);
        }
        // ================================================================
        // Hook 5: handleDeviceZenRule(Context, mnq$a)
        // 只处理手动规则（isManual=true），忽略自动规则
        // ================================================================
        try {
            Class<?> mnqClass = lpparam.classLoader.loadClass(CLS_ZEN_RULE);
            Class<?> mnqAClass = null;
            // 找到 mnq$a：包含 mnq[] 数组字段的内部类
            for (Class<?> inner : mnqClass.getDeclaredClasses()) {
                for (Field f : inner.getDeclaredFields()) {
                    if (f.getType().isArray()
                            && f.getType().getComponentType() == mnqClass) {
                        mnqAClass = inner;
                        break;
                    }
                }
                if (mnqAClass != null) break;
            }
            if (mnqAClass == null) {
                Log.wx("⚠ mnq$a not found, skip hook 5");
            } else {
                Class<?> finalMnqAClass = mnqAClass;
                XposedHelpers.findAndHookMethod(CLS_ZEN_SYNC_HELPER, lpparam.classLoader,
                        "handleDeviceZenRule", Context.class, finalMnqAClass,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                Context ctx = (Context) param.args[0];
                                Object ruleList = param.args[1];
                                if (ctx == null || ruleList == null) {
                                    param.setResult(null);
                                    return;
                                }
                                try {
                                    // 获取 f92169c → mnq[] rules
                                    Field rulesField = finalMnqAClass
                                            .getDeclaredField("c");
                                    rulesField.setAccessible(true);
                                    Object[] rules = (Object[]) rulesField.get(ruleList);
                                    if (rules == null) {
                                        param.setResult(null);
                                        return;
                                    }
                                    for (Object rule : rules) {
                                        if (rule == null) continue;
                                        // 只关心手动规则
                                        Field isManualField = rule.getClass()
                                                .getDeclaredField("c");
                                        isManualField.setAccessible(true);
                                        if (!isManualField.getBoolean(rule)) continue;
                                        // 读取状态: f92165e (int)
                                        Field stateField = rule.getClass()
                                                .getDeclaredField("e");
                                        stateField.setAccessible(true);
                                        int state = stateField.getInt(rule);
                                        // 设置系统勿扰
                                        NotificationManager nm = (NotificationManager)
                                                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                                        nm.setInterruptionFilter(state == 1
                                                ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                                                : NotificationManager.INTERRUPTION_FILTER_ALL);
                                        Log.dx("Device→Phone: DND "
                                                + (state == 1 ? "ON" : "OFF"));
                                        break; // 只处理第一个手动规则
                                    }
                                } catch (Throwable t) {
                                    Log.ex("handleDeviceZenRule error", t);
                                }
                                param.setResult(null);
                            }
                        });
                Log.ix("✓ Hook 5: handleDeviceZenRule → manual rule only");
            }
        } catch (Throwable t) {
            Log.ex("✗ Hook 5 failed", t);
        }

        // ================================================================
        // Hook 6: Settings.Secure.getLong — 时间戳 key，默认为 0 手环端不会生效
        // ================================================================
        try {
            XposedHelpers.findAndHookMethod(
                    Settings.Secure.class,
                    "getLong",
                    ContentResolver.class, String.class, long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if ("manual_zen_mode_last_update_time"
                                    .equals(param.args[1])) {
                                param.setResult(System.currentTimeMillis() / 1000);
                            }
                        }
                    });
            Log.ix("✓ Hook 6: Settings.Secure.getLong → currentTimeMillis");
        } catch (Throwable t) {
            Log.ex("✗ Hook 6 failed", t);
        }
        // ================================================================
        // Hook 7: registerZenModeListener()
        // ContentObserver 监听 Settings.Global.zen_mode，与 ZenUtils 一致
        // ================================================================
        try {
            XposedHelpers.findAndHookMethod(CLS_ZEN_SYNC_HELPER, lpparam.classLoader,
                    "registerZenModeListener",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object instance = getStaticField(
                                        lpparam.classLoader, CLS_ZEN_SYNC_HELPER, "INSTANCE");
                                Context ctx = EzXHelper.getAppContext();
                                mZenObserver = new ContentObserver(
                                        new Handler(Looper.getMainLooper())) {
                                    @Override
                                    public void onChange(boolean selfChange) {
                                        Log.dx("zen_mode changed → sync to device");
                                        try {
                                            Method m = instance.getClass()
                                                    .getDeclaredMethod("setZenRule", Context.class);
                                            m.setAccessible(true);
                                            m.invoke(instance, ctx);
                                        } catch (Throwable t) {
                                            Log.ex("invoke setZenRule failed", t);
                                        }
                                    }
                                };
                                ctx.getContentResolver().registerContentObserver(
                                        Settings.Global.getUriFor("zen_mode"),
                                        true,
                                        mZenObserver
                                );
                                Log.ix("✓ ContentObserver registered on zen_mode");
                            } catch (Throwable t) {
                                Log.ex("registerObserver failed", t);
                            }
                        }
                    });
            Log.ix("✓ Hook 7: registerZenModeListener → ContentObserver");
        } catch (Throwable t) {
            Log.ex("✗ Hook 7 failed", t);
        }
        Log.ix("=== All hooks done ===");


//        XposedHelpers.findAndHookMethod("com.xiaomi.fitness.devicesettings.common.zenmode.ZenModeSyncHelper$syncZenRule$1", lpparam.classLoader, "onError", "java.lang.String", int.class, int.class, new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                super.beforeHookedMethod(param);
//                String code = (String) param.args[0];
//                int type = (int) param.args[1];
//                int did = (int) param.args[2];
//                Log.ex("syncZenRule: onError: code = " + code + " type = " + type + " did = " + did);
//            }
//        });
//        XposedHelpers.findAndHookMethod("com.xiaomi.fitness.devicesettings.common.zenmode.ZenModeSyncHelper$syncZenRule$1", lpparam.classLoader, "onSuccess", "java.lang.String", int.class, "com.xiaomi.fitness.device.contact.export.SyncResult", new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                super.beforeHookedMethod(param);
//                // String did, int type, SyncResult result
//                String did = (String) param.args[0];
//                int type = (int) param.args[1];
//                Object result = param.args[2];
//                Log.dx("syncZenRule: onSuccess: did = " + did + " type = " + type + " result = " + result);
//            }
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                super.afterHookedMethod(param);
//            }
//        });
    }

    // ==================== 辅助方法 ====================
    private static Object getStaticField(ClassLoader cl, String className,
                                         String fieldName) {
        try {
            Field f = cl.loadClass(className).getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
