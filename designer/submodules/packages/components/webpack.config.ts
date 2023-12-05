import { withDefaultConfig } from "../../configs/webpack";
import { withModuleFederationPlugins } from "../../configs/webpack/withModuleFederationPlugins";
import { dependencies } from "./package.json";

const nuCoreUrl = process.env.NODE_ENV === "production" ? process.env.NU_FE_CORE_URL : process.env.PROXY_PATH;

const configuration = withDefaultConfig(
    withModuleFederationPlugins({
        remotes: {
            nussknackerUi: `${process.env.NU_FE_CORE_SCOPE}@${nuCoreUrl}/remoteEntry.js`,
        },
        shared: {
            ...dependencies,
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
    }),
);

export default configuration;
