import { defineConfig } from "cypress";

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
        experimentalRunAllSpecs: true,
        // We've imported your old cypress plugins here.
        // You may want to clean this up later by importing these.
        setupNodeEvents(on, config) {
            on("before:browser:launch", (browser, launchOptions) => {
                const width = 1400;
                const height = 1200;
                if (browser.isHeadless) {
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
            });
            return require("./cypress/plugins/index.js")(on, config);
        },
        baseUrl: "http://localhost:3000",
        excludeSpecPattern: ["**/__snapshots__/*", "**/__image_snapshots__/*"],
    },
});
