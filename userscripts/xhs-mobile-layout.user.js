// ==UserScript==
// @name 小红书：固定双列与紧凑底部导航
// @namespace local.xhs.layout
// @version 0.2.0
// @description 双列内容居中显示，启用原站底栏并移除过大的底部留白。实验版。
// @match https://www.xiaohongshu.com/*
// @match https://www.rednote.com/*
// @run-at document-start
// ==/UserScript==

(() => {
  'use strict';
  if (!['www.xiaohongshu.com', 'www.rednote.com'].includes(location.hostname)) return;
  if (window.top !== window.self || window.__xhsCompactLayout) return;
  window.__xhsCompactLayout = true;

  // The website uses window.innerWidth for its reactive waterfall layout.
  // Keep that layout's own positioning/virtualization rather than reparent cards.
  // Wide screens show a centered phone-width feed, with two columns.
  const MAX_WIDTH = 680; // below the site's 696px three-column breakpoint
  let owner = window;
  let descriptor;
  while (owner && !descriptor) {
    descriptor = Object.getOwnPropertyDescriptor(owner, 'innerWidth');
    if (!descriptor) owner = Object.getPrototypeOf(owner);
  }
  const original = Object.getOwnPropertyDescriptor(window, 'innerWidth');
  const readWidth = descriptor && descriptor.get
    ? () => descriptor.get.call(window)
    : () => document.documentElement.clientWidth;
  let widthPatched = false;
  try {
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      enumerable: descriptor ? descriptor.enumerable : true,
      get: () => Math.min(MAX_WIDTH, readWidth())
    });
    widthPatched = true;
  } catch (error) {
    console.warn('[小红书布局] 无法覆盖布局宽度；双列模式可能不生效。', error);
  }

  const ID = 'xhs-compact-layout-style';
  const css = `
    html.xhs-compact-layout .side-bar,
    html.xhs-compact-layout .side-bar-component {
      display: none !important;
    }
    html.xhs-compact-layout .main-content.with-side-bar,
    html.xhs-compact-layout .main-content.with-side-bar-collapse {
      padding-left: 0 !important;
      margin-left: 0 !important;
    }
    html.xhs-compact-layout .feeds-page {
      box-sizing: border-box !important;
      width: min(100%, ${MAX_WIDTH}px) !important;
      max-width: ${MAX_WIDTH}px !important;
      margin-left: auto !important;
      margin-right: auto !important;
      padding-bottom: 64px !important;
    }
    html.xhs-compact-layout :is(.bottom-menu, .bottom-menu-ai) {
      display: block !important;
      position: fixed !important;
      top: auto !important;
      bottom: 0 !important;
      left: 0 !important;
      right: 0 !important;
      width: 100% !important;
      box-sizing: border-box !important;
      height: 52px !important;
      min-height: 0 !important;
      max-height: 52px !important;
      padding: 0 !important;
      margin: 0 !important;
      border-top: 1px solid rgba(128,128,128,.18) !important;
    }
    html.xhs-compact-layout :is(.bottom-menu, .bottom-menu-ai) .channel-list {
      display: flex !important;
      box-sizing: border-box !important;
      align-items: center !important;
      justify-content: space-around !important;
      width: 100% !important;
      height: 51px !important;
      min-height: 0 !important;
      max-height: 51px !important;
      padding: 0 !important;
      margin: 0 !important;
      overflow: visible !important;
    }
    html.xhs-compact-layout :is(.bottom-menu, .bottom-menu-ai) .bottom-channel {
      display: flex !important;
      flex: 1 1 0 !important;
      align-items: center !important;
      justify-content: center !important;
      height: 51px !important;
      min-height: 0 !important;
      padding-top: 0 !important;
      padding-bottom: 0 !important;
      margin: 0 !important;
    }
    html.xhs-compact-layout :is(.bottom-menu, .bottom-menu-ai) .bottom-menu-component {
      display: flex !important;
    }
    html.xhs-compact-layout :is(.bottom-menu, .bottom-menu-ai) .text {
      display: none !important;
    }
  `;

  function mount() {
    const root = document.documentElement;
    if (!root) return;
    if (!root.classList.contains('xhs-compact-layout')) root.classList.add('xhs-compact-layout');
    if (document.getElementById(ID)) return;
    const style = document.createElement('style');
    style.id = ID;
    style.textContent = css;
    (document.head || root).appendChild(style);
  }
  mount();
  // document-start may run before head exists, so watch for it. The feed mutates the
  // DOM constantly while scrolling: batch the checks into one frame instead of running
  // on every mutation, which defeats the point of a lighter client.
  let scheduled = false;
  const observer = new MutationObserver(() => {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(() => { scheduled = false; mount(); });
  });
  observer.observe(document, { childList: true, subtree: true });
  const relayout = () => window.dispatchEvent(new Event('resize'));
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => { mount(); relayout(); }, { once: true });
  } else relayout();
  window.addEventListener('load', relayout, { once: true });

  // Disabling the script and reopening the Lite App completely removes the patch.
  // This helper is useful when debugging from a connected browser console.
  window.__xhsCompactLayout = {
    version: '0.2.0', widthPatched,
    restore() {
      observer.disconnect();
      if (widthPatched) {
        if (original) Object.defineProperty(window, 'innerWidth', original);
        else delete window.innerWidth;
      }
      document.getElementById(ID)?.remove();
      document.documentElement.classList.remove('xhs-compact-layout');
      delete window.__xhsCompactLayout;
      relayout();
    }
  };
})();
