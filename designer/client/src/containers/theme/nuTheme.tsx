import { alpha, createTheme, Palette, PaletteMode } from "@mui/material";
import { fontFamily, globalStyles } from "./styles";
import { blendDarken, blendLighten } from "./helpers";
import { deepmerge } from "@mui/utils";
import { lightModePalette } from "./lightModePalette";
import { darkModePalette } from "./darkModePalette";
import { WindowKind } from "../../windowManager";
import { EnvironmentTagColor } from "../EnvironmentTag";
import { NodeType } from "../../types";
import NodeUtils from "../../components/graph/NodeUtils";
import { Dispatch, SetStateAction } from "react";

declare module "@mui/material/FormHelperText" {
    interface FormHelperTextPropsVariantOverrides {
        largeMessage: true;
    }

    interface FormHelperTextOwnProps {
        "data-testid"?: string;
    }
}

interface NodePalette {
    fill: string;
}

interface WindowPalette {
    backgroundColor: string;
    color: string;
}

export interface CustomPalette {
    nodes: {
        [type: string]: NodePalette;
    };
    environmentAlert: {
        [Tag in EnvironmentTagColor]: string;
    };
    windows: {
        [type: string]: WindowPalette;
        default: WindowPalette;
    };
}

declare module "@mui/material/styles" {
    interface Palette {
        custom: ReturnType<typeof extendWithHelpers>;
    }

    interface PaletteOptions {
        custom?: CustomPalette;
    }

    interface Theme {
        custom: typeof custom;
        setMode: Dispatch<SetStateAction<PaletteMode>>;
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

const extendWithHelpers = (custom: CustomPalette) => ({
    ...custom,
    getNodeStyles: function (this: CustomPalette, node: NodeType) {
        return this.nodes[NodeUtils.nodeType(node)];
    },
    getWindowStyles: function (this: CustomPalette, type = WindowKind.default) {
        switch (type) {
            case WindowKind.compareVersions:
            case WindowKind.calculateCounts:
                return this.windows.compareVersions;
            case WindowKind.customAction:
                return this.windows.customAction;
            default:
                return this.windows.default;
        }
    },
});

export function getDesignTokens(mode: PaletteMode) {
    const modePalette = mode === "light" ? lightModePalette : darkModePalette;

    return {
        palette: {
            mode,
            ...modePalette,
            custom: extendWithHelpers(modePalette.custom),
        },
    };
}

const headerCommonStyles = {
    fontWeight: 500,
    lineHeight: 1.1,
    marginTop: "20px",
    marginBottom: "10px",
};

export const nuTheme = (mode: PaletteMode, setMode: Dispatch<SetStateAction<PaletteMode>>) => {
    return createTheme(
        deepmerge(getDesignTokens(mode), {
            typography: (palette: Palette) => ({
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
                    color: palette.text.primary,
                    fontSize: "15px",
                    fontWeight: "500",
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
                            ".MuiAlert-icon": {
                                color: alpha(theme.palette.common.black, 0.54),
                                alignSelf: "center",
                            },
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
                    variants: [
                        {
                            props: { variant: "largeMessage" },
                            style: { fontSize: ".875rem" },
                        },
                    ],
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
            setMode,
        }),
    );
};
