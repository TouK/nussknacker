/* eslint-disable i18next/no-literal-string */
var path = require("path");
var WebpackConfig = require("./webpack.config")
var _ = require("lodash")

const puppeteer = require("puppeteer");
process.env.CHROME_BIN = puppeteer.executablePath();

WebpackConfig.externals = {
  "react/addons": true,
  "react/lib/ExecutionEnvironment": true,
  "react/lib/ReactContext": true
};

module.exports = function(config) {
  config.set({
    basePath: "",
    frameworks: ["jasmine", "es6-shim"],
    files: [
      "test/**/*.js"
    ],

    webpack: WebpackConfig,

    preprocessors: {
      // add webpack as preprocessor
      "*.js": ["webpack"],
      "test/**/*.js": ["webpack"]
    },

    webpackServer: {
      noInfo: true //please don't spam the console when running in karma!
    },

    reporters: ["spec"],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    browsers: ["ChromeHeadlessCI"],
    customLaunchers: {
      ChromeHeadlessCI: {
        base: "ChromeHeadless",
        flags: [
          //this is needed for our CI server
          "--no-sandbox"
       ]
      }
    },
    singleRun: false
  })
};
