import { withDefaultConfig } from "../../configs/webpack";
import { withModuleFederationPlugins } from "../../configs/webpack/withModuleFederationPlugins";
import { dependencies } from "./package.json";

export default withDefaultConfig(
    withModuleFederationPlugins({
        remotes: {
            nussknackerUi: `${process.env.NK_CORE_SCOPE}@${process.env.NK_CORE_URL}/remoteEntry.js`,
        },
        shared: {
            ...dependencies,
            react: {
                eager: true,
                singleton: true,
            },
            "react-dom": {
                eager: true,
                singleton: true,
            },
        },
    }),
);
