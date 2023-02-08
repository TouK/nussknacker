import { withDefaultConfig } from "../../configs/webpack";
import { withModuleFederationPlugins } from "../../configs/webpack/withModuleFederationPlugins";
import { dependencies } from "./package.json";
import bootstrap from "bootstrap";
import webpack from "webpack";
import ver from "./version";

import MomentLocalesPlugin from "moment-locales-webpack-plugin";

const cssPreLoaders = [
    {
        loader: "postcss-loader",
        options: {
            postcssOptions: {
                plugins: [require("autoprefixer"), require("postcss-move-props-to-bg-image-query")],
            },
        },
    },
];

const fileLoader = {
    loader: "file-loader",
    options: {
        name: "assets/images/[name][hash].[ext]",
    },
};

const proxy = {
    "/api": {
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
        pathRewrite: {
            "^/be-static": "/static",
        },
    },
    "/submodules/components": {
        target: "http://localhost:5001",
        changeOrigin: true,
        pathRewrite: {
            "^/submodules/components": "/",
        },
    },
    "/submodules": {
        target: process.env.BACKEND_DOMAIN,
        changeOrigin: true,
    },
    "/static": {
        target: "http://localhost:3013",
        changeOrigin: true,
        pathRewrite: {
            "^/static": "/",
        },
    },
};

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
            "@mui/private-theming/ThemeProvider": {
                singleton: true,
                requiredVersion: dependencies["@mui/private-theming/ThemeProvider"],
            },
            "@mui/private-theming/useTheme": {
                singleton: true,
                requiredVersion: dependencies["@mui/private-theming/useTheme"],
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

configuration.resolve.fallback = {
    crypto: require.resolve("crypto-browserify"), //reason: jsonwebtoken
    stream: require.resolve("stream-browserify"), //reason: jsonwebtoken
    buffer: require.resolve("buffer-browserify"), //reason: jsonwebtoken
    util: require.resolve("util"), //reason: jsonwebtoken
    events: false,
    fs: false,
};

configuration.module.rules = [
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
        test: /\.(css|styl|less)?$/,
        use: [
            "style-loader",
            {
                loader: "legacy-css-loader",
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
        test: /\.styl$/,
        enforce: "pre",
        exclude: /node_modules/,
        use: [
            ...cssPreLoaders,
            {
                loader: "stylus-loader",
                options: {
                    stylusOptions: {
                        use: [bootstrap()],
                    },
                },
            },
        ],
    },
    {
        test: /\.less$/,
        enforce: "pre",
        exclude: /node_modules/,
        use: [...cssPreLoaders, "less-loader"],
    },
    {
        test: /\.(eot|ttf|woff|woff2)$/,
        use: [fileLoader],
    },
    {
        test: /\.(png|jpg)$/,
        use: [fileLoader],
    },
    {
        test: /\.svg$/,
        enforce: "pre",
        exclude: /font/,
        use: ["svg-transform-loader", "svgo-loader"],
    },

    {
        test: /\.svg$/,
        oneOf: [
            {
                issuer: /\.[tj]sx?$/,
                use: [
                    "babel-loader",
                    {
                        loader: "@svgr/webpack",
                        options: {
                            svgo: true,
                        },
                    },
                    fileLoader,
                ],
            },
            {
                use: [fileLoader],
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
        "process.version": JSON.stringify(process.version), //reason: jsonwebtoken
        "process.browser": true, //reason: jsonwebtoken
        "process.env": {
            NODE_ENV: JSON.stringify(NODE_ENV),
        },
        __BUILD_VERSION__: JSON.stringify(ver.version),
        __BUILD_HASH__: JSON.stringify(ver.hash),
    }),
);

export default configuration;
