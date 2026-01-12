import { Box, styled } from "@mui/material";
import React from "react";

import { InfoTooltip } from "../InfoTooltip/InfoTooltip";
import type { EditorConfig } from "./EditorConfig";
import { editorsParameters } from "./editorsParameters";
import { useHelpText } from "./helpText";

export function Info({ editorConfig }: { editorConfig: EditorConfig }) {
    const helpText = useHelpText(editorsParameters[editorConfig.type].language);
    if (!helpText) return null;
    return (
        <InfoTooltip
            customComponentsProps={{
                tooltip: {
                    sx: (theme) => ({
                        maxWidth: { xs: "none", sm: `${theme.breakpoints.values.sm}px` },
                    }),
                },
            }}
            title={helpText}
        />
    );
}

export const InputAdornmentEndColumn = styled(Box)({
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    width: "1rem",
    gap: 0.5,
});
