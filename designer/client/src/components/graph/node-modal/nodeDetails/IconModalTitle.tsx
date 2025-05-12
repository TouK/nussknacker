import type { PropsOf } from "@emotion/react";
import { styled, Typography } from "@mui/material";
import React from "react";

const ModalTitle = styled("div")({
    display: "flex",
    alignItems: "center",
});

type IconModalTitleProps = PropsOf<typeof ModalTitle> & {
    startIcon?: React.ReactElement;
    endIcon?: React.ReactElement;
};

export function IconModalTitle({ startIcon, endIcon, children, ...props }: IconModalTitleProps) {
    return (
        <ModalTitle {...props}>
            {startIcon}
            {children && (
                <Typography component="span" variant={"subtitle2"} noWrap>
                    {children}
                </Typography>
            )}
            {endIcon}
        </ModalTitle>
    );
}
