import type { ModuleUrl } from "@touk/federated-component";

import type { ToolbarsSide } from "../../reducers/toolbars";
import type { ButtonsVariant } from "../toolbarComponents/toolbarButtons";
import type { ToolbarButton } from "./buttons";

export interface ToolbarConfig {
    id: string;
    componentUrl?: ModuleUrl;
    title?: string;
    buttons?: ToolbarButton[];
    color?: string;
    buttonsVariant?: ButtonsVariant;
    disableCollapse?: boolean;
    additionalParams?: Record<string, string>;
}

export type ToolbarsConfig = Partial<Record<ToolbarsSide, ToolbarConfig[]>>;
