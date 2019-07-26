const path = require('path');
const webpack = require('webpack');
const childProcess = require('child_process');


const NODE_ENV = process.env.NODE_ENV || 'development';
const isProd = NODE_ENV === 'production';

const entry = {
  main: path.resolve(__dirname,'./index.js'),
}

module.exports = {
  mode: 'production',
  externals: {
    'styled-components': 'styled',
    react: 'React',
    'react-dom': 'ReactDOM',
    'PluginManager': 'PluginManager'
  },
  resolve: {
    alias: {
      'react-dom': '@hot-loader/react-dom'
    }
  },
  optimization: {
    minimize: false
  },
  entry: entry,
  output: {
    path: path.join(__dirname, '..', 'target', 'scala-2.11', 'classes'),
    filename: 'queryBuilder.js',
    library: 'queryBuilder'
  },
  devtool: isProd ? 'hidden-source-map' : 'eval-source-map',
  plugins: [
    new webpack.DefinePlugin({
      'process.env': {
        'NODE_ENV': JSON.stringify(NODE_ENV)
      }
    }),
  ].filter(p => p !== null),
  module: {
    rules: [
      {
        test: /\.js$/,
        use: ["babel-loader"],
        exclude: /node_modules/,
        include: __dirname
      },
      {
        test: /\.css?$/,
        loaders: ['style-loader', 'raw-loader'],
        include: __dirname
      },
      {
        test: /\.styl$/,
        loaders: ['style-loader', 'css-loader', 'stylus-loader'],
        include: __dirname
      },
      {
        test: /\.scss$/,
        loaders: ["style-loader", "css-loader", "sass-loader"],
        include: __dirname
      },
      {
        test: /\.(eot|svg|png|ttf|woff|woff2)$/,
        loader: 'file-loader?name=assets/fonts/[name].[ext]',
        include: __dirname
      }
    ]
  }
}
