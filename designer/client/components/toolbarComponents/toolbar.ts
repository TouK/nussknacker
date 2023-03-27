import {ReactChild} from "react"
import {ToolbarsSide} from "../../reducers/toolbars"

export interface Toolbar {
  id: string,
  component: ReactChild,
  isHidden?: boolean,
  defaultSide?: ToolbarsSide,
}
