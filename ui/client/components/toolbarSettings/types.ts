import {ToolbarsSide} from "../../reducers/toolbars"
import {ButtonsVariant} from "../toolbarComponents/ToolbarButtons"
import {ToolbarButton} from "./buttons"

export interface ToolbarConfig {
  id: string,
  buttons?: ToolbarButton[],
  buttonsVariant?: ButtonsVariant,
}

export type ToolbarsConfig = Partial<Record<ToolbarsSide, ToolbarConfig[]>>
