import { withDefaultConfig } from "../../configs/webpack";
import { withModuleFederationPlugins } from "../../configs/webpack/withModuleFederationPlugins";
import { dependencies } from "./package.json";

const extended = withDefaultConfig(
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

export default withDefaultConfig(extended, {
    module: {
        rules: [
            {
                test: /translations\/.*\.json$/i,
                type: "asset/resource",
                generator: {
                    filename: "[path][name][ext]",
                },
            },
        ],
    },
});
