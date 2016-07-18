if (process.env.NODE_ENV === 'production') {
  module.exports = require('./TodoAppRoot.prod');
} else {
  module.exports = require('./TodoAppRoot.dev');
}
