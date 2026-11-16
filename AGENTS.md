# 仓库约定 / Notes for coding agents

本文件给接续维护的编码代理看。用户可见的讨论用中文。

## 硬性边界

- 不提交签名私钥（`*.jks` / `*.keystore`）、凭据、Cookie、Token。
- 不提交官网 JS/CSS 快照、官方 Logo 或素材、编译产物（APK/AAB/dex）。
- 不发布 APK Release；CI 只产出测试用 artifact。
- 不添加绕过登录、鉴权、付费墙或风控的功能，不调用非公开接口，不做内容下载/去水印。
- 不把「编译通过 / 离线模拟通过 / 签名校验通过」说成「真机验证通过」。验证状态写在 `docs/notes.md`。
- 项目名是「轻页」。不要改回带平台名的名称。图标与配色是用户选定的（目前仍是平台红 #FF2442），要改先问用户。
- 未经用户明确同意不改包名 `local.lightpage.xhs`、不加开源许可证。

## 结构

- `userscripts/*.user.js` 是脚本的唯一源码副本；`android/build.sh` 会把它们拷进 APK assets，不要另存一份。
- `android/assets/back.js` 只给壳用（返回键判定），不对外分发。
- 改 `window.innerWidth` / `history` 包装属于侵入式补丁，改动后请在 `docs/notes.md` 记录影响面。
