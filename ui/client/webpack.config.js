const path = require('path');
const webpack = require('webpack');
const childProcess = require('child_process');
const HtmlWebpackPlugin = require('html-webpack-plugin');

const NODE_ENV = process.env.NODE_ENV || 'development';
const GIT_HASH = childProcess.execSync('git log -1 --format=%H').toString();
const GIT_DATE = childProcess.execSync('git log -1 --format=%cd').toString();
const isProd = NODE_ENV === 'production';

module.exports = {
  mode: NODE_ENV,
  performance: {
    maxEntrypointSize: 3000000,
    maxAssetSize: 3000000
  },
  resolve: {
    alias: {
      'react-dom': '@hot-loader/react-dom'
    }
  },
  entry: isProd ? './index' : {
    vendors: [
      "webpack-dev-server/client?http://localhost:3000",
      "react-hot-loader/patch"
    ],
    main: './index'
  },
  output: {
    path: path.join(__dirname, '..', 'server', 'target', 'scala-2.11', 'classes', 'web', 'static'),
    filename: '[name].js',
    publicPath: '/static/'
  },
  devtool: isProd ? 'hidden-source-map' : 'eval-source-map',
  devServer: {
    contentBase: __dirname,
    historyApiFallback: {
      index: '/static/main.html'
    },
    hot: true,
    hotOnly: true,
    port: 3000,
  },
  plugins: [
    new HtmlWebpackPlugin({
      title: "Nussknacker",
      hash: true,
      filename: 'main.html',
      template: "index_template_no_doctype.ejs"
    }),
    isProd ? null : new webpack.NamedModulesPlugin(),
    isProd ? null : new webpack.HotModuleReplacementPlugin(),
    new webpack.DefinePlugin({
      '__DEV__': !isProd,
      'process.env': {
        'NODE_ENV': JSON.stringify(NODE_ENV)
      },
      'GIT': {
        'HASH': JSON.stringify(GIT_HASH),
        'HASH': JSON.stringify(GIT_HASH),
        'DATE': JSON.stringify(GIT_DATE)
      }
    }),
  ].filter(p => p !== null),
  module: {
    rules: [{
      test: /\.html$/,
      loader: "html-loader?minimize=false"
    }, {
      test: /\.js$/,
      loader: 'babel-loader',
      include: __dirname
    }, {
      test: /\.css?$/,
      loaders: ['style-loader', 'raw-loader'],
      include: __dirname
    }, {
      test: /\.styl$/,
      loaders: ['style-loader', 'css-loader', 'stylus-loader'],
      include: __dirname
    }, {
      test: /\.less$/,
      loaders: ['style-loader', 'css-loader', 'less-loader'],
      include: __dirname
    }, {
      test: /\.(eot|svg|png|ttf|woff|woff2)$/,
      loader: 'file-loader?name=assets/fonts/[name].[ext]',
      include: __dirname
    }]
  }
};
