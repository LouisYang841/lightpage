# 轻页 (Lightpage)

把小红书**网页版**在手机上显示成「双列 + 底部导航」的显示适配，包含一个用户脚本和一个可选的极简 Android 壳。

> **非官方项目。** 本项目与小红书（Xiaohongshu / RED / rednote）及其关联公司**无任何隶属、赞助、授权或认可关系**。
> 项目名「轻页」、图标和代码均由本项目作者提供，不包含官方 Logo、官方代码或官方素材。
> 所有内容和服务的版权归原权利人所有，本项目只调整网页在你设备上的显示方式。

![轻页图标](android/icon-preview.png)

## 这是什么

- 一个用户脚本：让官网在手机/宽屏上都保持双列卡片、显示紧凑底部导航、去掉导航下方的过大空白。
- 一个可选 Android 壳（`local.lightpage.xhs`，约 190 行 Java）：系统 WebView 加载官网，注入上面的脚本，并把系统返回键映射成接近 App 的行为。
- 一份可复现的命令行构建脚本（aapt2 → ECJ → D8 → zipalign → apksigner），不需要 Android Studio 或 Gradle。

## 这不是什么

刻意不做下面这些事，既是技术选择，也是为了把风险压到最低：

| 不做 | 说明 |
| --- | --- |
| 不绕过登录、鉴权、风控 | 你用自己的账号正常登录，登录态只存在本机 WebView |
| 不调用非公开接口 | 只有网页本身，不逆向、不伪造签名、不抓私有 API |
| 不下载、导出、去水印、批量保存内容 | 没有任何内容获取功能 |
| 不代理、不中转、不自建服务器 | 没有后端，没有统计 SDK，没有远程配置 |
| 不注入广告、不收集数据 | 壳只声明 `INTERNET` 权限，无 JS 桥 |
| 不修改网站内容与版权声明 | 只加 CSS 和少量显示层补丁 |

## 目录

```
userscripts/   两个用户脚本（唯一的源码副本，构建时直接打进 APK）
android/       壳源码、资源、Manifest、命令行构建脚本
docs/          实现说明与验证状态
.github/       CI：只在 Actions 里产出测试 APK 工件，不发布 Release
```

## 用用户脚本（Hermit 等支持 UserScript 的浏览器）

1. 打开 `userscripts/xhs-mobile-layout.user.js`，按你的浏览器/脚本管理器的方式安装；
2. 再装 `userscripts/xhs-app-back.user.js`（可选，改善底栏与列表返回位置）；
3. 访问 `https://www.xiaohongshu.com/explore`。

脚本只匹配 `www.xiaohongshu.com` 与 `www.rednote.com`。需要注意：脚本会覆盖 `window.innerWidth` 来触发官网自己的双列断点，这是**侵入式**兼容补丁，网站改版后可能失效或出现错位；控制台执行 `window.__xhsCompactLayout.restore()` 可当场还原。

## 构建 Android 壳

只支持 **Linux x86_64**（或 GitHub Actions 的 ubuntu runner），需要 Java 17+、curl、unzip、python3：

```bash
cd android
bash build.sh
# 产物：android/out/lightpage-0.2.0-test.apk
```

首次运行会下载并校验 build-tools 35、platform 35、ECJ 3.40.0 到 `android/.tools/`。
没有密钥时会生成测试密钥 `android/signing/lightpage-test.jks`（口令 `android`）；可以用环境变量替换：

```bash
LIGHTPAGE_KEYSTORE=/path/your.jks LIGHTPAGE_KEYSTORE_PASS=*** \
LIGHTPAGE_KEY_ALIAS=your-alias bash build.sh
```

**签名一致才能覆盖升级。** 每次 CI 都用临时密钥、或你换了密钥，都必须先卸载旧版本再安装。
ARM64 Termux 原生环境跑不了这个脚本（下载的是 x86_64 工具链），可以用 GitHub Actions 构建。

## 已知限制（都是实测结论，不是猜测）

- 只做过编译、APK 结构、签名校验和离线 JS 模拟；**没有在真机上完成端到端验证**。
- 双列/底栏依赖官网当前的类名与 696px 断点，官网改版即可能失效。
- 第三方登录（微信/QQ/微博/Apple）走的是 `open.weixin.qq.com` 等少数授权域名，已放行在应用内；范围外的站外跳转一律需要你确认后才打开，未知弹层可能仍回退到网页历史。
- 下载、拍摄、推送、播放器全屏、软键盘适配未验证；视频全屏是基础实现。
- 桌面 UA 是有意为之（官网据此给出双列布局），副作用是部分移动端专用功能不出现。

## 合规与风险

- **性质**：本项目是非官方的个人显示适配工具，不是小红书的客户端、镜像或替代品。
- **协议**：使用官网即受其用户协议与隐私政策约束。自动注入脚本、客户端外壳这类用法**可能与平台条款存在冲突**，是否使用、如何使用由你自行判断并承担后果。这不是法律意见。
- **商标**：小红书 / RED / rednote 等是其权利人的商标。本项目只在描述兼容对象时以指示性方式提及，不用作品牌名、图标或包名主体。
- **分发**：仓库只放本项目自己写的代码；不含官方 JS/CSS 快照、不含 APK、不含任何签名私钥或凭据。
- **响应**：如果权利人认为本项目不当，请开 issue 说明，我会在确认后移除相关代码或停止分发。
- **授权**：未选择开源许可证，**保留所有权利**（fork/学习请先联系）。如需明确的开源许可（如 MIT/Apache-2.0）请开 issue 讨论。

## English

Lightpage is an **unofficial** viewer tweak for the xiaohongshu.com / rednote.com **website**: a userscript that forces the two-column feed plus a compact bottom bar, and an optional ~190-line Android WebView shell around it. Not affiliated with, endorsed by, or sponsored by Xiaohongshu/RED. No private APIs, no content downloading, no analytics, no backend, no official assets. It is a display-layer compatibility patch: the site can break it at any time, and using it may conflict with the site's terms of service — your call, your risk. No open-source license is granted; all rights reserved. No APKs are distributed here.
