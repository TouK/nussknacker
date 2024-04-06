import { ToolbarsSide } from "../../reducers/toolbars";
import { ButtonsVariant } from "../toolbarComponents/toolbarButtons";
import { ToolbarButton } from "./buttons";

export interface ToolbarConfig {
    id: string;
    title?: string;
    buttons?: ToolbarButton[];
    color?: string;
    buttonsVariant?: ButtonsVariant;
    disableCollapse?: boolean;
}

export type ToolbarsConfig = Partial<Record<ToolbarsSide, ToolbarConfig[]>>;
