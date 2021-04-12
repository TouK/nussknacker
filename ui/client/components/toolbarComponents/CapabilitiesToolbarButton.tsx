import React from "react"
import {useSelector} from "react-redux"
import {getCapabilities} from "../../reducers/selectors/other"
import ToolbarButton, {ToolbarButtonProps} from "./ToolbarButton"

interface Props {
  write?: boolean,
  change?: boolean,
  deploy?: boolean,
}

export function CapabilitiesToolbarButton(props: ToolbarButtonProps & Props): JSX.Element | null {
  const capabilities = useSelector(getCapabilities)
  if (Object.keys(capabilities).some(key => props[key] && !capabilities[key])) {
    return null
  }

  return <ToolbarButton {...props}/>
}
