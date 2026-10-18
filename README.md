# 定时快捷 (RoutineX)

到点自动执行某个 App 长按菜单里的快捷方式，锁屏时等你解锁再补跑。

三星的「模式和日常程序」里可以直接选到第三方 App 的长按快捷方式，小米这边没有。这个应用就是补这块空缺，第一个用例是比亚迪 App 每天早上自动签到。

---

## 能做什么

| 能力 | 说明 |
| :--- | :--- |
| 选应用 | 列出手机里所有可启动的应用 |
| 选快捷方式 | 读出该应用的全部动态快捷方式（长按桌面图标弹出来的那几个） |
| 定时 | 指定时分，按天或按星期重复 |
| 锁屏补跑 | 到点时手机锁着，就先挂起，等你下次解锁立刻执行 |
| 直接打开应用 | 不需要快捷方式的场景，只把 App 拉起来 |

## 怎么用

1. 打开应用，点右下角 `+`
2. 选目标应用，选它的某个快捷方式
3. 设置时间和重复规则，保存

编辑页里的「测试执行一次」会立刻跑一遍，用来确认快捷方式有效。

## 权限

| 权限 | 用途 | 必须 |
| :--- | :--- | :---: |
| root | 读取快捷方式、执行快捷方式 | 是 |
| 通知 | 显示「等待解锁」和前台服务 | 是（Android 13+） |
| 精确闹钟 | 准点触发 | 是 |
| 电池优化白名单 | 后台服务不被清掉 | 建议 |
| 自启动（小米） | 同上 | 建议 |

前两项做不到就基本用不了，主界面会把还缺的项列出来，点按钮能直接跳到对应设置页。

## 两个绕不过去的技术点

### 一、快捷方式怎么读到完整的启动参数

`cmd shortcut get-shortcuts --flags 15 <包名>` 只能看到 ID 和名字，intent 部分被截断成 `...`。完整 URI 在系统里：

```
/data/system_ce/0/shortcut_service/packages/<包名>.xml
```

这个文件是二进制封装，但 URI 是明文串，root 读出来扫一遍就能拿到。比亚迪的四条快捷方式差别全在 URL 参数里，最后一段是个 base64 的方法名，解码出来是 `daySign`，也就是每日签到。

### 二、执行必须走 root

比亚迪的快捷方式指向 `JumpNaviActivityHost`，这个 Activity 挂了一个 signature 级的自定义权限：

```
com.byd.aeri.jump.permission
```

普通应用、甚至 adb shell 直接 `am start` 都会被 `SecurityException` 拒掉。只有 root 能绕过这个检查，所以执行统一走：

```bash
su -c "am start -a android.intent.action.VIEW -f 0x10000000 -d '<完整 URI>'"
```

把 URI 里的 `path_jump_navi_host` 改成 `path_jump_navi` 确实能免权限启动，但语义丢了，只会打开应用主界面，不会签到。这条路走不通。

## 锁屏补跑踩的坑

一开始只用 `ACTION_USER_PRESENT` 广播监听解锁，结果通知出来了但任务没执行。

原因是 MIUI 会把闹钟压到解锁那一刻才投递。服务刚起来，系统的解锁广播已经发完，接收器注册得太晚，永远等不到。日志里看得很清楚：闹钟 19:26:00 触发，人 19:26:55 解锁，中间 55 秒服务一直挂着，一次都没醒。

现在改成两条腿走路：

- 广播当快路径
- 每 500ms 轮询一次 `KeyguardManager.isKeyguardLocked() || !PowerManager.isInteractive()`，状态一变就执行

不看信号，只看状态。实测解锁到执行之间是毫秒级。

## 日志

运行日志默认关闭，主界面设置区可以打开，也可以手动清除。打开后写在这里：

```bash
adb shell run-as com.z7ui.routinex cat files/rx.log
```

之所以做成落盘而不是只打 logcat，是因为 MIUI 上 logcat 经常抓不到自己应用的行，排查时等于没有。

## 已知限制

- 只有动态快捷方式（`ShortcutManager` 注册的那种）能读出来，包在 APK 里写死的静态快捷方式读不到
- 需要 root，未 root 的设备只能「直接打开应用」
- 快捷方式的 URI 带有时效性参数的应用可能失效，需要重新选一次
- 在小米以外的机型上没测过，理论上原生 Android 也能跑

## 构建

```powershell
# 需要 JDK 17+ 和 Android SDK（compileSdk 37）
.\gradlew.bat assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

## 作者

Deepseek · B站 [@z7ui](https://space.bilibili.com/4841227)
