import { css } from "@emotion/css";
import { useTheme } from "@mui/material";
import type { DefaultContentProps } from "@touk/window-manager";
import { DefaultContent } from "@touk/window-manager";
import type { PropsWithChildren } from "react";
import React, { useMemo } from "react";

import { isTouchDevice } from "../helpers/detectDevice";
import { LoadingButton } from "./LoadingButton";

const HeaderPlaceholder = () => <header>{/*grid placeholder*/}</header>;

export function PromptContent({ children, ...props }: PropsWithChildren<DefaultContentProps>): React.JSX.Element {
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

    return (
        <DefaultContent backgroundDrag={!isTouchDevice()} {...props} classnames={classnames} components={components}>
            {children}
        </DefaultContent>
    );
}
