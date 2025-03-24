import React from "react";
import { TOOLBAR_BUTTONS_MAP, ToolbarButton } from "./buttons";
import { TOOLBAR_COMPONENTS_MAP } from "./TOOLBAR_COMPONENTS_MAP";
import { ToolbarConfig } from "./types";
import { getToolbarComponent, getToolbarHorizontalComponent } from "./getToolbarComponent";
import { ToolbarConfig } from "./types";

function buttonSelector(btn: ToolbarButton, i: number) {
    // this type have to be specified to avoid type errors
    const Component: React.ComponentType<ToolbarButton> = TOOLBAR_BUTTONS_MAP[btn.type];
    if (!Component) return null;
    return <Component key={i} {...btn} />;
}

export type ToolbarSelectorProps = ToolbarConfig & {
    horizontal?: boolean;
};

export const toolbarSelector = ({ horizontal, ...props }: ToolbarSelectorProps): JSX.Element => {
    const Component = horizontal ? getToolbarHorizontalComponent(props) : getToolbarComponent(props);

    if (!Component) return null;

    const { buttons, ...passProps } = props;
    return <Component {...passProps}>{buttons?.map(buttonSelector)}</Component>;
};
