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
  moduleFileExtensions: [
    "js",
    "json",
    "jsx",
    "ts",
    "tsx",
    "svg",
  ],
  moduleNameMapper: {
    "\\.(css|less|scss|sss|styl)$": "<rootDir>/node_modules/jest-css-modules",
    "\\.(svg)$": "<rootDir>/__mocks__/svgComponentMock",
  },
  testMatch: [
    "**/*test.[tj]s?(x)",
  ],
}
