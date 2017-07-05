let API_URL = '/api';

if (__DEV__) {
  API_URL = 'http://localhost:8081/api';
}

export {
  API_URL,
};
