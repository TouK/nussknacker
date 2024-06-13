import React, { PropsWithChildren, ReactElement, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { splitUrl } from "@touk/federated-component";
import { ToolbarButtons } from "./toolbarButtons";
import { ToolbarConfig } from "../toolbarSettings/types";
import { ToolbarWrapper } from "./toolbarWrapper/ToolbarWrapper";
import { RemoteComponent } from "../RemoteComponent";

export type ToolbarPanelProps = PropsWithChildren<Omit<ToolbarConfig, "buttons">>;

export function DefaultToolbarPanel(props: ToolbarPanelProps): ReactElement {
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
    const [url, scope] = useMemo(() => splitUrl(componentUrl), [componentUrl]);

    return <RemoteComponent url={url} scope={scope} {...passProps} />;
}
