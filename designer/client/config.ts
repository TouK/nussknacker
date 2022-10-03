/*
  We want to be able to serve NK under different (in particular not empty) paths (e.g. via nginx configuration) without rebuilding frontends
  The easiest way would be to use relative urls, but because we don't use hash router, but rely on different paths, it's more difficult
  Currently we replace __publicPath__ string in main.html during serving with configured path, and we use "auto" publicPath from webpack
  via global variable (__webpack_public_path__).
  __webpack_public_path__ is also used when this module is imported in external app (WebpackModuleFederationPlugin) because it contains
  full url.
  It's not really elegant, but we don't see better way without moving to hash router.
 */

const publicUrl: URL | null = __webpack_public_path__ ? new URL(__webpack_public_path__?.replace(/static\/$/, "")) : null
const href = publicUrl?.href.replace(/\/$/, "") || ""

export const BASE_ORIGIN = publicUrl?.origin
export const BASE_PATH = publicUrl?.pathname || "/"
export const API_URL = `${href}/api`
export const BACKEND_STATIC_URL = __DEV__ ? `${href}/be-static/` : __webpack_public_path__

export const DATE_FORMAT = "YYYY-MM-DD HH:mm:ss"
export const DISPLAY_DATE_FORMAT = "YYYY-MM-DD|HH:mm"
