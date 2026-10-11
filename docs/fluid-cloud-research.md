# 流体云样式研究与 1.0.3 实现

2026-10-05。

## 可用路径

OPPO 官方组件文档提供胶囊 compact、展开 expanded，以及 leading/center/trailing 分区。模板能配置文字、图片、进度、按钮和部分更新动效；它是一套模板开发机制，并非把对应字段塞入普通 Android Notification 就能使用。本项目没有发布 OPPO 专属模板，因此不宣称已经接入这条路径。

来源：https://open.oppomobile.com/new/developmentDoc/info?id=12703

Android ProgressStyle 的正式结构包括标题、内容、subtext、largeIcon、进度和动作按钮。官方允许通过配图帮助识别任务；Live Updates 不允许自定义 RemoteViews 和 colorized 背景。

来源：https://developer.android.com/develop/ui/compose/notifications/progress-centric
来源：https://developer.android.com/develop/ui/views/notifications/live-update

## 本版实际修改

- 胶囊显示上传/下载小图标与百分比；写入确认阶段使用“保存”。
- 展开卡片采用文件类型配图：视频、图片、音频、压缩包、文档。配图在本地 Canvas 绘制并缓存，不联网拉图，也不包含个人文件缩略图。
- 标题为任务状态、文件类型、百分比；文件名独立显示；过长名称保留完整 Unicode 字符、前缀与扩展名。
- 副标题有速度时显示实际速度和预计剩余时间，没有足够采样时显示已传输/总量。
- 单段主题色进度条，无轨迹大箭头。
- 普通进度更新最多约每秒一次；开始状态和保存阶段及时更新。沿用同一通知 ID，不反复删除/创建通知。
- 使用单调时间、服务器确认字节数和指数平滑估算速率。恢复前已存在的字节不算作新流量；不足1秒不推算速度。下载使用实际写入字节。预计时间只是估算，不会伪造数值。
- 应用内传输卡片同样显示速度与预计剩余时间。

## 验证边界

4项速率/文件名测试、6项界面渲染测试。构建、Lint、签名验证另见 deliverables/verification-1.0.3.txt。

界面测试不能证明 OPPO 流体云最终布局。字体、系统卡片背景和展开收起动画仍由设备主题与系统实现决定；未执行 OPPO 实机回归。
