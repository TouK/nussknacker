import {WindowContentProps} from "@touk/window-manager"
import React, {useMemo} from "react"
import {WindowContent, WindowKind} from "../windowManager"
import styled from "@emotion/styled"

const FullSizeBorderlessIFrame = styled.iframe({
  border: 0,
  background: "white",
  width: "100%",
  height: "100%",
  minWidth: 0,
  minHeight: 0,
})

export function FrameDialog(props: WindowContentProps<WindowKind, string>): JSX.Element {
  const {data: {meta}} = props
  const components = useMemo(
    () => ({
      Content: () => <FullSizeBorderlessIFrame src={meta}/>,
      Footer: () => null,
    }),
    [meta]
  )

  return (
    <WindowContent {...props} components={components}/>
  )
}

export default FrameDialog
