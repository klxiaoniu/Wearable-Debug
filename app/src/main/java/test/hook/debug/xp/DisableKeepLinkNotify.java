package test.hook.debug.xp;

import com.github.kyuubiran.ezxhelper.HookFactory;
import com.github.kyuubiran.ezxhelper.Log;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;
import org.luckypray.dexkit.result.UsingFieldData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import test.hook.debug.xp.utils.DexKit;

/**
 * 关闭3.46.0i版本中出现的连接保护弹窗和红点提示
 */
public class DisableKeepLinkNotify {
    public static void disableDeviceSystemRedDot(ClassLoader loader) {
        try {
            Class<?> target = XposedHelpers.findClass("com.xiaomi.fitness.device.manager.export.bean.TabContentItem", loader);
            DexKitBridge bridge = DexKit.INSTANCE.getDexKitBridge();
            ClassData classData = bridge.getClassData(target);
            MethodDataList method = classData.getMethods().findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().name("<init>")));
            String targetName = null;
            for (int i = 0; i < method.size(); i++) {
                MethodData methodData = method.get(i);
                for (UsingFieldData field : methodData.getUsingFields()) {
                    String name = field.getField().getName();
                    if (name.endsWith("Dot")) {
                        targetName = name;
                        break;
                    }
                }
                if (targetName != null) {
                    break;
                }
            }

            if (targetName == null) {
                return;
            }
            Field field = target.getDeclaredField(targetName);
            field.setAccessible(true);

            for (int i = 0; i < method.size(); i++) {
                MethodData methodData = method.get(i);
                Constructor<?> constructor = methodData.getConstructorInstance(loader);
                HookFactory.createConstructorAfterHook(constructor, methodHookParam -> {
                    try {
                        field.set(methodHookParam.thisObject, null);
                    } catch (IllegalAccessException e) {
                        Log.e(e, "");
                    }
                });
            }
        } catch (NoSuchMethodError | Exception e) {
            Log.e("Failed to disable red dot for device settings", e);
        }
    }

    public static void disableTabRedDot(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod("com.xiaomi.fitness.main.MainActivity", loader, "refreshDeviceTabIcon", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return null;
                }
            });
        } catch (NoSuchMethodError | Exception e) {
            Log.e("Failed to disable red dot for tab", e);
        }
    }

    public static void disableDialog(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod("com.xiaomi.fitness.main.MainActivity", loader, "showKeepLinkDialog", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return null;
                }
            });
        } catch (NoSuchMethodError | Exception e) {
            Log.e("Failed to disable keep link dialog", e);
        }
    }
}
