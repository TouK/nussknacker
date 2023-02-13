import { withDefaultConfig } from "../../configs/webpack";
import { withModuleFederationPlugins } from "../../configs/webpack/withModuleFederationPlugins";
import { dependencies } from "./package.json";

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

export default configuration;
