import React, { PropsWithChildren, ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { ToolbarButtons } from "../toolbarComponents/toolbarButtons";
import { ToolbarConfig } from "../toolbarSettings/types";
import { ToolbarWrapper } from "./toolbarWrapper/ToolbarWrapper";

export type ToolbarPanelProps = PropsWithChildren<Omit<ToolbarConfig, "buttons">>;

export function DefaultToolbarPanel(props: ToolbarPanelProps): ReactElement {
    const { t } = useTranslation();
    const { children, title, id, buttonsVariant, ...passProps } = props;
    const label = title ?? id;
    return (
        /* i18next-extract-disable-line */
        <ToolbarWrapper id={id} title={label && t(`panels.${id}.title`, label)} {...passProps}>
            <ToolbarButtons variant={buttonsVariant}>{children}</ToolbarButtons>
        </ToolbarWrapper>
    );
}
