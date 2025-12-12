import { useTheme } from "@mui/material";
import type { CSSProperties } from "react";

export const useJsonTreeTheme = () => {
    const MuiTheme = useTheme();

    const extend = {
        scheme: "nussknacker",
        author: "Nussknacker",
        base00: "#272822",
        base01: "#383830",
        base02: "#49483e",
        base03: "#75715e",
        base04: "#a59f85",
        base05: "#f8f8f2",
        base06: "#f5f4f1",
        base07: "#f9f8f5",
        base08: "#f92672",
        base09: MuiTheme.palette.custom.codeEditor.numeric.color, // numbers
        base0A: "#f4bf75",
        base0B: MuiTheme.palette.custom.codeEditor.string.color, // Strings
        base0C: "#a1efe4",
        base0D: MuiTheme.palette.custom.codeEditor.objectKeys.color, // Object keys
        base0E: MuiTheme.palette.custom.codeEditor.numeric.color, // booleans
        base0F: "#cc6633",
    };

    const treeStyle: CSSProperties = {
        fontFamily: `'Roboto Mono','Monaco',monospace`,
        fontSize: "0.875rem",
        paddingTop: MuiTheme.spacing(1),
        paddingBottom: MuiTheme.spacing(1),
        backgroundColor: MuiTheme.palette.custom.codeEditor.readonly.backgroundColor,
    };

    return { extend, treeStyle };
};
