(() => {
  const visible = el => !!el && el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden';
  const active = document.activeElement;
  if (active && (active.matches('input, textarea') || active.isContentEditable)) { active.blur(); return 'handled'; }
  // Only close controls within identified dialogs/viewers; never click arbitrary page buttons.
  const layers = [...document.querySelectorAll('[role="dialog"], .el-image-viewer__wrapper, .image-viewer, .image-preview-modal')].filter(visible).reverse();
  for (const layer of layers) {
    const button = [...layer.querySelectorAll('[aria-label="Close"], [aria-label="关闭"], .el-image-viewer__close, .close-icon-wrapper, .close-circle, button.close')].find(visible);
    if (button) { button.click(); return 'handled'; }
  }
  const path = location.pathname;
  if (/^\/(?:explore|discovery\/item|search_result)\/[a-f\d]{24}\/?$/i.test(path)) {
    const close = [...document.querySelectorAll('.close-circle')].find(visible);
    if (close) { close.click(); return 'handled'; }
    return 'back';
  }
  if (/^\/(?:explore)?\/?$/.test(path)) return 'exit';
  if (/^\/(?:red|notification|notifications|message|messages|user\/profile\/[^/]+)\/?$/.test(path)) return 'home';
  return 'back';
})();
