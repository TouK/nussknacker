import { css } from "@emotion/css";
import { DefaultContent, DefaultContentProps } from "@touk/window-manager";
import React, { PropsWithChildren, useMemo } from "react";
import { LoadingButton } from "./LoadingButton";
import { isTouchDevice } from "../helpers/detectDevice";
import { useTheme } from "@mui/material";

const HeaderPlaceholder = () => <header>{/*grid placeholder*/}</header>;

export function PromptContent(props: PropsWithChildren<DefaultContentProps>): JSX.Element {
    const theme = useTheme();
    const classnames = useMemo(() => {
        const content = css({
            paddingBottom: theme.custom.spacing.baseUnit,
            paddingTop: theme.custom.spacing.baseUnit * 2,
            paddingLeft: theme.custom.spacing.baseUnit * 6,
            paddingRight: theme.custom.spacing.baseUnit * 6,
        });
        return { ...props.classnames, content };
    }, [props.classnames, theme.custom.spacing.baseUnit]);

    const components = useMemo(
        () => ({
            FooterButton: LoadingButton,
            ...props.components,
            Header: HeaderPlaceholder,
        }),
        [props.components],
    );

    return <DefaultContent backgroundDrag={!isTouchDevice()} {...props} classnames={classnames} components={components} />;
}
