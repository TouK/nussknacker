import CopyPlugin from "copy-webpack-plugin";
import ForkTsCheckerWebpackPlugin from "fork-ts-checker-webpack-plugin";
import HtmlWebpackPlugin from "html-webpack-plugin";
import * as path from "path";
import { resolve } from "path";
import { Configuration, DefinePlugin } from "webpack";
import pkg from "../../package.json";

// eslint-disable-next-line @typescript-eslint/no-var-requires
const { contextPath, name, version } = require(path.join(process.cwd(), "package.json"));
export const outputPath = contextPath ? path.join(__dirname, `../../dist/${contextPath}`) : path.join(process.cwd(), "dist");

export const commonConfig: Configuration = {
    resolve: {
        extensions: [".tsx", ".ts", ".jsx", ".js"],
        cacheWithContext: false,
    },
    target: "web",
    output: {
        path: outputPath,
    },
    entry: "./src/index.js",
    module: {
        rules: [
            {
                test: /\.[jt]sx?$/,
                exclude: /node_modules/,
                loader: require.resolve("babel-loader"),
            },
            {
                test: /\.css$/,
                use: [{ loader: require.resolve("style-loader") }, { loader: require.resolve("css-loader") }],
            },
            {
                test: /\.svg$/i,
                exclude: /node_modules/,
                enforce: "pre",
                use: [
                    {
                        loader: require.resolve("svgo-loader"),
                        options: {
                            externalConfig: resolve(__dirname, "../../.svgo.yml"),
                        },
                    },
                ],
            },
            {
                test: /\.svg$/i,
                exclude: /node_modules/,
                oneOf: [
                    {
                        issuer: /\.[tj]sx?$/,
                        type: "asset/resource",
                        use: [
                            {
                                loader: require.resolve("babel-loader"),
                            },
                            {
                                loader: require.resolve("@svgr/webpack"),
                                options: {
                                    babel: false,
                                },
                            },
                        ],
                    },
                    {
                        type: "asset/resource",
                    },
                ],
            },
            {
                test: /\.(jpe?g|png|gif)$/i,
                exclude: /node_modules/,
                type: "asset/resource",
                use: [
                    {
                        loader: require.resolve("image-webpack-loader"),
                        options: {
                            optipng: { optimizationLevel: 7 },
                            gifsicle: { interlaced: false },
                        },
                    },
                ],
            },
        ],
    },
    plugins: [
        new HtmlWebpackPlugin({
            title: `${pkg.name} ${pkg.version}`,
            chunks: ["runtime", "main"],
            publicPath: "/",
        }),
        new ForkTsCheckerWebpackPlugin({ async: true }),
        new CopyPlugin({
            patterns: [
                { from: "assets", to: "assets", noErrorOnMissing: true },
                { from: "translations", to: "translations", noErrorOnMissing: true },
            ],
        }),
        new DefinePlugin({
            PACKAGE_NAME: JSON.stringify(name),
            PACKAGE_VERSION: JSON.stringify(version),
        }),
    ],
    optimization: {
        runtimeChunk: false,
    },
};
