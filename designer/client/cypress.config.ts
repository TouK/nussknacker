import {defineConfig} from "cypress"

export default defineConfig({
  env: {
    "cypress-plugin-snapshots": {
      imageConfig: {
        resizeDevicePixelRatio: false,
        threshold: 0.004,
      },
      screenshotConfig: {
        scale: false,
      },
      backgroundBlend: "overlay",
      updateSnapshots: false,
    },
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
      return require("./cypress/plugins/index.js")(on, config)
    },
    baseUrl: "http://localhost:3000",
    excludeSpecPattern: ["**/__snapshots__/*", "**/__image_snapshots__/*"],
  },
})
