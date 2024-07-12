import { ModuleUrl } from "@touk/federated-component";
import { ToolbarsSide } from "../../reducers/toolbars";
import { ButtonsVariant } from "../toolbarComponents/toolbarButtons";
import { ToolbarButton } from "./buttons";

export interface ToolbarConfig {
    id: string;
    componentUrl?: ModuleUrl;
    title?: string;
    buttons?: ToolbarButton[];
    color?: string;
    buttonsVariant?: ButtonsVariant;
    disableCollapse?: boolean;
}

export type ToolbarsConfig = Partial<Record<ToolbarsSide, ToolbarConfig[]>>;
