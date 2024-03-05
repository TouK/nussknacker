import { Box, styled } from "@mui/material";
import React from "react";
import Highlighter from "react-highlight-words";

function HighlightComponent({ value, filterText, className }: { value?: string; filterText: string; className?: string }): JSX.Element {
    return (
        <Box
            component={Highlighter}
            sx={(theme) => ({ ...theme.typography.body2 })}
            autoEscape
            className={className}
            textToHighlight={value.toString()}
            searchWords={filterText?.toString().trim().split(/\s/) || []}
            highlightTag={"strong"}
        />
    );
}

export const Highlight = styled(HighlightComponent)(({ theme }) => ({
    strong: {
        color: theme.palette.primary.main,
    },
}));
