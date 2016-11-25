var path = require('path');
var WebpackConfig = require('./webpack.config')
var _ = require('lodash')

module.exports = function(config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine'],
    files: [
      'test/**/*.js'
    ],

    preprocessors: {
      // add webpack as preprocessor
      '*.js': ['webpack', 'sourcemap'],
      'test/**/*.js': ['webpack', 'sourcemap']
    },

    webpack: { //tutaj staramy sie w miare cywilizowany sposob uzyc istniejacej konfiguracji webpacka i dopchnac tylko niektore rzeczy na potrzeby testow
      devtool: WebpackConfig.devtool,
      module: {
        loaders: _.concat(WebpackConfig.module.loaders,
          { test: /\.json$/,
            loader: 'json'
          }
        )
      },
      resolve: {
        alias: WebpackConfig.resolve.alias,
        extensions: _.concat(WebpackConfig.resolve.extensions, '.json')
      },
      resolveLoader: WebpackConfig.resolveLoader,
      externals: {
        'react/addons': true,
        'react/lib/ExecutionEnvironment': true,
        'react/lib/ReactContext': true
      },
    },

    webpackServer: {
      noInfo: true //please don't spam the console when running in karma!
    },

    plugins: [
      'karma-webpack',
      'karma-jasmine',
      'karma-sourcemap-loader',
      'karma-phantomjs-launcher',
      'karma-verbose-reporter',
      'karma-spec-reporter'
    ],

    //fixme to jakby w ogole nie dziala?
    babelPreprocessor: {
      options: {
        presets: ["es2015-loose", "stage-0", "react"]
      }
    },


    reporters: ['spec'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    browsers: ['PhantomJS'],
    singleRun: false,
  })
};
