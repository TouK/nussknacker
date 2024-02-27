import { css, cx } from "@emotion/css";
import { DefaultContent, DefaultContentProps } from "@touk/window-manager";
import React, { PropsWithChildren, useMemo } from "react";
import { getWindowColors } from "./getWindowColors";
import { LoadingButton } from "./LoadingButton";
import ErrorBoundary from "../components/common/ErrorBoundary";
import { alpha, useTheme } from "@mui/material";

export function WindowContent({ children, ...props }: PropsWithChildren<DefaultContentProps>): JSX.Element {
    const theme = useTheme();

    const classnames = useMemo(
        () => ({
            header: cx(getWindowColors(props.data.kind)),
            headerButtons: css({
                fontSize: 15,
                "button:focus": {
                    background: alpha(theme.custom.colors.secondaryColor, 0.25),
                },
                "button:hover": {
                    background: alpha(theme.custom.colors.secondaryColor, 0.75),
                },
                "button[name=close]:focus": {
                    background: alpha(theme.custom.colors.danger, 0.5),
                },
                "button[name=close]:hover": {
                    background: theme.custom.colors.danger,
                    "svg path": { fill: theme.custom.colors.secondaryColor },
                },
            }),
            footer: css({
                justifyContent: "flex-end",
                background: theme.custom.colors.secondaryBackground,
                borderTop: `${theme.custom.spacing.baseUnit / 3}px solid ${theme.custom.colors.borderColor}`,
            }),
            ...props.classnames,
        }),
        [props.classnames, props.data.kind, theme],
    );

    const components = useMemo(
        () => ({
            FooterButton: LoadingButton,
            ...props.components,
        }),
        [props.components],
    );

    return (
        <DefaultContent {...props} components={components} classnames={classnames}>
            <ErrorBoundary>{children}</ErrorBoundary>
        </DefaultContent>
    );
}
