import { Box, styled } from "@mui/material";

import { editorAnchorName } from "../NodeDetailsContent/NodeTableStyled";

type RowFieldLabelProps = {
    label: string;
    showLabel: boolean;
};

export const RowFieldLabel = styled(Box, {
    shouldForwardProp: (propName: string) => !["label", "showLabel"].includes(propName),
})<RowFieldLabelProps>(({ theme, showLabel, label }) => ({
    "&::before": {
        content: showLabel ? `'${label}'` : "unset",

        ...theme.typography.overline,
        color: theme.palette.text.disabled,

        "position-anchor": editorAnchorName,
        position: "absolute",
        bottom: "calc(anchor(top))",
        marginBottom: theme.spacing(0.75),

        "[data-rfd-droppable-context-id] &": {
            content: "unset",
        },

        "[data-rfd-droppable-context-id] > :first-of-type &": {
            content: showLabel ? "unset" : `'${label}'`,
        },
    },
}));
