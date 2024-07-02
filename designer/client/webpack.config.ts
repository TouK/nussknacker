/* eslint-disable i18next/no-literal-string */
import progressBar from "./progressBar.js";
import path from "path";
import webpack, { Configuration } from "webpack";
import HtmlWebpackPlugin from "html-webpack-plugin";
import HtmlWebpackHarddiskPlugin from "html-webpack-harddisk-plugin";
import ForkTsCheckerWebpackPlugin from "fork-ts-checker-webpack-plugin";
import MomentLocalesPlugin from "moment-locales-webpack-plugin";
import ReactRefreshWebpackPlugin from "@pmmmwh/react-refresh-webpack-plugin";
import PreloadWebpackPlugin from "@vue/preload-webpack-plugin";
import CopyPlugin from "copy-webpack-plugin";
import autoprefixer from "autoprefixer";
import postcss_move_props_to_bg_image_query from "postcss-move-props-to-bg-image-query";
import { withModuleFederationPlugins } from "./configs/withModuleFederationPlugins";
import { hash, version } from "./version";
import "webpack-dev-server";
import { dependencies } from "./package.json";

const isProd = process.env.NODE_ENV === "production";
const entry = {
    main: path.resolve(__dirname, "./src/init.js"),
};
const BundleAnalyzerPlugin = require("webpack-bundle-analyzer").BundleAnalyzerPlugin;

const cssPreLoaders = [
    {
        loader: "postcss-loader",
        options: {
            postcssOptions: {
                plugins: [autoprefixer, postcss_move_props_to_bg_image_query],
            },
        },
    },
];

const outputPath = path.join(process.cwd(), "dist");

