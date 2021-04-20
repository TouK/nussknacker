import React from "react"
import {useSelector} from "react-redux"
import {getCapabilities} from "../../reducers/selectors/other"
import ToolbarButton, {ToolbarButtonProps} from "./ToolbarButton"

interface Props {
  write?: boolean,
  change?: boolean,
  deploy?: boolean,
}

export function CapabilitiesToolbarButton({deploy, change, write, ...props}: ToolbarButtonProps & Props): JSX.Element | null {
  const capabilities = useSelector(getCapabilities)
  const checks = {deploy, change, write}

  if (Object.keys(capabilities).some(key => checks[key] && !capabilities[key])) {
    return null
  }

  return <ToolbarButton {...props}/>
}
