import React from "react"
import {useSelector} from "react-redux"
import {getCapabilities} from "../../reducers/selectors/other"
import ToolbarButton, {ToolbarButtonProps} from "./ToolbarButton"

interface Props {
  write?: boolean,
  change?: boolean,
  deploy?: boolean,
  hide?: boolean,
  editFrontend?: boolean
}

export function CapabilitiesToolbarButton({deploy, change, write, editFrontend, disabled, hide, ...props}: ToolbarButtonProps & Props): JSX.Element | null {
  const capabilities = useSelector(getCapabilities)
  const checks = {deploy, change, write, editFrontend}
  const hiddenByCapabilities = Object.keys(capabilities).some(key => checks[key] && !capabilities[key])

  if (hide && hiddenByCapabilities) {
    return null
  }

  const overridesProps = {...props, ...{disabled: disabled || hiddenByCapabilities}}

  return <ToolbarButton {...overridesProps} />
}
