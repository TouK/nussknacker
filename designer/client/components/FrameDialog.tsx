import {WindowContentProps} from "@touk/window-manager"
import React from "react"
import {WindowContent, WindowKind} from "../windowManager"

type FrameDialogProps = WindowContentProps<WindowKind, string>

export function FrameDialog(props: FrameDialogProps): JSX.Element {
  const {data} = props

  return (
    <WindowContent {...props}>
      <iframe src={data.meta}></iframe>
    </WindowContent>
  )
}

export default FrameDialog
