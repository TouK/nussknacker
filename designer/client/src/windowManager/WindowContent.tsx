import { css } from "@emotion/css";
import { DefaultContent, DefaultContentProps } from "@touk/window-manager";
import React, { PropsWithChildren, ReactElement, useMemo } from "react";
import { LoadingButton } from "./LoadingButton";
import ErrorBoundary from "../components/common/ErrorBoundary";
import { useTheme } from "@mui/material";
import { StyledContent, StyledHeader } from "../components/graph/node-modal/node/StyledHeader";
import { IconModalHeader } from "../components/graph/node-modal/nodeDetails/NodeDetailsModalHeader";

type WindowContentProps = DefaultContentProps &
    PropsWithChildren<{
        icon?: ReactElement;
        subheader?: ReactElement;
    }>;

export function WindowContent({ children, icon, subheader, ...props }: WindowContentProps): JSX.Element {
    const theme = useTheme();

    const classnames = useMemo(
        () => ({
            footer: css({
                justifyContent: "flex-end",
            }),
            ...props.classnames,
        }),
        [props.classnames, props.data.kind, theme],
    );

    const components = useMemo<DefaultContentProps["components"]>(
        () => ({
            Header: StyledHeader,
            Content: StyledContent,
            HeaderTitle: (headerProps) => <IconModalHeader {...headerProps} startIcon={icon} subheader={subheader} />,
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
