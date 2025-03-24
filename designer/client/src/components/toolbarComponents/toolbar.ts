import { ReactElement } from "react";
import { ToolbarsSide } from "../../reducers/toolbars";
import { ToolbarConfig } from "../toolbarSettings/types";

export interface Toolbar extends ToolbarConfig {
    id: string;
    component: ReactElement;
    horizontalComponent: ReactElement | null;
    isHidden?: boolean;
    defaultSide?: ToolbarsSide;
}
