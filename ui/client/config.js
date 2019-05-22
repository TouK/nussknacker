let API_URL = '/api';
let dateFormat = "YYYY-MM-dd HH:mm:ss";

if (__DEV__) {
  API_URL = 'http://localhost:8081/api';
}

export {
  API_URL,
  dateFormat
};
