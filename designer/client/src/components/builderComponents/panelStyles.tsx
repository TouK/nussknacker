import { alpha, Box, Paper, styled } from "@mui/material";

import { rowAceEditor, nodeValue } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";

export const PanelPaper = styled(Paper)(({ theme }) => ({
    border: `1px solid ${theme.palette.divider}`,
    borderRadius: theme.shape.borderRadius,
    overflow: "hidden",
    display: "flex",
    flexDirection: "column",
}));

export const PanelHeader = styled(Box)(({ theme }) => ({
    padding: theme.spacing(1.5, 2),
    borderBottom: `1px solid ${theme.palette.divider}`,
    backgroundColor: alpha(theme.palette.background.paper, 0.6),
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    flexShrink: 0,
}));

export const ScrollArea = styled(Box)({
    overflowY: "auto",
    maxHeight: 680,
});

export const SpelOutput = styled("pre")(({ theme }) => ({
    margin: 0,
    padding: theme.spacing(1.5, 2),
    fontSize: 12,
    lineHeight: 1.7,
    color: theme.palette.primary.light,
    overflowX: "auto",
    whiteSpace: "pre-wrap",
    wordBreak: "break-all",
    fontFamily: "monospace",
}));

export const SpelEditorContainer = styled(Box)(({ theme }) => ({
    flex: 1,
    minWidth: 0,
    [`.${nodeValue}`]: { flex: 1, width: "100%" },
    [`.${rowAceEditor}`]: {
        padding: theme.spacing(0.75, 0.75),
        minHeight: 30,
        ".ace-nussknacker": { outline: "none" },
        "& .ace_placeholder": {
            color: theme.palette.text.disabled,
            fontStyle: "italic",
            fontSize: 12,
        },
    },
}));
