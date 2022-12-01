import {defineConfig} from "cypress"

export default defineConfig({
  env: {
    pluginVisualRegressionUpdateImages: false,
    pluginVisualRegressionForceDeviceScaleFactor: false,
  },
  reporter: "junit",
  reporterOptions: {
    mochaFile: "cypress-test-results/[hash].xml",
    toConsole: false,
  },
  defaultCommandTimeout: 10000,
  e2e: {
    // We've imported your old cypress plugins here.
    // You may want to clean this up later by importing these.
    setupNodeEvents(on, config) {
      on("before:browser:launch", (browser, launchOptions) => {
        if (browser.isHeadless) {
          if (browser.name === "chrome") {
            launchOptions.args.push("--window-size=1400,1200")
          }
          if (browser.name === "electron") {
            launchOptions.preferences.width = 1400
            launchOptions.preferences.height = 1200
          }
          if (browser.name === "firefox") {
            launchOptions.args.push("--width=1400")
            launchOptions.args.push("--height=1200")
          }
        } else {
          if (browser.name === "electron") {
            launchOptions.preferences.fullscreen = true
          } else if (browser.family === "chromium") {
            launchOptions.args.push("--start-fullscreen")
          }
        }

        return launchOptions
      })
      return require("./cypress/plugins/index.js")(on, config)
    },
    baseUrl: "http://localhost:3000",
    excludeSpecPattern: ["**/__snapshots__/*", "**/__image_snapshots__/*"],

  },
})
