import ReactRefreshWebpackPlugin from "@pmmmwh/react-refresh-webpack-plugin";
import { config } from "dotenv";
import { resolve } from "path";
import merge from "webpack-merge";
import { commonConfig, outputPath } from "./common";

config({ path: resolve(__dirname, "../../.env") });

export default merge(commonConfig, {
    mode: "development",
    devServer: {
        static: {
            directory: outputPath,
            watch: {
                ignored: [outputPath],
            },
        },
        client: {
            logging: "error",
            overlay: false,
        },
        port: process.env.PORT ? parseInt(process.env.PORT) : 7890,
        hot: true,
        historyApiFallback: true,
        allowedHosts: "all",
        devMiddleware: {
            writeToDisk: true,
        },
    },
    devtool: false,
    plugins: [new ReactRefreshWebpackPlugin()],
});
