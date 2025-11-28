import { styled, Typography, type TypographyProps } from "@mui/material";
import React, { forwardRef } from "react";

const Typo = forwardRef(function Typo(props: TypographyProps, forwardedRef: React.Ref<HTMLElement>) {
    return <Typography ref={forwardedRef} variant="subtitle1" {...props} />;
});

export const VersionHeader = styled(Typo)(({ theme }) => ({
    margin: theme.spacing(2, 4),
}));

export const CompareModal = styled("div")`
    font-size: 15px;
    font-weight: 700;
`;

export const CompareContainer = styled("div")`
    zoom: 0.9;
    > div:nth-of-type(1),
    > div:nth-of-type(2) {
        width: 50%;
        display: inline-block;
        vertical-align: top;
    }
`;
