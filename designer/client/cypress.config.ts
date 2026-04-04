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
        video: true,
        experimentalMemoryManagement: true,
        experimentalRunAllSpecs: true,
        numTestsKeptInMemory: 0,
        // We've imported your old cypress plugins here.
        // You may want to clean this up later by importing these.
        setupNodeEvents(on, config) {
            on("before:browser:launch", (browser, launchOptions) => {
                if (browser.family === "chromium") {
                    launchOptions.args.push("--disable-dev-shm-usage");
                }
                const width = 1920;
                const height = 1200;
                if (browser.isHeadless) {
                    if (browser.name === "chrome") {
                        launchOptions.args.push(`--window-size=${width},${height}`);
                        launchOptions.args.push("--disable-gpu");
                        launchOptions.args.push("--disable-software-rasterizer");
                    }
                    if (browser.name === "electron") {
                        launchOptions.preferences.width = width;
                        launchOptions.preferences.height = height;
                        launchOptions.args.push("--disable-gpu");
                        launchOptions.args.push("--disable-software-rasterizer");
                    }
                    if (browser.name === "firefox") {
                        launchOptions.args.push(`--width=${width}`);
                        launchOptions.args.push(`--height=${height}`);
                    }
                }

                return launchOptions;
            });
            return require("./cypress/plugins/index.js")(on, config);
        },
        baseUrl: `http://localhost:${process.env.PORT || 3000}`,
        excludeSpecPattern: ["**/__snapshots__/*", "**/__image_snapshots__/*"],
    },
});
