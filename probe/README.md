# ShortcutProbe

一个几十行的测试应用，只回答一个问题：普通第三方应用（不是默认桌面、没有 root）能不能通过 `LauncherApps` 读取和启动别的应用的快捷方式。

结论是**不能**，两个接口都被同一个异常拦掉。这个应用是为了让这个结论可复现，不是拿来日常用的。

## 跑起来

在 `routinex/` 目录下执行，用它自带的 wrapper：

```powershell
.\gradlew.bat -p probe assembleDebug
adb install -r probe\app\build\outputs\apk\debug\app-debug.apk
```

## 两种用法

界面上有三个按钮，点一下结果直接显示在屏幕上，适合手动试。

命令行更适合脚本化：

```bash
# 读取某应用的快捷方式
adb shell am broadcast -a com.z7ui.probe.shortcut.RUN \
  -n com.z7ui.probe.shortcut/.ProbeReceiver \
  --es mode enum --es pkg com.byd.aeri.caranywhere

# 按 id 启动某个快捷方式
adb shell am broadcast -a com.z7ui.probe.shortcut.RUN \
  -n com.z7ui.probe.shortcut/.ProbeReceiver \
  --es mode launch --es pkg com.byd.aeri.caranywhere --es id 3
```

结果同时写进 logcat（tag 是 `PROBE`）和 `files/probe.log`：

```bash
adb shell run-as com.z7ui.probe.shortcut cat files/probe.log
```

## 实测输出

```
hasShortcutHostPermission=false
getShortcuts  失败: java.lang.SecurityException: Caller can't access shortcut information
startShortcut 失败: java.lang.SecurityException: Caller can't access shortcut information
```

## 两个坑

1. Android 17（API 37）删掉了 `startShortcut(String, String, Rect, Bundle)` 这个四参重载，只剩带 `UserHandle` 的五参版本。照旧写法会编译不过。
2. 小米的 Greezer 会拒绝给缓存进程投递广播，日志里是 `Greezer Denial: ... need cached broadcast`。测试前先用 `am start` 把应用拉到前台，否则看起来像没反应。

## 换别的机型或应用

改 `MainActivity` 里的 `PKG` 和 `SIGN_ID` 两个常量，或者直接用广播传参，不用重新编译。

快捷方式 id 可以从系统里查：

```bash
adb shell cmd shortcut get-shortcuts --flags 15 <包名>
```
