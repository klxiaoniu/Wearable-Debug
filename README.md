# Wearable Debug

Xposed插件启用Mi Fitness第三方小程序安装卸载，表盘安装，固件安装等功能。

激活插件后会将Profile->About this app->User Agreement页面替换为模块功能入口。

## 注意

当前代码仅**手环11NFC**在**小米运动健康3.60.1**测试正常，其他版本、国际版理论也可使用。

## 使用

### 表盘安装

点击Profile->About this app->User Agreement后在弹窗中选择WatchFace。方案1直接选择表盘文件安装；方案2跳转App自带的表盘设计器页面。等待安装完成。

### 固件安装

**注意该功能可能导致设备变砖，请谨慎使用**

点击Profile->About this app->User Agreement后在弹窗中选择Firmware。确认警告后选择固件文件。在更新页面点击Download。

### 小程序安装

点击Profile->About this app->User Agreement后在弹窗中选择App。点击安装后直接选择小程序文件，包名自动从文件中读取；卸载需要输入完整包名后点击卸载。

### 日志拉取

点击Profile->About this app->User Agreement后在弹窗中选择Pull log。日志会输出到/sdcard/Android/data/[应用包名]/files/log/devicelog/

### 获取EncryptKey

点击Profile->About this app->User Agreement后在弹窗中选择Encrypt Key。获取当前设备上保存的EncryptKey信息。输出格式为：设备Did: [设备名称, Encrypt Key]

### 勿扰模式同步

点击Profile->About this app->User Agreement后在弹窗中选择Zen Sync。不同Hook模式的区别在弹窗中说明，可以自行切换尝试是否有效。模式切换需重启App生效。

### 其他功能

界面优化、微信通话6秒强提醒、一加日程同步修复等功能默认生效，无开关，可参考具体代码实现了解。