const mode = isProd ? "production" : "development";
const isBundleReport = process.env.NODE_ENV === "bundleReport";
const config: Configuration = {
    mode: mode,
    performance: {
        maxEntrypointSize: 3000000,
        maxAssetSize: 3000000,
    },
    resolve: {
        extensions: [".ts", ".tsx", ".js", ".jsx", ".json"],
        fallback: {
            path: require.resolve("path-browserify"), //reason: react-markdown
            crypto: require.resolve("crypto-browserify"), //reason: jsonwebtoken
            stream: require.resolve("stream-browserify"), //reason: jsonwebtoken
            fs: false,
            "process/browser": require.resolve("process/browser"),
        },
    },
    entry: entry,
    output: {
        path: outputPath,
        filename: isProd ? "[contenthash].js" : "[name].js",
        assetModuleFilename: "assets/images/[name][hash][ext]",
        clean: true,
    },
    devtool: isProd ? "source-map" : "eval-source-map",
    watchOptions: {
        ignored: /^(?!.*\/src\/).*$/,
    },
    devServer: {
        client: {
            overlay: false,
        },
        historyApiFallback: {
            index: "/main.html",
            disableDotRule: true,
        },
        hot: true,
        host: "::",
        allowedHosts: "all",
        headers: {
            "Access-Control-Allow-Credentials": "true",
            "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
            "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization",
        },
        port: 3000,
        proxy: {
            "/api": {
                target: process.env.BACKEND_DOMAIN,
                changeOrigin: true,
                onProxyRes: (proxyRes, req) => {
                    if (req.headers?.origin) {
                        proxyRes.headers["Access-Control-Allow-Origin"] = req.headers.origin;
                    }
                },
            },
            "/grafana": {
                target: process.env.BACKEND_DOMAIN,
                changeOrigin: true,
                onProxyRes: (proxyRes, req) => {
                    if (req.headers?.origin) {
                        proxyRes.headers["Access-Control-Allow-Origin"] = req.headers.origin;
                    }
                },
            },
            "/be-static": {
                target: process.env.BACKEND_DOMAIN,
                changeOrigin: true,
                onProxyRes: (proxyRes, req) => {
                    if (req.headers?.origin) {
                        proxyRes.headers["Access-Control-Allow-Origin"] = req.headers.origin;
                    }
                },
                pathRewrite: {
                    "^/be-static": "/static",
                },
            },
            "/submodules/components": {
                target: "http://localhost:5001",
                changeOrigin: true,
                pathRewrite: {
                    "^/submodules/components": "",
                },
                onError: (err, req, res) => {
                    const url = `${process.env.BACKEND_DOMAIN}/submodules/components${req.path}`;
                    console.warn(`Submodules not available locally - falling back to ${url}`);
                    res.redirect(url);
                },
            },
            "/static": {
                target: "http://localhost:3000",
                changeOrigin: true,
                pathRewrite: {
                    "^/static": "/",
                },
            },
        },
        static: {
            directory: outputPath,
            watch: {
                ignored: ["**/*.tgz", "**/*.txt", "**/*.json", "**/*.js.map"],
            },
        },
    },
    plugins: [
        new MomentLocalesPlugin({
            localesToKeep: ["en"],
        }),
        new HtmlWebpackPlugin({
            title: "Nussknacker",
            chunks: ["runtime", "main"],
            //see ./config.ts
            base: isProd ? "__publicPath__/static/" : "/",
            filename: "main.html",
            favicon: "src/assets/img/nussknacker-logo-icon.svg",
            meta: {
                viewport: "user-scalable = no",
            },
        }),
        new HtmlWebpackHarddiskPlugin(),
        new CopyPlugin({
            patterns: [
                { from: "translations", to: "assets/locales", noErrorOnMissing: true },
                { from: "assets/img/icons/license", to: "license", noErrorOnMissing: true },
            ],
        }),
        new PreloadWebpackPlugin({
            rel: "preload",
            as: "font",
            include: "allAssets",
            fileWhitelist: [/\.(woff2?|eot|ttf|otf)(\?.*)?$/i],
        }),
        new webpack.ProvidePlugin({
            process: "process/browser",
        }),
        new webpack.DefinePlugin({
            __DEV__: !isProd,
            "process.version": JSON.stringify(process.version), //reason: jsonwebtoken
            "process.browser": true, //reason: jsonwebtoken
            "process.env": {
                NODE_ENV: JSON.stringify(mode),
            },
            __BUILD_VERSION__: JSON.stringify(version),
            __BUILD_HASH__: JSON.stringify(hash),
        }),
        new ForkTsCheckerWebpackPlugin({
            typescript: {
                memoryLimit: 5000,
            },
        }),
        isProd ? null : new ReactRefreshWebpackPlugin({ overlay: false }),
        new webpack.ProgressPlugin(progressBar),
        isBundleReport ? new BundleAnalyzerPlugin() : null,
    ].filter(Boolean),
    module: {
        rules: [
            {
                test: /\.html$/,
                use: {
                    loader: "html-loader",
                    options: {
                        minimize: false,
                    },
                },
            },
            {
                test: /\.[tj]sx?$/,
                exclude: /node_modules/,
                use: ["babel-loader"],
            },
            {
                test: /\.(css|less)?$/,
                sideEffects: true,
                use: [
                    "style-loader",
                    {
                        loader: "css-loader",
                        options: {
                            modules: {
                                mode: "global",
                                exportGlobals: true,
                                localIdentName: "[name]--[local]--[hash:base64:5]",
                                exportLocalsConvention: "camelCase",
                            },
                        },
                    },
                ],
            },
            {
                test: /\.css?$/,
                enforce: "pre",
                exclude: /node_modules/,
                use: cssPreLoaders,
            },
            {
                test: /\.less$/,
                enforce: "pre",
                exclude: /node_modules/,
                use: [...cssPreLoaders, "less-loader"],
            },
            {
                test: /\.(eot|ttf|woff|woff2)$/,
                type: "asset/resource",
            },
            {
                test: /\.(png|jpg)$/,
                type: "asset/resource",
            },
            {
                test: /\.svg$/i,
                issuer: /\.[tj]sx?$/,
                use: [
                    "babel-loader",
                    {
                        loader: "@svgr/webpack",
                        options: {
                            babel: false,
                        },
                    },
                    "svgo-loader",
                ],
            },
        ],
    },
};

module.exports = withModuleFederationPlugins(config, {
    shared: {
        ...dependencies,
        "@touk/window-manager": {
            singleton: true,
            requiredVersion: dependencies["@touk/window-manager"],
        },
        "@emotion/react": {
            singleton: true,
            requiredVersion: dependencies["@emotion/react"],
        },
        "@mui/material": {
            singleton: true,
            requiredVersion: dependencies["@mui/material"],
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
});
