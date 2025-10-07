import ReactRefreshWebpackPlugin from "@pmmmwh/react-refresh-webpack-plugin";
import merge from "webpack-merge";

import { commonConfig, outputPath } from "./common";
import "webpack-dev-server";

export default merge(commonConfig, {
    mode: "development",
    devServer: {
        static: {
            directory: outputPath,
            watch: {
                ignored: [outputPath],
            },
        },
        headers: {
            "Access-Control-Allow-Origin": "*",
        },
        client: {
            overlay: false,
        },
        port: parseInt(process.env.PORT),
        hot: true,
        historyApiFallback: true,
        allowedHosts: "all",
        devMiddleware: {
            writeToDisk: true,
        },
        proxy: {
            [process.env.PROXY_PATH]: {
                target: process.env.NU_FE_CORE_URL,
                changeOrigin: true,
                pathRewrite: {
                    [`^${process.env.PROXY_PATH}/api`]: "/api",
                    [`^${process.env.PROXY_PATH}`]: "/static",
                },
                onProxyRes: (proxyRes, req) => {
                    if (req.headers?.origin) {
                        proxyRes.headers["Access-Control-Allow-Origin"] = req.headers.origin;
                    }
                },
            },
        },
    },
    devtool: "eval-source-map",
    plugins: [new ReactRefreshWebpackPlugin({ overlay: false, forceEnable: true, esModule: true })],
});
