import { CleanWebpackPlugin } from "clean-webpack-plugin";
import merge from "webpack-merge";
import { commonConfig } from "./common";

export default merge(commonConfig, {
    mode: "production",
    plugins: [new CleanWebpackPlugin()],
    devtool: "source-map",
    output: {
        filename: "[name].[contenthash].js",
    },
});
