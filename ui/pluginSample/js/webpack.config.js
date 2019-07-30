const path = require('path');
const webpack = require('webpack');


const NODE_ENV = process.env.NODE_ENV || 'development';
const isProd = NODE_ENV === 'production';

const entry = {
  main: path.resolve(__dirname,'./index.js'),
}

module.exports = {
  mode: 'production',
  externals: {
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
    filename: 'literalExpressions.js',
    library: 'literalExpressions'
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
      }
    ]
  }
}
