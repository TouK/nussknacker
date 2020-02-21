import {ReactEventHandler} from "react"
import {DropEvent} from "react-dropzone"

export interface PanelButton {
  name: string,
  icon: string,
  onClick: ReactEventHandler,
  isHidden?: boolean,
  disabled?: boolean,
  onMouseOut?: ReactEventHandler,
  onMouseOver?: ReactEventHandler,
  onDrop?: <T extends File>(acceptedFiles: T[], rejectedFiles: T[], event: DropEvent) => void,
  title?: string,
  className?: string,
}

export interface PanelConfig {
  panelName: string,
  buttons: PanelButton[],
  isHidden?: boolean,
}
