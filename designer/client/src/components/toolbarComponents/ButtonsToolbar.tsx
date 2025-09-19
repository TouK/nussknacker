import type { PropsWithChildren, ReactElement } from "react";
import React from "react";
import { useTranslation } from "react-i18next";

import { RemoteComponent } from "../RemoteComponent";
import type { ToolbarConfig } from "../toolbarSettings/types";
import { ToolbarButtons } from "./toolbarButtons/ToolbarButtons";
import { ToolbarWrapper } from "./toolbarWrapper/ToolbarWrapper";

export type ToolbarPanelProps = PropsWithChildren<Omit<ToolbarConfig, "buttons" | "additionalParams">>;

export function ButtonsToolbar(props: ToolbarPanelProps): ReactElement {
    const { t } = useTranslation();
    const { children, title, id, buttonsVariant, componentUrl, ...passProps } = props;

    const label = title ?? id;
    const buttons = <ToolbarButtons variant={buttonsVariant}>{children}</ToolbarButtons>;

    return (
        <ToolbarWrapper id={id} title={label && t(`panels.${id}.title`, label)} {...passProps}>
            {componentUrl ? <RemoteToolbarContent {...props}>{buttons}</RemoteToolbarContent> : buttons}
        </ToolbarWrapper>
    );
}

function RemoteToolbarContent(props: ToolbarPanelProps): ReactElement {
    const { componentUrl, ...passProps } = props;
    return <RemoteComponent url={componentUrl} {...passProps} />;
}
