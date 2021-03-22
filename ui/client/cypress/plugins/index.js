/// <reference types="cypress" />
// ***********************************************************
// This example plugins/index.js can be used to load plugins
//
// You can change the location of this file or turn off loading
// the plugins file with the 'pluginsFile' configuration option.
//
// You can read more here:
// https://on.cypress.io/plugins-guide
// ***********************************************************

// This function is called when a project is opened or re-opened (e.g. due to
// the project's config changing)

/**
 * @type {Cypress.PluginConfig}
 */

const browserify = require("@cypress/browserify-preprocessor")

module.exports = (on, config) => {
  require("cypress-plugin-snapshots/plugin").initPlugin(on, config)
  require("@cypress/code-coverage/task")(on, config)

  const options = browserify.defaultOptions
  // transform[1][1] is "babelify"
  // so we just add our code instrumentation plugin to the list
  options.browserifyOptions.transform[1][1].plugins.push("babel-plugin-istanbul")

  on(
    "file:preprocessor",
    browserify({...options, typescript: require.resolve("typescript")}),
  )
  return config
}
