import React, { PropsWithChildren } from "react";
import { useTranslation } from "react-i18next";
import { ToolbarWrapper, ToolbarWrapperProps } from "./toolbarWrapper/ToolbarWrapper";
import { ButtonsVariant, ToolbarButtons } from "./toolbarButtons";

export type ToolbarPanelProps = PropsWithChildren<{
    id: string;
    title?: string;
    buttonsVariant?: ButtonsVariant;
}>;

export function DefaultToolbarPanel(props: ToolbarPanelProps & ToolbarWrapperProps): JSX.Element {
    const { t } = useTranslation();
    const { children, title, id, buttonsVariant, ...passProps } = props;
    return (
        /* i18next-extract-disable-line */
        <ToolbarWrapper id={id} title={t(`panels.${id}.title`, title || id)} {...passProps}>
            <ToolbarButtons variant={buttonsVariant}>{children}</ToolbarButtons>
        </ToolbarWrapper>
    );
}
