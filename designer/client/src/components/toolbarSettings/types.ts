import { ToolbarsSide } from "../../reducers/toolbars";
import { ButtonsVariant } from "../toolbarComponents/toolbarButtons";
import { ToolbarButton } from "./buttons";

export interface ToolbarConfig {
    id: string;
    title?: string;
    buttons?: ToolbarButton[];
    buttonsVariant?: ButtonsVariant;
}

export type ToolbarsConfig = Partial<Record<ToolbarsSide, ToolbarConfig[]>>;
