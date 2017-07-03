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
    path: path.join(__dirname, 'dist', 'web', 'static'),
    filename: 'bundle.js',
    publicPath: '/static/'
  },
  plugins: [
    new webpack.optimize.UglifyJsPlugin({
      compress:{
        warnings: true
      }
    }),
    new webpack.optimize.DedupePlugin(),
    new webpack.optimize.AggressiveMergingPlugin(),
    new webpack.DefinePlugin({
      //To sie wydaje nadmiarowe, bo w packages.json juz wlasciwie ustawiamy taka sama zmienna, tu jest jakas proba wyjasnienia tego http://stackoverflow.com/questions/37311972/react-doesnt-switch-to-production-mode
      'process.env': {
        'NODE_ENV': JSON.stringify('production')
      },
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
        loader: "html-loader?minimize=false"
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
