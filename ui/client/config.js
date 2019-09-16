let nkPath = window.nkPath
//https://webpack.js.org/guides/public-path/#on-the-fly
__webpack_public_path__ = `${nkPath}/static/`


let API_URL = `${nkPath}/api`
const dateFormat = "YYYY-MM-DD HH:mm:ss"

if (__DEV__) {
  API_URL = 'http://localhost:8081/api'
}

export {
  API_URL,
  dateFormat,
  nkPath
}