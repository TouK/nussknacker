/*
  We want to be able to serve NK under different (in particular not empty) paths (e.g. via nginx configuration) without rebuilding frontends
  The easiest way would be to use relative urls, but because we don't use hash router, but rely on different paths, it's more difficult
  Currently we replace __publicPath__ string in main.html during serving with configured path, and we override publicPath here, via global variable (window.nkPath
  and __webpack_public_path__).
  It's not really elegant, but we don't see better way without moving to hash router.
 */
const nkPath = __DEV__ ? "" : window.nkPath

//https://webpack.js.org/guides/public-path/#on-the-fly
__webpack_public_path__ = `${nkPath}/static/`

const API_URL = `${nkPath}/api`
const DATE_FORMAT = "YYYY-MM-DD HH:mm:ss"
const DISPLAY_DATE_FORMAT = "YYYY-MM-DD|HH:mm"
const BACKEND_STATIC_URL = __DEV__ ? `${nkPath}/be-static/` : `${nkPath}/static/`

export {
  API_URL,
  BACKEND_STATIC_URL,
  DATE_FORMAT as dateFormat,
  DISPLAY_DATE_FORMAT as displayDateFormat,
  nkPath,
}
