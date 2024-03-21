import { css, cx } from "@emotion/css";
import { DefaultContent, DefaultContentProps } from "@touk/window-manager";
import React, { PropsWithChildren, useMemo } from "react";
import { getWindowColors } from "./getWindowColors";
import { LoadingButton } from "./LoadingButton";
import ErrorBoundary from "../components/common/ErrorBoundary";

export function WindowContent({ children, ...props }: PropsWithChildren<DefaultContentProps>): JSX.Element {
    const classnames = useMemo(
        () => ({
            header: cx(getWindowColors(props.data.kind)),
            footer: css({
                justifyContent: "flex-end",
            }),
            ...props.classnames,
        }),
        [props.classnames, props.data.kind],
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
