# 实现与验证状态

## 布局补丁怎么生效

官网用 `window.innerWidth` 决定瀑布流列数，696px 以上切三列。脚本把读到的宽度夹到 680，官网自己就会走双列逻辑；再用 CSS 隐藏侧栏、让 `.feeds-page` 居中、把 `.bottom-menu` 固定成 52px 底部导航。

- 不重新排列卡片、不接管虚拟列表，渲染仍然由官网完成。
- 覆盖 `innerWidth` 属于侵入式补丁；`window.__xhsCompactLayout.restore()` 可还原。
- 用 `MutationObserver` 兜住 head 尚未出现的时机。官网信息流在滚动时 DOM 变动极频繁，所以回调合并到一帧内执行一次，而不是每次变动都跑。
- 底栏尺寸写死 52px，官网改版或大字号模式可能错位。

## 返回与历史

`xhs-app-back.user.js` 包装 `history.pushState/replaceState`：

- 点到一级栏目后 1.2 秒内的跳转改成 `replace`，底栏不累积历史；同时把 Vue Router 的 `history.state.position` 复制过去，避免路由栈判断错乱。
- 从笔记页 popstate 回来时按 URL 恢复列表滚动位置（最多记 40 条）。
- 壳里的返回顺序：全屏视频 → 输入框失焦 → 识别到的弹层/图片预览关闭 → 笔记页关闭 → 一级栏目回首页 → 首页双击退出 → 其余走 `web.goBack()`。

## 站外跳转策略

- 官方域名 + `https`：留在应用内。
- 少数授权域名（`open.weixin.qq.com`、`open.qq.com`、`passport.weibo.com`、`appleid.apple.com` 等）：留在应用内，否则第三方登录会被打断。
- 其余 `http(s)`：用户点击才弹确认，转交系统浏览器；重定向链里的无手势跳转直接拦下并提示。
- 自定义 scheme（`weixin://` 等）：必须用户点击并确认，不自动唤起。
- 子框架（广告、嵌入播放器）一律不放行。

## 验证状态

| 项目 | 状态 |
| --- | --- |
| Java 编译、D8、aapt2 链接、zipalign | GitHub Actions 上通过（Linux x86_64 构建链） |
| v2/v3 签名与 Manifest 校验 | 已通过（artifacts 里的 APK 含 v2 签名块） |
| 布局脚本：宽度夹取、样式注入、重入、观察器合并、restore 还原 | jsdom 离线测试通过 |
| 历史包装：底栏 replace、position 保留、笔记 push | jsdom 离线测试通过 |
| 返回判定：exit / home / back / 弹层 / 输入框 | jsdom 离线测试通过 |
| APK 内容：`assets/` 下三个脚本齐全 | 已通过（核对过 CI 产物的 APK 条目） |
| 真机：登录、覆盖升级、双列、底部空白、返回、弹层、视频、软键盘 | **未验证** |
| 第三方登录、下载、拍摄、推送 | **未验证** |

离线测试只说明脚本在模拟环境里的逻辑符合预期，不能当作端到端验证。
