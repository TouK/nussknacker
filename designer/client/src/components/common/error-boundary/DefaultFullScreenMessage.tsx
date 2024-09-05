import React, { ComponentType, PropsWithChildren, SVGProps } from "react";
import { styled, Typography } from "@mui/material";
import ErrorOccurredSvg from "./images/error-occurred.svg";

interface Props {
    message: string;
    description: string;
    Image?: ComponentType<SVGProps<SVGElement>>;
}

const StylesWrapper = styled("div")(() => ({
    height: "100%",
    width: "100%",
    padding: 0,
    margin: 0,
    display: "grid",
    alignItems: "center",
    justifyItems: "center",
    color: "text.primary", // MUI theme color reference
    backgroundColor: "background.default", // MUI theme background reference

    ".position-wrapper": {
        "--top-margin": "8vh",
        "--bottom-margin": "3vh",
        "--side-margin": "5vw",
        position: "relative",
        margin: "var(--top-margin) var(--side-margin) var(--bottom-margin)",
    },

    ".text-position": {
        containerType: "size",
        position: "absolute",
        top: "0px",
        left: 0,
        right: 0,
    },

    ".text": {
        fontWeight: 300,
        fontSize: "2.1cqi",
    },

    "h1, h2": {
        fontWeight: "inherit",
        marginBlockStart: 0,
        marginBlockEnd: 0,
    },

    h1: {
        fontSize: "2em",
    },

    h2: {
        fontSize: "1.42em",
    },

    button: {
        margin: "45px 0",
    },

    ".artwork": {
        "--top-margin": "8vh",
        "--bottom-margin": "3vh",
        "--side-margin": "5vw",
        maxWidth: "calc(100vw - 2 * var(--side-margin))",
        maxHeight: "calc(100vh - var(--top-margin) - var(--bottom-margin))",
        width: "100vmin",
    },
}));

export function DefaultFullScreenMessage({
    message,
    description,
    children,
    Image = ErrorOccurredSvg,
}: PropsWithChildren<Props>): JSX.Element {
    return (
        <StylesWrapper>
            <div className="position-wrapper">
                <div className="text-position">
                    <Typography variant={"h1"}>{message}</Typography>
                    <Typography variant={"h2"}>{description}</Typography>
                    {children}
                </div>
                <Image className="artwork" />
            </div>
        </StylesWrapper>
    );
}
