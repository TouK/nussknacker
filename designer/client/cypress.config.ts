import { defineConfig } from "cypress";

function setupWindowSize(browser: Cypress.Browser, launchOptions: Cypress.BeforeBrowserLaunchOptions) {
    if (browser.isHeadless) {
        const width = 1400;
        const height = 1200;
        if (browser.name === "chrome") {
            launchOptions.args.push(`--window-size=${width},${height}`);
        }
        if (browser.name === "electron") {
            launchOptions.preferences.width = width;
            launchOptions.preferences.height = height;
        }
        if (browser.name === "firefox") {
            launchOptions.args.push(`--width=${width}`);
            launchOptions.args.push(`--height=${height}`);
        }
    } else {
        if (browser.name === "electron") {
            launchOptions.preferences.fullscreen = true;
        } else if (browser.family === "chromium") {
            launchOptions.args.push("--start-fullscreen");
        }
    }
    return launchOptions;
}

export default defineConfig({
    env: {
        updateSnapshotsOnFail: false,
        pluginVisualRegressionMaxDiffThreshold: 0.007,
        pluginVisualRegressionUpdateImages: false,
        pluginVisualRegressionForceDeviceScaleFactor: false,
    },
    reporter: "junit",
    reporterOptions: {
        mochaFile: "cypress-test-results/[hash].xml",
        toConsole: false,
    },
    defaultCommandTimeout: 30000,
    e2e: {
        experimentalMemoryManagement: true,
        experimentalRunAllSpecs: true,
        numTestsKeptInMemory: 10,
        // We've imported your old cypress plugins here.
        // You may want to clean this up later by importing these.
        setupNodeEvents: (on, config) => {
            on("before:browser:launch", (browser, launchOptions) => {
                setupWindowSize(browser, launchOptions);
            });
            on("before:browser:launch", (browser) => {
                config.video = browser.isHeadless;
            });
            return require("./cypress/plugins/index.js")(on, config);
        },
        baseUrl: `http://localhost:${process.env.PORT || 3000}`,
        excludeSpecPattern: ["**/__snapshots__/*", "**/__image_snapshots__/*"],
    },
});
