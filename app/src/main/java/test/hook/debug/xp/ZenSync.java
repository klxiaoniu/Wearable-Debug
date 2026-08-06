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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;

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
    private String CLS_ZEN_RULE;
    private ContentObserver mZenObserver;

    public static ZenSync INSTANCE = new ZenSync();

    public void hook(XC_LoadPackage.LoadPackageParam lpparam) {
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

        if (!lpparam.processName.endsWith(":device")) return;

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
        // 只在handleDeviceZenRule被调用，被hook后不调用，不用处理
        // ================================================================
//        try {
//            XposedHelpers.findAndHookMethod(CLS_ZEN_SYNC_HELPER, lpparam.classLoader,
//                    "updatePhoneZenRules",
//                    Context.class, boolean.class, long.class, List.class,
//                    new XC_MethodHook() {
//                        @Override
//                        protected void beforeHookedMethod(MethodHookParam param) {
//                            boolean manualState = (boolean) param.args[1];
//                            List<Object> rules = (List<Object>) param.args[3];
//                            Log.dx("updatePhoneZenRules: manualState = " + manualState + " rules = " + rules);
//                            try {
//                                NotificationManager nm = (NotificationManager)
//                                        ((Context) param.args[0]).getSystemService(
//                                                Context.NOTIFICATION_SERVICE);
//                                nm.setInterruptionFilter(manualState
//                                        ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
//                                        : NotificationManager.INTERRUPTION_FILTER_ALL);
//                                Log.dx("setInterruptionFilter: "
//                                        + (manualState ? "PRIORITY" : "ALL"));
//                            } catch (Throwable t) {
//                                Log.ex("updatePhoneZenRules error", t);
//                            }
//                            param.setResult(null);
//                        }
//                    });
//            Log.ix("✓ Hook 4: updatePhoneZenRules → setInterruptionFilter");
//        } catch (Throwable t) {
//            Log.ex("✗ Hook 4 failed", t);
//        }
        // ================================================================
        // Hook 5: handleDeviceZenRule(Context, mnq$a)
        // 解析规则确定是否开启勿扰
        // ================================================================
        try {
//            Lcom/xiaomi/fitness/devicesettings/common/zenmode/ZenModeSyncHelper;->getMiuiZenRules(Landroid/content/Context;)Ljava/util/List;
            Method method = XposedHelpers.findMethodExact(CLS_ZEN_SYNC_HELPER, lpparam.classLoader, "getMiuiZenRules", Context.class);
            // 获取方法声明的泛型返回类型（包含 List<T> 中的 T）
            Type genericReturnType = method.getGenericReturnType();

//            if (genericReturnType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericReturnType;
            // 获取实际的类型参数数组（List 只有一个泛型参数）
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments.length > 0) {
                Type elementType = actualTypeArguments[0];

                // 如果是一个具体的 Class，可以直接强转
                if (elementType instanceof Class) {
                    Class<?> clazz = (Class<?>) elementType;
                    Log.ix("CLS_ZEN_RULE: " + clazz.getName());
                    CLS_ZEN_RULE = clazz.getName();
                }
            }
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
                                    NotificationManager nm = (NotificationManager)
                                            ctx.getSystemService(Context.NOTIFICATION_SERVICE);

                                    boolean hasActiveManualRule = false;   // 是否有生效的手动规则
                                    boolean hasActiveAutoRule = false;     // 是否有生效的自动规则

                                    for (Object ruleObj : rules) {
                                        if (ruleObj == null) continue;

                                        Rule rule = new Rule(ruleObj);
                                        Log.dx(rule.toString());
                                        boolean isToday = isTodayInRepeatMask(rule.condition.repeatDaysMask);

                                        // 1. 判断规则是否有效：规则必须启用 且 今天在重复周期内
                                        boolean isRuleEnabled = rule.state == 1 && rule.conditionOverride != 2 && isToday;

                                        if (!isRuleEnabled) {
                                            continue; // 规则无效，跳过
                                        }

                                        // 2. 判断当前时间是否在生效时间段内
                                        // 不是空Condition
                                        if (!(rule.condition.start.hour == 0 && rule.condition.start.minute == 0 && rule.condition.end.hour == 0 && rule.condition.end.minute == 0)) {
                                            int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                                            int currentMinute = Calendar.getInstance().get(Calendar.MINUTE);
                                            int currentTotalMinutes = currentHour * 60 + currentMinute;
                                            int startTotalMinutes = rule.condition.start.hour * 60 + rule.condition.start.minute;
                                            int endTotalMinutes = rule.condition.end.hour * 60 + rule.condition.end.minute;

                                            // 处理跨天情况 (例如 22:00 - 06:00)
                                            boolean isInTimeRange;
                                            if (startTotalMinutes < endTotalMinutes) {
                                                // 不跨天
                                                isInTimeRange = (currentTotalMinutes >= startTotalMinutes && currentTotalMinutes < endTotalMinutes);
                                            } else {
                                                // 跨天
                                                isInTimeRange = (currentTotalMinutes >= startTotalMinutes || currentTotalMinutes < endTotalMinutes);
                                            }

                                            if (!isInTimeRange) {
                                                continue; // 不在时间段内，跳过
                                            }
                                        }

                                        // 3. 根据规则类型进行分类统计
                                        if (rule.isManual) {
                                            // 手动规则：只要有一条生效，最终结果就是开启DND
                                            hasActiveManualRule = true;
                                            // 手动规则优先级最高，可以提前跳出循环
                                            break;
                                        } else {
                                            // 自动规则：标记存在生效的自动规则
                                            hasActiveAutoRule = true;
                                            // 如果有多个自动规则同时生效，系统会选择一个应用
                                        }
                                    }

                                    // ----- 循环结束后，得出最终决策 -----
                                    boolean finalDNDState;
                                    if (hasActiveManualRule) {
                                        // 任何手动规则生效 → 开启DND
                                        finalDNDState = true;
                                    } else if (hasActiveAutoRule) {
                                        // 没有任何手动规则，但有自动规则生效 → 开启DND
                                        finalDNDState = true;
                                    } else {
                                        // 没有任何规则生效 → 关闭DND
                                        finalDNDState = false;
                                    }

                                    nm.setInterruptionFilter(finalDNDState
                                            ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                                            : NotificationManager.INTERRUPTION_FILTER_ALL);
                                    Log.dx("Device→Phone: DND "
                                            + (finalDNDState ? "ON" : "OFF"));

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

    private static String getStringField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(obj);
            return val instanceof String ? (String) val : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 判断今天是否在 repeatDaysMask 中
     *
     * @param repeatDaysMask 位掩码，例：工作日 周一~周五 = 31，每天 = 127
     * @return true=今天需要重复，false=今天不重复
     */
    public static boolean isTodayInRepeatMask(int repeatDaysMask) {
        if (repeatDaysMask == 0) return true;
        int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK); // 1=周日 ... 7=周六
        // 将 Calendar 的星期几转换为掩码位
        int maskBit;
        if (dayOfWeek == Calendar.SUNDAY) {
            maskBit = 1 << 6;   // 64
        } else {
            maskBit = 1 << (dayOfWeek - Calendar.MONDAY);
            // Monday=2 -> 1<<0 = 1
            // Tuesday=3 -> 1<<1 = 2
            // Wednesday=4 -> 1<<2 = 4
            // Thursday=5 -> 1<<3 = 8
            // Friday=6 -> 1<<4 = 16
            // Saturday=7 -> 1<<5 = 32
        }
        return (repeatDaysMask & maskBit) != 0;
    }

    /**
     * 规则pb解析类，取值含义仅供参考
     */
    static class Rule {
        boolean isManual;
        String name;
        /**
         * 0-未知，1-已激活，2-未激活
         */
        int state;
        /**
         * 0-无覆盖，1-强制启用，2-强制停用
         */
        int conditionOverride;
        int lastActivationTime;
        /**
         * 规则生效时间段，环9全为0
         */
        Condition condition;

        @Override
        public String toString() {
            return "Rule{" +
                    "isManual=" + isManual +
                    ", name='" + name + '\'' +
                    ", state=" + state +
                    ", conditionOverride=" + conditionOverride +
                    ", lastActivationTime=" + lastActivationTime +
                    ", condition=" + condition +
                    '}';
        }

        static class Condition {
            Time start;
            Time end;
            int repeatDaysMask;

            @Override
            public String toString() {
                return "Condition{" +
                        "start=" + start +
                        ", end=" + end +
                        ", repeatDaysMask=" + repeatDaysMask +
                        '}';
            }

            static class Time {
                int hour;
                int minute;

                Time(Object time) {
                    hour = XposedHelpers.getIntField(time, "c");
                    minute = XposedHelpers.getIntField(time, "d");
                }

                @Override
                public String toString() {
                    return "Time{" +
                            "hour=" + hour +
                            ", minute=" + minute +
                            '}';
                }
            }

            Condition(Object condition) {
                start = new Time(XposedHelpers.getObjectField(condition, "c"));
                end = new Time(XposedHelpers.getObjectField(condition, "d"));
                repeatDaysMask = XposedHelpers.getIntField(condition, "e");
            }
        }

        Rule(Object rule) {
            isManual = XposedHelpers.getBooleanField(rule, "c");
            name = getStringField(rule, "d");
            state = XposedHelpers.getIntField(rule, "e");
            conditionOverride = XposedHelpers.getIntField(rule, "f");
            lastActivationTime = XposedHelpers.getIntField(rule, "g");
            condition = new Condition(XposedHelpers.getObjectField(rule, "h"));
        }

    }
}
