import { css } from "@emotion/css";
import type { DefaultContentProps } from "@touk/window-manager";
import { DefaultContent, useWindowManager } from "@touk/window-manager";
import type { FooterButtonProps } from "@touk/window-manager/cjs/components/window/footer";
import type { PropsWithChildren, ReactElement } from "react";
import React, { forwardRef, useCallback, useMemo, useState } from "react";
import { useKey } from "rooks";

import { StyledContent, StyledHeader } from "../components/graph/node-modal/node/StyledHeader";
import { IconModalHeader } from "../components/graph/node-modal/nodeDetails/NodeDetailsModalHeader";
import { isInputTarget } from "../containers/BindKeyboardShortcuts";
import { LoadingButton } from "./LoadingButton";

function cleanList<T extends NonNullable<unknown>>(elements: (T | null | undefined | false | void)[]): T[] {
    return elements.filter(Boolean) as T[];
}

export type WindowContentProps = Omit<DefaultContentProps, "buttons"> &
    PropsWithChildren<{
        icon?: ReactElement;
        subheader?: ReactElement;
        buttons?: (FooterButtonProps | false)[];
        closeWithEsc?: boolean;
    }>;

export const WindowContent = forwardRef<HTMLDivElement, WindowContentProps>(function WindowContent(
    { children, icon, subheader, buttons = [], closeWithEsc, ...props },
    ref,
): React.JSX.Element {
    const { frontWindow } = useWindowManager();
    const classnames = useMemo(
        () => ({
            footer: css({
                justifyContent: "flex-end",
            }),
            ...props.classnames,
        }),
        [props.classnames],
    );

    useKey(
        "Escape",
        (e) => {
            e.preventDefault();
            if (!isInputTarget(e.composedPath().shift())) {
                props.close();
            }
        },
        { when: closeWithEsc && props.data.id === frontWindow },
    );

    const [stableIcon] = useState(icon);
    const [stableSubheader] = useState(subheader);

    const CustomHeaderTitle: DefaultContentProps["components"]["HeaderTitle"] = useCallback(
        (headerProps) => {
            return <IconModalHeader {...headerProps} startIcon={stableIcon} subheader={stableSubheader} />;
        },
        [stableIcon, stableSubheader],
    );

    const components = useMemo<DefaultContentProps["components"]>(
        () => ({
            Header: StyledHeader,
            Content: StyledContent,
            HeaderTitle: CustomHeaderTitle,
            FooterButton: LoadingButton,
            ...props.components,
        }),
        [CustomHeaderTitle, props.components],
    );

    return (
        <DefaultContent ref={ref} {...props} buttons={cleanList(buttons)} components={components} classnames={classnames}>
            {children}
        </DefaultContent>
    );
});
