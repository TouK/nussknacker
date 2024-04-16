import { alpha, createTheme, PaletteMode } from "@mui/material";
import { fontFamily, globalStyles } from "./styles";
import { blendDarken, blendLighten } from "./helpers";
import { deepmerge } from "@mui/utils";

declare module "@mui/material/FormHelperText" {
    interface FormHelperTextPropsVariantOverrides {
        largeMessage: true;
    }
    interface FormHelperTextOwnProps {
        "data-testid"?: string;
    }
}

declare module "@mui/material/styles" {
    interface PaletteOptions {
        custom?: (typeof darkModePalette)["custom"];
    }

    interface Palette {
        custom?: (typeof darkModePalette)["custom"];
    }

    interface Theme {
        custom: typeof custom;
    }

    interface ThemeOptions {
        custom?: typeof custom;
    }
}

const custom = {
    ConnectionErrorModal: {
        zIndex: 1600,
    },
    spacing: {
        controlHeight: 36,
        baseUnit: 4,
    },
    fontSize: 14,
};

const lightModePalette = {
    primary: {
        main: "#8256B5",
    },
    secondary: {
        main: "#BF360C",
    },
    error: {
        main: "#B71C1C",
    },
    warning: {
        main: "#FF6F00",
    },
    success: {
        main: "#388E3C",
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
    custom: {
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
                fill: "#FAA05A",
            },
            Switch: {
                fill: "#1B78BC",
            },
            VariableBuilder: {
                fill: "#FEB58A",
            },
            Variable: {
                fill: "#FEB58A",
            },
            Enricher: {
                fill: "#A171E6",
            },
            FragmentInput: {
                fill: "#A171E6",
            },
            Split: {
                fill: "#F9C542",
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
                fill: "#1EC6BE",
            },
            Join: {
                fill: "#1EC6BE",
            },
            _group: {
                fill: "#1EC6BE",
            },
        },
        windows: {
            compareVersions: { backgroundColor: "#1ba1af", color: "white" },
            customAction: { backgroundColor: "white", color: "black" },
            default: { backgroundColor: "#2D8E54", color: "white" },
        },
    },
};

const darkModePalette = {
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
    custom: {
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
                fill: "#FAA05A",
            },
            Switch: {
                fill: "#1B78BC",
            },
            VariableBuilder: {
                fill: "#FEB58A",
            },
            Variable: {
                fill: "#FEB58A",
            },
            Enricher: {
                fill: "#A171E6",
            },
            FragmentInput: {
                fill: "#A171E6",
            },
            Split: {
                fill: "#F9C542",
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
                fill: "#1EC6BE",
            },
            Join: {
                fill: "#1EC6BE",
            },
            _group: {
                fill: "#1EC6BE",
            },
        },
        windows: {
            compareVersions: { backgroundColor: "#1ba1af", color: "white" },
            customAction: { backgroundColor: "white", color: "black" },
            default: { backgroundColor: "#2D8E54", color: "white" },
        },
    },
};

export const getDesignTokens = (mode: PaletteMode) => ({
    palette: {
        mode,
        ...(mode === "light" ? lightModePalette : darkModePalette),
    },
});

const headerCommonStyles = {
    fontWeight: 500,
    lineHeight: 1.1,
    marginTop: "20px",
    marginBottom: "10px",
};

export const nuTheme = (mode: PaletteMode) =>
    createTheme(
        deepmerge(getDesignTokens(mode), {
            typography: (palette) => ({
                fontFamily,
                h1: { ...headerCommonStyles },
                h2: { ...headerCommonStyles },
                h3: { ...headerCommonStyles },
                h4: { ...headerCommonStyles },
                h5: { ...headerCommonStyles },
                h6: { ...headerCommonStyles },
                subtitle1: {
                    fontWeight: "bold",
                },
                subtitle2: {
                    fontWeight: "bold",
                },
                overline: {
                    fontSize: ".6875rem",
                    letterSpacing: "inherit",
                    lineHeight: "inherit",
                    textTransform: "inherit",
                    color: palette.text.secondary,
                },
            }),
            components: {
                MuiSwitch: {
                    styleOverrides: {
                        input: {
                            margin: 0,
                        },
                    },
                },
                MuiAlert: {
                    styleOverrides: {
                        root: ({ theme }) => ({
                            width: 300,
                            zIndex: 20000,
                            marginTop: 10,
                            cursor: "pointer",
                            maxHeight: 400,
                            ".MuiAlert-icon": { color: alpha(theme.palette.common.black, 0.54), alignSelf: "center" },
                        }),
                        standardSuccess: ({ theme }) => ({
                            backgroundColor: theme.palette.success.main,
                            color: blendDarken(theme.palette.text.primary, 0.9),
                        }),
                        standardError: ({ theme }) => ({
                            backgroundColor: blendLighten(theme.palette.error.main, 0.15),
                            color: blendDarken(theme.palette.text.primary, 0.9),
                        }),
                        standardWarning: ({ theme }) => ({
                            backgroundColor: theme.palette.warning.main,
                        }),
                        standardInfo: ({ theme }) => ({
                            backgroundColor: theme.palette.primary.main,
                            color: blendDarken(theme.palette.text.primary, 0.9),
                        }),
                    },
                },
                MuiCssBaseline: {
                    styleOverrides: (theme) => globalStyles(theme),
                },
                MuiFormControl: {
                    styleOverrides: {
                        root: {
                            display: "flex",
                            flexDirection: "row",
                            margin: "16px 0",
                        },
                    },
                },
                MuiFormLabel: {
                    styleOverrides: {
                        root: ({ theme }) => ({
                            ...theme.typography.body2,
                            display: "flex",
                            marginTop: "9px",
                            flexBasis: "20%",
                            maxWidth: "20em",
                            overflowWrap: "anywhere",
                        }),
                    },
                    defaultProps: {
                        focused: false,
                    },
                },
                MuiFormHelperText: {
                    styleOverrides: {
                        root: ({ theme }) => ({
                            marginLeft: 0,
                            color: theme.palette.success.main,
                            "&.Mui-error": {
                                color: theme.palette.error.light,
                            },
                        }),
                    },
                    variants: [{ props: { variant: "largeMessage" }, style: { fontSize: ".875rem" } }],
                    defaultProps: {
                        "data-testid": "form-helper-text",
                    },
                },
                MuiAutocomplete: {
                    styleOverrides: {
                        noOptions: ({ theme }) => ({
                            ...theme.typography.body2,
                            padding: theme.spacing(0.75, 2),
                            marginTop: theme.spacing(0.5),
                            backgroundColor: theme.palette.background.paper,
                        }),
                        loading: ({ theme }) => ({
                            ...theme.typography.body2,
                            padding: theme.spacing(0.75, 2),
                            marginTop: theme.spacing(0.5),
                            backgroundColor: theme.palette.background.paper,
                        }),
                    },
                },
            },
            custom,
        }),
    );
