# Salt Lyrics · 盐词

![盐词 Salt Lyrics 项目封面：黄昏方块山谷中的漂浮双语歌词](docs/assets/saltlyrics-cover.png)

将 Salt Player 正在播放的歌词显示为 Minecraft 世界中的 3D 漂浮文字。

[English](README_EN.md) | 简体中文

本项目独立于 DangoMusic 运行，按 GPL-3.0 提取、改造其歌词渲染代码；原作者为 Sorihiko 和 DangoMusic 贡献者。来源和修改记录见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。目前为本地开发版本，游戏内效果仍需验证。

## ✨ 功能

| 功能 | 说明 |
| --- | --- |
| 世界歌词 | 3D 漂浮、切句淡出、逐字浮现与按词弹出 |
| 动画 | 可配置手绘抖动和多级柔光 |
| 位置 | 大小、距离、随机分散、朝向镜头、保持距离、留在视野和保持高度 |
| 同步 | SaltBridge 后台轮询、播放时钟、歌词更新和毫秒偏移 |
| 双语歌词 | 原文在上、翻译在下；支持独立翻译字段和相同时间戳的多行歌词 |
| 设置 | 原版选项入口，连接、播放控制、位置和外观均在设置内 |

不提供普通屏幕歌词、独立播放器面板、音乐搜索或账号登录。音乐由 Salt Player 输出；服务端无需安装本模组。

## 🚀 安装和使用

运行环境：Minecraft **1.20.1**、Fabric Loader **0.16.10 或更高**、对应的 Fabric API、Java **17 或更高**。
Salt Player 与 [SaltBridge](https://github.com/Luoning77/saltbridge) 需在同一台 Windows 电脑运行。

1. 将 `build/libs/` 中以 `-fabric.jar` 结尾的文件放进 Minecraft 实例的 `mods` 文件夹，并安装 Fabric API。不需要额外安装 Fabric Language Kotlin；Kotlin 运行库已打包。
2. 在 Salt Player 中启用 SaltBridge，开始播放有歌词的音乐。
3. 打开 Minecraft 的“选项 → 右上角盐词设置 → 连接与控制”。默认读取 SaltBridge 自动配对文件；失败时复制访问令牌，点击“从剪贴板粘贴令牌”。
4. 进入世界查看漂浮歌词，在“歌词位置”和“动画与外观”中调整效果。

可选安装 Mod Menu：在模组列表中选择“盐词”，点击配置按钮即可进入同一个设置界面；不安装时仍可从原版“选项”进入。

开关快捷键默认不绑定，可在“控制 → 按键绑定 → 盐词”中设置。F1 隐藏界面时也隐藏歌词。端口通过“应用端口并重新连接”生效；进度滑块在松开鼠标后提交跳转；外观设置立即预览，“完成”保存到磁盘。正同步偏移表示使用更靠后的歌词位置。

## ⚙️ 配置

| 路径 | 用途 |
| --- | --- |
| `config/saltlyrics/settings.json` | 外观、偏移和连接端口 |
| `config/saltlyrics/salt_token.txt` | 手动令牌；设置中仅显示末四位 |
| `~/.saltbridge/token.txt` | 手动令牌为空时读取的自动配对文件 |
| `version.properties` | 唯一项目版本源；本地构建不自增 |

逐字歌词使用播放器提供的真实时间；只有逐行时间时，按词弹出和长句拆分属于估算动画。歌词空缺或加载失败时不会从其他平台自动搜索歌词。

双语歌词按播放器提供的顺序显示。原文保留逐字动画，翻译整行淡入；双语组保持配对，不独立拆句，过长的原文和翻译会分别视觉换行。没有翻译时只显示原文，不自动生成翻译。

“歌词位置 → 显示翻译”默认开启，关闭后只显示原文，点击“完成”保存。

## 🛠️ 源码构建

使用 JDK 21 运行 Gradle，生成 Java 17 字节码。国内镜像优先，Fabric/Architectury 专用依赖保留官方仓库。

```bash
./gradlew test build sourceArchive
```

产物位于 `build/libs/`；完整源码工程压缩包在 `build/distributions/`。验证状态和游戏实测清单见 [docs/VALIDATION.md](docs/VALIDATION.md)。光影、Iris/Sodium 和其他界面 Mod 的兼容性尚未验证。首版保留原版像素字体路径，未迁移 DangoMusic 的平滑字体系统。

## 📄 许可证与致谢

本项目采用 **GPL-3.0-only**，完整条款见 [LICENSE](LICENSE)。分发二进制时须同时按许可证提供对应源码和构建材料。

感谢 [DangoMusic](https://github.com/Sorihiko/DangoMusic) 的原始歌词动画和渲染实现。本项目由 Luoning77 独立维护，不是 DangoMusic 官方版本。
