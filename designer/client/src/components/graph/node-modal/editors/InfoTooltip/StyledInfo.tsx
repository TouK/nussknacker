import InfoIcon from "@mui/icons-material/Info";
import { styled } from "@mui/material";
import React from "react";

export const StyledInfo = styled(InfoIcon)(() => ({
    cursor: "pointer",
    width: "1rem",
    height: "1rem",
}));

// disable svg <title> behavior
function Span(props: React.DetailedHTMLProps<React.HTMLAttributes<HTMLSpanElement>, HTMLSpanElement>) {
    return <span title="" {...props} />;
}

export const StyledInfoChildrenWrapper = styled(Span)(() => ({
    display: "inherit",
    height: "fit-content",
}));
