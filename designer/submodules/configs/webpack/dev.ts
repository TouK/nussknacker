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
            logging: "error",
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
                pathRewrite: {
                    [`^${process.env.PROXY_PATH}/api`]: "/api",
                    [`^${process.env.PROXY_PATH}`]: "/static",
                },
                onProxyReq: (proxyReq, req, res) => {
                    if (req.headers?.authorization?.match(/^basic/i)) {
                        if (req.headers?.origin) {
                            // redirect instead of rewrite for one time basic auth
                            res.setHeader("Access-Control-Allow-Origin", req.headers.origin);
                            res.setHeader("Access-Control-Allow-Credentials", "true");
                            res.setHeader("Access-Control-Allow-Headers", "X-Requested-With, content-type, Authorization");
                            res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                        }
                        res.redirect(new URL(req.url, process.env.NU_FE_CORE_URL).href);
                    }
                },
            },
        },
    },
    devtool: "eval-source-map",
    plugins: [new ReactRefreshWebpackPlugin({ overlay: false })],
});
