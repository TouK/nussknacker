import {ReactChild} from "react"
import {ToolbarsSide} from "../../reducers/toolbars"

export interface Toolbar {
  id: string,
  component: ReactChild,
  isDragDisabled?: boolean,
  defaultSide?: ToolbarsSide,
}
