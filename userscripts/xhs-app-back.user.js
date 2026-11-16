// ==UserScript==
// @name 小红书：App 式返回增强（实验版）
// @namespace local.xhs.layout
// @version 0.1.0
// @description 底部栏目不累积历史；从笔记返回时恢复列表位置。保留官网路由。
// @match https://www.xiaohongshu.com/*
// @match https://www.rednote.com/*
// @run-at document-start
// ==/UserScript==

(() => {
  'use strict';
  if (!['www.xiaohongshu.com', 'www.rednote.com'].includes(location.hostname)) return;
  if (window.top !== window.self || window.__xhsAppBack) return;
  window.__xhsAppBack = true;

  // Web code cannot consume Android Back when Hermit closes its activity.
  // Do not manufacture endless history entries to trap the user in the site.
  const originalPush = history.pushState;
  const originalReplace = history.replaceState;
  let current = location.href;
  const positions = new Map();
  const topPath = path => /^\/(?:explore|red|notification|notifications|message|messages|user\/profile\/[^/]+)\/?$/.test(path);
  const notePath = path => /^\/(?:explore|discovery\/item|search_result)\/[a-f\d]{24}\/?$/i.test(path);
  let tabUntil = 0;

  function scrollers() {
    const result = [{ key: 'window', node: window, y: window.scrollY }];
    ['#mfContainer', '.feeds-page', '.main-content'].forEach(selector => {
      const node = document.querySelector(selector);
      if (node && node.scrollHeight > node.clientHeight + 5) {
        result.push({ key: selector, node, y: node.scrollTop });
      }
    });
    return result;
  }
  function save(url) {
    positions.set(url, scrollers().map(({key,y}) => ({key,y})));
    if (positions.size > 40) positions.delete(positions.keys().next().value);
  }
  function restore(url) {
    const saved = positions.get(url);
    if (!saved) return;
    const apply = () => saved.forEach(({key,y}) => {
      if (key === 'window') window.scrollTo(0,y);
      else document.querySelector(key)?.scrollTo(0,y);
    });
    // Vue renders asynchronously; give its scroll behavior time to finish.
    requestAnimationFrame(() => requestAnimationFrame(apply));
    setTimeout(() => { if (location.href === url) apply(); }, 250);
  }
  function destination(url) {
    try { return new URL(url === undefined || url === null ? location.href : url, location.href); }
    catch { return null; }
  }
  function onClick(event) {
    const target = event.target instanceof Element ? event.target : event.target?.parentElement;
    // Reuse the site's actual controls and login handling. Only mark the
    // subsequent top-level navigation as a replacement, not a new stack page.
    if (target?.closest('.bottom-menu .bottom-channel, .bottom-menu-ai .bottom-channel')) {
      tabUntil = Date.now() + 1200;
    }
    if (target?.closest('.note-item')) save(location.href);
  }
  document.addEventListener('click', onClick, true);

  function push(state, unused, url) {
    const to = destination(url);
    save(current);
    if (to && to.origin === location.origin && topPath(to.pathname) && Date.now() < tabUntil) {
      tabUntil = 0;
      // Vue Router stores a numeric stack position in history.state.
      // Keep it when replacing, and mutate the passed object so its cache agrees.
      if (state && typeof state === 'object' && typeof history.state?.position === 'number') {
        state.position = history.state.position;
      }
      const result = originalReplace.call(history, state, unused, url);
      current = location.href;
      return result;
    }
    const result = originalPush.call(history, state, unused, url);
    current = location.href;
    return result;
  }
  function replace(state, unused, url) {
    const result = originalReplace.call(history, state, unused, url);
    current = location.href;
    return result;
  }
  history.pushState = push;
  history.replaceState = replace;

  function router() {
    return document.querySelector('#app')?.__vue_app__?.config?.globalProperties?.$router;
  }
  function onPop() {
    const previous = destination(current);
    const target = new URL(location.href);
    current = target.href;
    if (previous && notePath(previous.pathname)) restore(target.href);
    // If the browser actually delivers a Back event from a primary tab,
    // return to Home. A native close with no popstate remains Hermit's job.
    if (previous && topPath(previous.pathname) && !/^\/(?:explore|red)\/?$/.test(previous.pathname)) {
      setTimeout(() => {
        const r = router();
        if (r && location.href === target.href) r.replace('/explore').catch(console.warn);
      }, 0);
    }
  }
  window.addEventListener('popstate', onPop);
})();
