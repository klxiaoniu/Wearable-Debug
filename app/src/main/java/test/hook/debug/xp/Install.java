package test.hook.debug.xp;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.kyuubiran.ezxhelper.ClassUtils;
import com.github.kyuubiran.ezxhelper.Log;
import com.github.kyuubiran.ezxhelper.finders.MethodFinder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.json.JSONObject;

import de.robv.android.xposed.XposedHelpers;
import test.hook.debug.xp.utils.Save;
import test.hook.debug.xp.utils.SignUtils;

public class Install {
    /**
     * 安装第三方应用时上报的版本号
     */
    private static final int THIRD_APP_VERSION_CODE = 10;
    /**
     * 应用文件里读不到包名时使用的占位包名
     */
    private static final String FALLBACK_PACKAGE_NAME = "com.mi.wearable.thirdapp";
    /**
     * 第三方应用文件的传输类型与子类型
     */
    private static final int THIRD_APP_FILE_TYPE = 4;
    private static final int THIRD_APP_FILE_DETAIL_TYPE = 0;
    /**
     * AppStatusInfo 中的应用状态码
     */
    private static final int STATUS_INSTALLING = 10;
    private static final int STATUS_INSTALL_SUCCESS = 11;
    private static final int STATUS_INSTALL_FAILED = 12;
    private static final int STATUS_TRANSMIT_FAILED = 9;
    private static final int STATUS_UNINSTALL_FAILED = 16;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * 处理 Proxy 收到的 Object 方法，避免 equals/hashCode 被当成回调方法处理
     */
    private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals":
                return proxy == (args != null && args.length > 0 ? args[0] : null);
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "WearableDebugProxy@" + Integer.toHexString(System.identityHashCode(proxy));
            default:
                return null;
        }
    }

    /**
     * 读取指定表盘文件ID
     *
     * @param file 表盘文件路径
     * @return 表盘文件ID
     */
    public static String getWatchFaceId(File file) {
        if (!file.exists()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        try (FileInputStream stream = new FileInputStream(file)) {
            if (stream.skip(40) != 40) {
                return null;
            }
            int read;
            while ((read = stream.read()) != 0) {
                builder.append((char) read);
            }
        } catch (IOException e) {
            Log.ex(e, "getWatchFaceId");
        }
        return builder.toString();
    }

    /**
     * 启动升级页面
     *
     * @param loader  当前类加载器
     * @param context 上下文用于Intent
     * @param path    固件文件位置
     */
    public static void invokeUpdate(ClassLoader loader, Object context, String path) {
        try {
            Class<?> checkUpdateExtKt = XposedHelpers.findClass("com.mi.fitness.checkupdate.export.CheckUpdateExtKt", loader);
            Object manager = XposedHelpers.callStaticMethod(checkUpdateExtKt, "getCheckUpdateManager");

            XposedHelpers.callMethod(manager, "manualUpgrade", new Class[]{Context.class, String.class, boolean.class},
                    context, path, false);
        } catch (Throwable e) {
            Log.ex(e, "invokeUpdate new version not found");

            // 旧版本
            Class<?> checkUpdateManagerImpl = XposedHelpers.findClass("com.mi.fitness.checkupdate.util.CheckUpdateManagerImpl", loader);
            Object manager = XposedHelpers.newInstance(checkUpdateManagerImpl);
            // boolean参数为true时为静默安装
            XposedHelpers.callMethod(manager, "manualUpgrade", new Class[]{Context.class, String.class, boolean.class},
                    context, path, false);
        }
    }

    /**
     * 安装表盘文件
     *
     * @param loader  当前类加载器
     * @param file    表盘文件路径
     * @param context context上下文
     */
    public static void installWatchFace(ClassLoader loader, File file, Context context) {
        try {
            String watchFaceId = getWatchFaceId(file);
            if (watchFaceId == null) {
                Log.ex("Failed to get id from " + file.getAbsolutePath(), null);
                return;
            }

            Class<?> model;
            try {
                // 新
                model = XposedHelpers.findClass("com.xiaomi.fitness.watch.face.viewmodel.FaceBleInfoViewModel", loader);
            } catch (XposedHelpers.ClassNotFoundError e) {
                model = XposedHelpers.findClass("com.xiaomi.fitness.watch.face.viewmodel.FaceDetailViewModel", loader);
            }

            Object instance = model.newInstance();
            Object controller = XposedHelpers.getObjectField(instance, "faceInstallController");

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setCancelable(false);
            ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setIndeterminate(false);

            builder.setView(progressBar);
            AlertDialog dialog = builder.create();
            dialog.show();

            Class<?> callbackClass = XposedHelpers.findClass("com.xiaomi.fitness.watch.face.install.FaceInstallPushCallback", loader);
            Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                try {
                    switch (method.getName()) {
                        case "onProgress": {
                            int pos = (int) args[0];
                            Log.ix("p: " + pos, null);
                            progressBar.setProgress(pos);
                            break;
                        }
                        case "onFinish": {
                            boolean success = (boolean) args[0];
                            int code = (int) args[1];
                            Log.ix("success: " + success + " code: " + code, null);
                            dialog.dismiss();
                            break;
                        }
                        case "onStart": {
                            Log.ix("start install", null);
                            break;
                        }
                        default: {
                            Log.ex("Unknown value: " + method.getName(), null);
                        }
                    }
                } catch (Throwable e) {
                    Log.ex(e, method.toString());
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                }
                return null;
            });

            try {
                // 构造 Kotlin 的 Function3 代理，用于 preInstall 回调
                Class<?> function3Class = XposedHelpers.findClass("kotlin.jvm.functions.Function3", loader);
                Object dummyAction = Proxy.newProxyInstance(loader, new Class<?>[]{function3Class}, (proxy, method, args) -> {
                    // 安全起见，返回 kotlin.Unit.INSTANCE 以绕过 Kotlin 的非空检查
                    return XposedHelpers.getStaticObjectField(XposedHelpers.findClass("kotlin.Unit", loader), "INSTANCE");
                });

                // 先调用 preInstall 建立握手
                // preInstall(String id, String path, long version, long size, boolean needCheckStorage, String license, String sign, Integer trialDuration, Function3 action)
                XposedHelpers.callMethod(controller, "preInstall",
                        new Class<?>[]{
                                String.class, String.class, long.class, long.class, boolean.class,
                                String.class, String.class, Integer.class, function3Class
                        },
                        watchFaceId, file.getAbsolutePath(), 0L, file.length(), true,
                        null, null, 0, dummyAction
                );
            } catch (Throwable error) {
                // 旧版没这步骤
            }

            // 调用 doInstall 正式传输文件
            // doInstall(String path, String id, Integer segmentLength, FaceInstallPushCallback callback)
            XposedHelpers.callMethod(controller, "doInstall",
                    new Class<?>[]{
                            String.class, String.class, Integer.class, callbackClass
                    },
                    file.getAbsolutePath(), watchFaceId, 0, callback
            );

        } catch (Throwable e) {
            Log.ex(e, "installWatchFace");
        }
    }

    /**
     * 安装第三方应用（rpk/bin）
     *
     * 包名取自应用文件内的 manifest.json，取不到时使用占位包名。
     * 先调用 DeviceModelExtKt.prepareInstallApp 预安装获取分片长度，
     * 再通过 DeviceContact.sendFile 把文件发送到手表，并监听 AppStatusManager 的安装状态。
     *
     * @param loader  当前类加载器
     * @param context context上下文
     * @param file    应用文件
     */
    public static void installApp(ClassLoader loader, Context context, File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "应用文件无效", Toast.LENGTH_SHORT).show();
            return;
        }
        String resolvedPackageName = resolvePackageName(file);
        final String packageName = (resolvedPackageName == null || resolvedPackageName.isEmpty())
                ? FALLBACK_PACKAGE_NAME : resolvedPackageName;

        try {
            Object deviceModel = getCurrentDevice(loader);
            if (deviceModel == null) {
                Toast.makeText(context, "设备未连接", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isBluetooth(loader, deviceModel)) {
                Toast.makeText(context, "当前设备不支持通过该方式安装应用", Toast.LENGTH_SHORT).show();
                return;
            }

            final String did = (String) XposedHelpers.callMethod(deviceModel, "getDid");
            Save.sign = SignUtils.generateSign(file);

            final ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setIndeterminate(true);

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("正在准备安装");
            builder.setCancelable(false);
            builder.setView(progressBar);
            final AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();

            watchAppStatus(loader, context, packageName, dialog);

            Class<?> deviceModelExtKt = XposedHelpers.findClass("com.xiaomi.xms.wearable.extensions.DeviceModelExtKt", loader);
            Method prepareInstallApp = MethodFinder.fromClass(deviceModelExtKt).filterByName("prepareInstallApp").first();
            Class<?> callbackClass = prepareInstallApp.getParameterTypes()[4];

            Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return handleObjectMethod(proxy, method, args);
                }
                switch (method.getName()) {
                    case "onResult": {
                        Integer segmentLength = (args == null || args[0] == null) ? null : (Integer) args[0];
                        MAIN_HANDLER.post(() -> {
                            if (segmentLength == null) {
                                dismissQuietly(dialog);
                                Toast.makeText(context, "预安装失败", Toast.LENGTH_LONG).show();
                                return;
                            }
                            progressBar.setIndeterminate(false);
                            dialog.setTitle("正在发送文件");
                            try {
                                sendThirdAppFile(loader, did, file, segmentLength, progressBar, dialog, context);
                            } catch (Throwable e) {
                                Log.ex(e, "sendThirdAppFile");
                                dismissQuietly(dialog);
                                Toast.makeText(context, "发送文件失败: " + e, Toast.LENGTH_LONG).show();
                            }
                        });
                        return null;
                    }
                    case "onError": {
                        int code = (args == null || args[0] == null) ? -1 : (int) args[0];
                        MAIN_HANDLER.post(() -> {
                            dismissQuietly(dialog);
                            Toast.makeText(context, "预安装失败: " + code, Toast.LENGTH_LONG).show();
                        });
                        return null;
                    }
                    default:
                        return null;
                }
            });

            // prepareInstallApp(WearableDeviceModel model, String packageName, int versionCode, int fileLength, callback)
            try {
                prepareInstallApp.invoke(null, deviceModel, packageName, THIRD_APP_VERSION_CODE, (int) file.length(), callback);
            } catch (Throwable e) {
                dismissQuietly(dialog);
                throw e;
            }
        } catch (Throwable e) {
            Log.ex(e, "installApp");
            Toast.makeText(context, "安装失败: " + e, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 从应用文件的 manifest.json 中读取包名
     *
     * @return 包名，读不到时返回 null
     */
    private static String resolvePackageName(File file) {
        try {
            String manifest = readZipText(file, "manifest.json");
            if (manifest == null) {
                return null;
            }
            JSONObject json = new JSONObject(manifest);
            for (String key : new String[]{"package", "packageName", "id"}) {
                String value = json.optString(key, "");
                if (!value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        } catch (Throwable e) {
            Log.ex(e, "resolvePackageName");
        }
        return null;
    }

    /**
     * 读取 zip 内的文本文件
     *
     * @param file      zip 文件
     * @param entryName 条目名
     * @return 文件内容，条目不存在时返回 null
     */
    private static String readZipText(File file, String entryName) throws IOException {
        try (ZipFile zipFile = new ZipFile(file)) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                entry = zipFile.getEntry("/" + entryName);
            }
            if (entry == null) {
                return null;
            }
            StringBuilder builder = new StringBuilder();
            try (InputStream stream = zipFile.getInputStream(entry);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buffer = new char[0x1000];
                int read;
                while ((read = reader.read(buffer)) != -1 && builder.length() < 0x10000) {
                    builder.append(buffer, 0, read);
                }
            }
            return builder.toString();
        }
    }

    /**
     * 把应用文件分片发送到手表，并同步进度
     */
    private static void sendThirdAppFile(ClassLoader loader, String did, File file, int segmentLength,
                                         final ProgressBar progressBar, final AlertDialog dialog, final Context context) throws Throwable {
        Class<?> deviceContactClass = XposedHelpers.findClass("com.xiaomi.fitness.device.contact.export.DeviceContact", loader);
        Class<?> deviceSyncExtKt = XposedHelpers.findClass("com.xiaomi.fitness.device.contact.export.DeviceSyncExtKt", loader);
        Class<?> listenerClass = XposedHelpers.findClass("com.xiaomi.fitness.device.contact.export.SyncStateListener", loader);

        Object companion = XposedHelpers.getStaticObjectField(deviceContactClass, "Companion");
        Object contact = XposedHelpers.callStaticMethod(deviceSyncExtKt, "getInstance", new Class<?>[]{companion.getClass()}, companion);

        Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{listenerClass}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            switch (method.getName()) {
                case "onSyncing": {
                    int progress = (args == null || args[1] == null) ? 0 : (int) args[1];
                    MAIN_HANDLER.post(() -> progressBar.setProgress(progress));
                    return null;
                }
                case "onFinish": {
                    int code = (args == null || args[1] == null) ? -1 : (int) args[1];
                    MAIN_HANDLER.post(() -> {
                        if (code == 0) {
                            progressBar.setProgress(100);
                            dismissQuietly(dialog);
                            Toast.makeText(context, "文件已发送，请在手表上确认安装结果", Toast.LENGTH_LONG).show();
                        } else {
                            dismissQuietly(dialog);
                            Toast.makeText(context, "发送失败: " + code, Toast.LENGTH_LONG).show();
                        }
                    });
                    return null;
                }
                default:
                    return null;
            }
        });

        // sendFile(String did, int type, int detailType, String path, int segmentLength, SyncStateListener listener, int linkType)
        XposedHelpers.callMethod(contact, "sendFile",
                new Class<?>[]{String.class, int.class, int.class, String.class, int.class, listenerClass, int.class},
                did, THIRD_APP_FILE_TYPE, THIRD_APP_FILE_DETAIL_TYPE, file.getAbsolutePath(), segmentLength, listener, 0);
    }

    /**
     * 监听应用安装状态，安装完成后关闭进度框
     */
    private static void watchAppStatus(ClassLoader loader, final Context context, final String packageName, final AlertDialog dialog) {
        try {
            Class<?> managerClass = XposedHelpers.findClass("com.xiaomi.xms.wearable.ui.appshop.manager.AppStatusManager", loader);
            Class<?> function1Class = XposedHelpers.findClass("kotlin.jvm.functions.Function1", loader);
            Object unit = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("kotlin.Unit", loader), "INSTANCE");
            Object manager = XposedHelpers.callStaticMethod(managerClass, "getINSTANCE");

            final Object[] holder = new Object[1];
            Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{function1Class}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return handleObjectMethod(proxy, method, args);
                }
                if (!"invoke".equals(method.getName()) || args == null || args[0] == null) {
                    return unit;
                }
                final int status = (int) XposedHelpers.callMethod(args[0], "getStatus");
                switch (status) {
                    case STATUS_INSTALLING: {
                        MAIN_HANDLER.post(() -> dialog.setTitle("正在安装，请稍候…"));
                        break;
                    }
                    case STATUS_INSTALL_SUCCESS: {
                        MAIN_HANDLER.post(() -> {
                            dismissQuietly(dialog);
                            Toast.makeText(context, "应用安装成功", Toast.LENGTH_LONG).show();
                        });
                        removeStatusListener(manager, packageName, holder[0], function1Class);
                        break;
                    }
                    case STATUS_INSTALL_FAILED:
                    case STATUS_TRANSMIT_FAILED:
                    case STATUS_UNINSTALL_FAILED: {
                        MAIN_HANDLER.post(() -> {
                            dismissQuietly(dialog);
                            Toast.makeText(context, "安装失败，状态码: " + status, Toast.LENGTH_LONG).show();
                        });
                        removeStatusListener(manager, packageName, holder[0], function1Class);
                        break;
                    }
                    default:
                        break;
                }
                return unit;
            });
            holder[0] = listener;

            // addStatusListener(String packageName, Function1<AppStatusInfo, Unit> listener)
            XposedHelpers.callMethod(manager, "addStatusListener", new Class<?>[]{String.class, function1Class}, packageName, listener);
        } catch (Throwable e) {
            Log.ex(e, "watchAppStatus");
        }
    }

    /**
     * 移除应用状态监听
     */
    private static void removeStatusListener(Object manager, String packageName, Object listener, Class<?> function1Class) {
        if (listener == null) {
            return;
        }
        try {
            XposedHelpers.callMethod(manager, "removeStatusListener", new Class<?>[]{String.class, function1Class}, packageName, listener);
        } catch (Throwable e) {
            Log.ex(e, "removeStatusListener");
        }
    }

    /**
     * 卸载手表上的第三方应用
     *
     * @param loader  当前类加载器
     * @param context context上下文
     * @param pkgName 应用包名
     */
    public static void unInstallApp(ClassLoader loader, Context context, String pkgName) {
        if (pkgName == null || pkgName.trim().isEmpty()) {
            Toast.makeText(context, "请先输入包名", Toast.LENGTH_SHORT).show();
            return;
        }
        final String packageName = pkgName.trim();

        try {
            Object deviceModel = getCurrentDevice(loader);
            if (deviceModel == null) {
                Toast.makeText(context, "设备未连接", Toast.LENGTH_SHORT).show();
                return;
            }

            String did = (String) XposedHelpers.callMethod(deviceModel, "getDid");
            byte[] fingerprint = getInstalledFingerprint(loader, packageName, did);
            if (fingerprint == null) {
                fingerprint = Save.sign;
            }
            if (fingerprint == null) {
                // SHA-1 的长度
                fingerprint = new byte[20];
            }

            Class<?> deviceModelExtKt = XposedHelpers.findClass("com.xiaomi.xms.wearable.extensions.DeviceModelExtKt", loader);
            Method uninstallApp = MethodFinder.fromClass(deviceModelExtKt).filterByName("uninstallApp").first();
            Class<?> callbackClass = uninstallApp.getParameterTypes()[3];

            Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return handleObjectMethod(proxy, method, args);
                }
                switch (method.getName()) {
                    case "onResult": {
                        MAIN_HANDLER.post(() -> Toast.makeText(context, "卸载成功", Toast.LENGTH_LONG).show());
                        return null;
                    }
                    case "onError": {
                        int code = (args == null || args[0] == null) ? -1 : (int) args[0];
                        MAIN_HANDLER.post(() -> Toast.makeText(context, "卸载失败: " + code, Toast.LENGTH_LONG).show());
                        return null;
                    }
                    default:
                        return null;
                }
            });

            // uninstallApp(WearableDeviceModel model, String packageName, byte[] fingerprint, callback)
            uninstallApp.invoke(null, deviceModel, packageName, fingerprint, callback);
        } catch (Throwable e) {
            Log.ex(e, "unInstallApp");
            Toast.makeText(context, "卸载失败: " + e, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 查询已安装应用的签名信息
     *
     * @return SHA-1 签名，未找到时返回 null
     */
    private static byte[] getInstalledFingerprint(ClassLoader loader, String pkgName, String did) {
        try {
            Class<?> repositoryClass = XposedHelpers.findClass("com.xiaomi.xms.wearable.db.WatchAppRepository", loader);
            Object companion = XposedHelpers.getStaticObjectField(repositoryClass, "Companion");
            Object repository = XposedHelpers.callMethod(companion, "getInstance");
            Object entity = XposedHelpers.callMethod(repository, "getWatchApp", pkgName, did);
            if (entity != null) {
                return (byte[]) XposedHelpers.callMethod(entity, "getFingerprint");
            }
        } catch (Throwable e) {
            Log.ex(e, "getInstalledFingerprint");
        }
        return null;
    }

    /**
     * 判断设备是否通过蓝牙连接
     */
    private static boolean isBluetooth(ClassLoader loader, Object deviceModel) {
        try {
            Class<?> deviceModelExtKt = XposedHelpers.findClass("com.xiaomi.fitness.device.manager.DeviceModelExtKt", loader);
            return (boolean) ClassUtils.invokeStaticMethodBestMatch(deviceModelExtKt, "isBluetooth", null, deviceModel);
        } catch (Throwable e) {
            Log.ex(e, "isBluetooth");
            return true;
        }
    }

    private static void dismissQuietly(AlertDialog dialog) {
        try {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 获取设备管理器
     *
     * @param loader 当前类加载器
     * @return com.xiaomi.fitness.device.manager.WearableDeviceManagerImpl
     */
    public static Object getDeviceManager(ClassLoader loader) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> deviceManager = ClassUtils.loadFirstClass("com.xiaomi.fitness.device.manager.export.DeviceManager", "com.xiaomi.fitness.device.manager.export.WearableDeviceManager");
        Object companion = XposedHelpers.getStaticObjectField(deviceManager, "Companion");
        Class<?> deviceManagerExtKt = XposedHelpers.findClass("com.xiaomi.fitness.device.manager.export.DeviceManagerExtKt", loader);
        return ClassUtils.invokeStaticMethodBestMatch(deviceManagerExtKt, "getInstance", null, companion);
    }

    public static Object getCurrentDevice(ClassLoader loader) throws ClassNotFoundException, NoSuchMethodException {
        Object instance = getDeviceManager(loader);
        Object deviceModel = XposedHelpers.callMethod(instance, "getCurrentDeviceModel");
        if (deviceModel == null || !(boolean) XposedHelpers.callMethod(deviceModel, "isDeviceConnected")) {
            return null;
        }
        return deviceModel;
    }

    /**
     * 创建临时文件，保留原始文件名的扩展名
     *
     * @param context     上下文
     * @param data        文件来源
     * @param displayName 原始文件名，可为 null
     * @return 临时文件位置
     */
    public static File saveTmpFile(Context context, Uri data, String displayName) throws Throwable {
        String name = (displayName == null || displayName.isEmpty()) ? "tmpFile" : displayName.replaceAll("[^A-Za-z0-9._-]", "_");
        File tmpFile = new File(context.getCacheDir(), name);
        try (FileOutputStream stream = new FileOutputStream(tmpFile)) {
            byte[] bytes = new byte[0x400];
            try (InputStream inputStream = context.getContentResolver().openInputStream(data)) {
                if (inputStream == null) {
                    return null;
                }
                int read;
                while ((read = inputStream.read(bytes)) != -1) {
                    stream.write(bytes, 0, read);
                }
            }
        }
        return tmpFile;
    }
}
