var path = require('path');
var webpack = require('webpack');
var childProcess = require('child_process'),
GIT_HASH = childProcess.execSync('git log -1 --format=%H').toString();
GIT_DATE = childProcess.execSync('git log -1 --format=%cd').toString();

module.exports = {
  devtool: 'cheap-source-map',
  entry: [
    './index'
  ],
  output: {
    path: path.join(__dirname, 'dist'),
    filename: 'bundle.js',
    publicPath: '/static/'
  },
  plugins: [
    //new webpack.optimize.UglifyJsPlugin(), fixme to niestety nie dziala i psuje escapowanie ciapek w svg i przez to javascrypty zajmuja 4mb zamaist 1mb...
    new webpack.optimize.DedupePlugin(),
    new webpack.optimize.AggressiveMergingPlugin(),
    new webpack.DefinePlugin({
      'GIT': {
        'HASH': JSON.stringify(GIT_HASH),
        'DATE': JSON.stringify(GIT_DATE)
      }
    })
  ],
  resolve: {
    alias: {
      'react': path.join(__dirname, 'node_modules', 'react'),
      'appConfig': path.join(__dirname, 'config', process.env.NODE_ENV || 'development')
    },
    extensions: ['', '.js']
  },
  resolveLoader: {
    'fallback': path.join(__dirname, 'node_modules')
  },
  module: {
    loaders: [{
        test: /\.html$/,
        loader: "html"
    }, {
      test: /\.js$/,
      loaders: ['babel'],
      exclude: /node_modules/,
      include: __dirname
    }, {
      test: /\.css?$/,
      loaders: ['style', 'raw'],
      include: __dirname
    }, {
      test: /\.styl$/,
      loaders: ['style-loader', 'css-loader', 'stylus-loader'],
      include: __dirname
    }, {
      test: /\.less$/,
      loaders: ['style', 'css', 'less'],
      include: __dirname
    }, {
      test: /\.(eot|svg|png|ttf|woff|woff2)$/,
      loader: 'file?name=assets/fonts/[name].[ext]',
      include: __dirname
    }]
  }
};
