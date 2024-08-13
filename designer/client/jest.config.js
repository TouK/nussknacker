/* eslint-disable i18next/no-literal-string */

module.exports = {
    testEnvironment: "jsdom",
    clearMocks: true,
    collectCoverage: true,
    coverageDirectory: "jest-coverage",
    globals: {
        __DEV__: true,
        __webpack_public_path__: "",
    },
    moduleFileExtensions: ["js", "json", "jsx", "ts", "tsx", "svg"],
    moduleNameMapper: {
        "\\.(css|less|scss|sss)$": "<rootDir>/node_modules/jest-css-modules",
        "\\.(svg)$": "<rootDir>/__mocks__/svgComponentMock",
        uuid: require.resolve("uuid"),
        "@fontsource/roboto-mono": "<rootDir>/node_modules/jest-css-modules",
        "color-alpha": "<rootDir>/__mocks__/color-alpha.ts",
    },
    testMatch: ["**/*test.[tj]s?(x)"],
    snapshotSerializers: ["@emotion/jest/serializer"],
    setupFilesAfterEnv: ["<rootDir>/jest-setup.js"],
    transformIgnorePatterns: ["<rootDir>/node_modules/(?!react-cron-generator)/.*"],
};
