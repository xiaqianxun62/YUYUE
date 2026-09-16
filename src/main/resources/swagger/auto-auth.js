/*!
 * Swagger UI 自动登录增强（由 SwaggerAutoTokenFilter 注入到 swagger-ui 页面，勿手动引入）
 *
 * 1. 在 UI 里调用 POST /auth/login（或 /auth/register）成功后，自动提取 data.token 并完成授权，
 *    之后点任何接口的 "Execute" 都会自动带上 Authorization: Bearer <token>
 * 2. token 存入 localStorage，刷新页面 / 重开浏览器后自动恢复（有效期与 JWT 一致）
 * 3. 响应 401 时自动清除失效 token 并提示重新登录
 */
(function () {
  var SCHEME = 'bearerAuth';   // 与 OpenApiConfig.SECURITY_SCHEME 保持一致
  var STORE_KEY = 'yuyue:swagger:token';
  var TTL = 72 * 3600 * 1000;  // 与 yuyue.jwt.expire-hours 保持一致
  var AUTH_API = /\/auth\/(login|register)(\?|#|$)/;

  var token = null;

  /* ---------------- 本地存储 ---------------- */

  function load() {
    try {
      var raw = window.localStorage.getItem(STORE_KEY);
      if (!raw) return null;
      var data = JSON.parse(raw);
      if (!data || !data.token || (data.exp && data.exp < Date.now())) {
        window.localStorage.removeItem(STORE_KEY);
        return null;
      }
      return data.token;
    } catch (e) {
      return null;
    }
  }

  function save(value) {
    try {
      window.localStorage.setItem(STORE_KEY, JSON.stringify({token: value, exp: Date.now() + TTL}));
    } catch (e) { /* 隐私模式下忽略 */ }
  }

  function clear() {
    try { window.localStorage.removeItem(STORE_KEY); } catch (e) { /* ignore */ }
    token = null;
  }

  /* ---------------- 授权 ---------------- */

  function extract(body) {
    if (!body || typeof body !== 'object') return null;
    if (body.data && typeof body.data === 'object' && body.data.token) return body.data.token;
    if (typeof body.token === 'string') return body.token;
    return null;
  }

  function authorize(value) {
    if (!value || !window.ui) return false;
    try {
      if (typeof window.ui.preauthorizeApiKey === 'function') {
        window.ui.preauthorizeApiKey(SCHEME, value);
        return true;
      }
      if (window.ui.authActions && typeof window.ui.authActions.authorize === 'function') {
        var payload = {};
        payload[SCHEME] = {value: value, schema: SCHEME};
        window.ui.authActions.authorize(payload);
        return true;
      }
    } catch (e) { /* ignore */ }
    return false;
  }

  function apply(value) {
    token = value;
    save(value);
    return authorize(value);
  }

  /* ---------------- 页面提示 ---------------- */

  function tip(text, isError) {
    try {
      var el = document.createElement('div');
      el.textContent = text;
      el.style.cssText = 'position:fixed;top:12px;right:12px;z-index:99999;padding:8px 14px;'
        + 'border-radius:6px;font:13px/1.4 sans-serif;color:#fff;box-shadow:0 2px 8px rgba(0,0,0,.2);'
        + 'background:' + (isError ? '#d4380d' : '#389e0d');
      document.body.appendChild(el);
      window.setTimeout(function () {
        if (el.parentNode) el.parentNode.removeChild(el);
      }, 4000);
    } catch (e) { /* ignore */ }
    if (window.console) window.console.log('[swagger auto-auth] ' + text);
  }

  /* ---------------- fetch 拦截 ---------------- */

  function withAuthHeader(init, value) {
    init = init || {};
    var headers = init.headers;
    var next;
    if (!headers) {
      next = {};
    } else if (typeof Headers !== 'undefined' && headers instanceof Headers) {
      if (headers.get('Authorization')) return init;
      next = new Headers(headers);
      next.set('Authorization', 'Bearer ' + value);
      init = Object.assign({}, init);
      init.headers = next;
      return init;
    } else if (typeof headers === 'object') {
      if (headers.Authorization || headers.authorization) return init;
      next = Object.assign({}, headers);
      next.Authorization = 'Bearer ' + value;
    } else {
      return init;
    }
    next.Authorization = next.Authorization || ('Bearer ' + value);
    init = Object.assign({}, init);
    init.headers = next;
    return init;
  }

  function urlOf(input) {
    if (typeof input === 'string') return input;
    return (input && input.url) ? String(input.url) : '';
  }

  function methodOf(input, init) {
    var m = (init && init.method) || (input && input.method) || 'GET';
    return String(m).toUpperCase();
  }

  var nativeFetch = window.fetch;

  window.fetch = function (input, init) {
    var url = urlOf(input);
    var isLoginApi = AUTH_API.test(url) && methodOf(input, init) === 'POST';

    // 兜底：若 UI 授权未生效（如手动清过 Authorize），直接给同源请求补上 token
    if (!isLoginApi && token && typeof input === 'string') {
      init = withAuthHeader(init, token);
    }

    var promise = nativeFetch.call(this, input, init);
    if (!isLoginApi) return promise;

    return promise.then(function (res) {
      if (res && res.ok) {
        try {
          res.clone().json().then(function (body) {
            var value = extract(body);
            if (value) {
              if (apply(value)) tip('已自动注入 JWT，后续请求自动携带（刷新后仍有效）');
              else tip('已保存 JWT，但未能自动授权，请点右上角 Authorize 粘贴', true);
            }
          })['catch'](function () { /* 非 JSON 响应忽略 */ });
        } catch (e) { /* ignore */ }
      } else if (res && res.status === 401) {
        clear();
        tip('token 已失效，请重新调用 /auth/login', true);
      }
      return res;
    });
  };

  /* ---------------- 兜底：从渲染出的响应里再找一次 JWT ---------------- */
  /* 若 swagger-client 持有的是原生 fetch 引用（上面的拦截失效），响应仍会渲染到页面上 */
  var JWT = /eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]+/;

  function scan(node) {
    if (!node) return;
    var text = (node.nodeType === 3 ? node.nodeValue : node.textContent) || '';
    if (text.length === 0 || text.length > 20000) return;
    var m = text.match(JWT);
    if (!m || m[0] === token) return;
    if (apply(m[0])) {
      tip('已自动注入 JWT，后续请求自动携带（刷新后仍有效）');
    }
  }

  if (typeof window.MutationObserver === 'function') {
    new window.MutationObserver(function (records) {
      for (var i = 0; i < records.length; i++) {
        var added = records[i].addedNodes;
        for (var j = 0; j < added.length; j++) {
          scan(added[j]);
        }
      }
    }).observe(document.documentElement, {childList: true, subtree: true});
  }

  /* ---------------- 页面加载后恢复 ---------------- */

  var saved = load();
  var waiter = window.setInterval(function () {
    if (!window.ui) return;
    window.clearInterval(waiter);
    if (saved && apply(saved)) tip('已恢复上次登录的 JWT');
  }, 200);
})();
