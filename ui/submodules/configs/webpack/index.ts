import { Configuration } from "webpack";
import { mergeWithCustomize, unique } from "webpack-merge";
import devConfig from "./dev";
import prodConfig from "./prod";

const defaultConfig: Configuration = process.env.NODE_ENV === "production" ? prodConfig : devConfig;

export function withDefaultConfig(...configs: [(defaultCfg: Configuration) => Configuration[]] | Configuration[]): Configuration {
    const [first] = configs;
    return mergeWithCustomize({
        customizeArray: unique("plugins", ["CleanWebpackPlugin"], (plugin) => plugin.constructor && plugin.constructor.name),
    })(defaultConfig, ...(typeof first === "function" ? first(defaultConfig) : configs));
}
