import { alpha } from "@mui/material";
import { PaletteOptions } from "@mui/material/styles/createPalette";
import { EnvironmentTagColor } from "../EnvironmentTag";

const standardPalette: PaletteOptions = {
    primary: {
        main: "#8256B5",
    },
    secondary: {
        light: "#bf570c",
        main: "#BF360C",
    },
    error: {
        light: "#DE7E8A",
        main: "#B71C1C",
    },
    warning: {
        main: "#FF6F00",
    },
    success: {
        main: "#388E3C",
        dark: `#206920`,
        contrastText: `#FFFFFF`,
    },
    background: {
        paper: "#c6c7d1",
        default: "#9394A5",
    },
    text: {
        primary: "#212121",
        secondary: "#030303",
    },
    action: {
        hover: alpha("#8256B5", 0.08),
        active: alpha("#8256B5", 0.12),
    },
};

// It's for a testing purpose only, to check if all color relations are added. We don't support light mode yet
export const lightModePalette: PaletteOptions = {
    ...standardPalette,
    custom: {
        environmentAlert: {
            [EnvironmentTagColor.green]: "#80D880",
            [EnvironmentTagColor.yellow]: "#FDE3A0",
            [EnvironmentTagColor.red]: "#DF818C",
            [EnvironmentTagColor.blue]: "#43A1E6",
        },
        nodes: {
            Source: {
                fill: "#509D6E",
            },
            FragmentInputDefinition: {
                fill: "#509D6E",
            },
            Sink: {
                fill: "#DB4646",
            },
            FragmentOutputDefinition: {
                fill: "#DB4646",
            },
            Filter: {
                fill: "#FF9444",
            },
            Switch: {
                fill: "#1B78BC",
            },
            VariableBuilder: {
                fill: "#E16AC0",
            },
            Variable: {
                fill: "#E16AC0",
            },
            Enricher: {
                fill: "#A171E6",
            },
            FragmentInput: {
                fill: "#A171E6",
            },
            Split: {
                fill: "#CC9C00",
            },
            Processor: {
                fill: "#4583dd",
            },
            Aggregate: {
                fill: "#e892bd",
            },
            CustomNode: {
                fill: "#19A49D",
            },
            Join: {
                fill: "#19A49D",
            },
            _group: {
                fill: "#19A49D",
            },
        },
        windows: {
            compareVersions: { backgroundColor: "#1ba1af", color: "white" },
            customAction: { backgroundColor: "white", color: "black" },
            editProperties: {
                backgroundColor: "#46ca94",
                color: "white",
            },
            default: { backgroundColor: "#2D8E54", color: "white" },
        },
    },
};
