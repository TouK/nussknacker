import { withDefaultConfig } from "../../configs/webpack";
import { withModuleFederationPlugins } from "../../configs/webpack/withModuleFederationPlugins";
import { dependencies } from "./package.json";
import bootstrap from "bootstrap";
import webpack from "webpack";
import ver from "./version";
import MomentLocalesPlugin from "moment-locales-webpack-plugin";
import { svgRule } from "../../configs/webpack/common";
import { resolve } from "path";

const configuration = withDefaultConfig(
    withModuleFederationPlugins({
        remotes: {
            nussknackerUi: `${process.env.NU_FE_CORE_SCOPE}@${process.env.NU_FE_CORE_URL}/remoteEntry.js`,
        },
        shared: {
            ...dependencies,
            "@emotion/react": {
                singleton: true,
                requiredVersion: dependencies["@emotion/react"],
            },
            react: {
                eager: true,
                singleton: true,
                requiredVersion: dependencies["react"],
            },
            "react-dom": {
                eager: true,
                singleton: true,
                requiredVersion: dependencies["react-dom"],
            },
        },
    }),
);

configuration.module.rules = [
    ...configuration.module.rules.filter((r) => r !== svgRule),
    {
        test: /\.svg$/i,
        oneOf: [
            {
                issuer: /\.[tj]sx?$/,
                use: [
                    require.resolve("babel-loader"),
                    {
                        loader: require.resolve("@svgr/webpack"),
                        options: {
                            svgo: true,
                        },
                    },
                    {
                        loader: require.resolve("file-loader"),
                        options: {
                            name: "assets/images/[name][hash].[ext]",
                        },
                    },
                    require.resolve("svg-transform-loader"),
                    require.resolve("svgo-loader"),
                ],
            },
            {
                type: "asset/resource",
                use: [
                    {
                        loader: require.resolve("svgo-loader"),
                        options: {
                            externalConfig: resolve(__dirname, "../../.svgo.yml"),
                        },
                    },
                ],
            },
        ],
    },
    {
        test: /\.styl$/,
        exclude: /node_modules/,
        use: [
            require.resolve("style-loader"),
            {
                loader: require.resolve("legacy-css-loader"),
                options: {
                    modules: {
                        mode: "global",
                        exportGlobals: true,
                        localIdentName: "[name]--[local]--[hash:base64:5]",
                        exportLocalsConvention: "camelCase",
                    },
                },
            },
            {
                loader: require.resolve("postcss-loader"),
                options: {
                    postcssOptions: {
                        plugins: [require("autoprefixer"), require("postcss-move-props-to-bg-image-query")],
                    },
                },
            },
            {
                loader: require.resolve("stylus-loader"),
                options: {
                    stylusOptions: {
                        use: [bootstrap()],
                    },
                },
            },
        ],
    },
    {
        test: /\.(eot|ttf|woff|woff2)$/,
        use: [
            {
                loader: require.resolve("file-loader"),
                options: {
                    name: "assets/images/[name][hash].[ext]",
                },
            },
        ],
    },
];

const NODE_ENV = process.env.NODE_ENV || "development";
configuration.plugins.push(
    new MomentLocalesPlugin({
        localesToKeep: ["en"],
    }),
    new webpack.DefinePlugin({
        __DEV__: NODE_ENV !== "production",
        "process.env": {
            NODE_ENV: JSON.stringify(NODE_ENV),
        },
        __BUILD_VERSION__: JSON.stringify(ver.version),
        __BUILD_HASH__: JSON.stringify(ver.hash),
    }),
);

export default configuration;
