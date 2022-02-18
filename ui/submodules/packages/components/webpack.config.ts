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
            "@emotion/react": { singleton: true },
            "@mui/private-theming/ThemeProvider": { singleton: true },
            "@mui/private-theming/useTheme": { singleton: true },
            react: { eager: true, singleton: true },
            "react-dom": { eager: true, singleton: true },
        },
    }),
);

configuration.module.rules.push({
    test: /translations\/.*\.json$/i,
    type: "asset/resource",
    generator: {
        filename: "[path][name][ext]",
    },
});

export default configuration;
