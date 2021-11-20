import ReactRefreshWebpackPlugin from "@pmmmwh/react-refresh-webpack-plugin";
import { config } from "dotenv";
import path, { resolve } from "path";
import merge from "webpack-merge";
import { commonConfig } from "./common";

config({ path: resolve(__dirname, "../../.env") });

export default merge(commonConfig, {
    mode: "development",
    devServer: {
        static: {
            directory: path.resolve("dist"),
            watch: {
                ignored: [path.resolve("dist")],
            },
        },
        client: {
            logging: "error",
            overlay: false,
        },
        port: process.env.PORT ? parseInt(process.env.PORT) : 7890,
        hot: true,
        historyApiFallback: true,
        devMiddleware: {
            writeToDisk: true,
        },
    },
    devtool: false,
    plugins: [new ReactRefreshWebpackPlugin()],
});
