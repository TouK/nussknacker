import { alpha } from "@mui/material";
import { PaletteOptions } from "@mui/material/styles/createPalette";
import { EnvironmentTagColor } from "../EnvironmentTag";

const standardPalette: PaletteOptions = {
    primary: {
        main: `#D2A8FF`,
    },
    secondary: {
        light: "#D2A8FF",
        main: `#762976`,
    },
    error: {
        light: "#DE7E8A",
        main: `#D4354D`,
    },
    warning: {
        main: "#FF9A4D",
    },
    success: {
        main: `#80D880`,
        dark: `#206920`,
        contrastText: `#FFFFFF`,
    },
    background: {
        paper: "#242F3E",
        default: "#131A25",
    },
    text: {
        primary: "#ededed",
        secondary: "#cccccc",
    },
    action: {
        hover: alpha("#D2A8FF", 0.24),
        active: alpha("#D2A8FF", 0.4),
    },
};

export const darkModePalette: PaletteOptions = {
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
            Properties: {
                fill: "#46ca94",
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
            compareVersions: {
                backgroundColor: "#1ba1af",
                color: "white",
            },
            default: {
                backgroundColor: "#2D8E54",
                color: "white",
            },
        },
    },
};
