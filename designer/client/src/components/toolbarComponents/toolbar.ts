import type { ReactElement } from "react";

import type { ToolbarsSide } from "../../reducers/toolbars";
import type { ToolbarConfig } from "../toolbarSettings/types";

export interface Toolbar extends ToolbarConfig {
    id: string;
    component: ReactElement;
    horizontalComponent: ReactElement | null;
    isHidden?: boolean;
    defaultSide?: ToolbarsSide;
}
