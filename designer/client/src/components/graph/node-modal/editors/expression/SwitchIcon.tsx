import React from "react"
import classes from "../../../../../stylesheets/graph.styl"
import {ButtonWithFocus} from "../../../../withFocus"
import {ReactComponent as Icon} from "../../../../../assets/img/buttons/switch.svg"
import {cx} from "@emotion/css"

interface SwitchIconProps {
  switchable?: boolean,
  hint?: string,
  displayRawEditor?: boolean,
  readOnly?: boolean,
  onClick?: () => void,
}

export default function SwitchIcon({switchable, readOnly, hint, onClick, displayRawEditor}: SwitchIconProps) {
  const disabled = !switchable || readOnly
  return (
    <ButtonWithFocus
      className={cx(
        classes.switchIcon,
        displayRawEditor && classes.switchIconActive,
        disabled && classes.switchIconReadOnly
      )}
      onClick={onClick}
      disabled={disabled}
      title={readOnly ? "Switching to basic mode is disabled. You are in read-only mode" : hint}
    >
      <Icon/>
    </ButtonWithFocus>
  )
}
