import { styled } from "@mui/material/styles";
import React, { forwardRef } from "react";

import { getBorderColor } from "../../../../../containers/theme/helpers";
import AceEditor from "./ace";

type StyledAceEditorProps = React.ComponentProps<typeof AceEditor> & {
    codeTheme: string | "nussknacker";
};

const AceEditorBase = forwardRef<AceEditor, StyledAceEditorProps>(({ codeTheme, ...props }, ref) => (
    <AceEditor ref={ref} theme={codeTheme} {...props} />
));

AceEditorBase.displayName = "AceEditorBase";

export const StyledAceEditor = styled(AceEditorBase)(({ theme }) => ({
    "& .ace_tooltip": {
        ...theme.typography.body2,
        fontSize: "0.75rem",
        padding: theme.spacing(1),
        background: theme.palette.background.paper,
        borderColor: getBorderColor(theme),
        borderRadius: "6px",
        position: "sticky", // To keep the tooltip near error button in a Ace editor gutter
        minWidth: "400px",
    },
    "& .ace-error-marker": {
        position: "absolute",
        borderBottom: `2px solid ${theme.palette.error.main}`,
        borderRadius: 0,
    },
}));
