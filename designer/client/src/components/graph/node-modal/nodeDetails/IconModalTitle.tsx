import React from "react";
import { styled, Typography } from "@mui/material";
import { PropsOf } from "@emotion/react";

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
                <Typography component="span" variant={"subtitle2"}>
                    {children}
                </Typography>
            )}
            {endIcon}
        </ModalTitle>
    );
}
