import { mapValues } from "lodash";
import React from "react";

import type { ToolbarButton } from "./buttons";
import { TOOLBAR_BUTTONS_MAP } from "./buttons";
import { getToolbarComponent, getToolbarHorizontalComponent } from "./getToolbarComponent";
import type { ToolbarConfig } from "./types";

function buttonSelector(btn: ToolbarButton, i: number) {
    // this type have to be specified to avoid type errors
    const Component: React.ComponentType<ToolbarButton> = TOOLBAR_BUTTONS_MAP[btn.type];
    if (!Component) return null;
    return <Component key={i} {...btn} />;
}

export type ToolbarSelectorProps = ToolbarConfig & {
    horizontal?: boolean;
};

function saveParse(value: string) {
    try {
        return JSON.parse(value);
    } catch {
        return value;
    }
}

export const toolbarSelector = ({ horizontal, ...props }: ToolbarSelectorProps): JSX.Element => {
    const Component = horizontal ? getToolbarHorizontalComponent(props) : getToolbarComponent(props);

    if (!Component) return null;

    const { buttons, additionalParams, ...passProps } = props;
    return (
        <Component {...passProps} {...mapValues(additionalParams, saveParse)}>
            {buttons?.map(buttonSelector)}
        </Component>
    );
};
