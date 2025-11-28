import InfoIcon from "@mui/icons-material/Info";
import { styled } from "@mui/material";
import React, { forwardRef } from "react";

export const StyledInfo = styled(InfoIcon)(() => ({
    cursor: "pointer",
    width: "1rem",
    height: "1rem",
}));

// disable svg <title> behavior
const Span = forwardRef(function Span(
    props: React.DetailedHTMLProps<React.HTMLAttributes<HTMLSpanElement>, HTMLSpanElement>,
    forwardedRef: React.Ref<HTMLSpanElement>,
) {
    return <span ref={forwardedRef} title="" {...props} />;
});

export const StyledInfoChildrenWrapper = styled(Span)(() => ({
    display: "inherit",
    height: "fit-content",
}));
