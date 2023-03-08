import PropTypes from "prop-types"
import React from "react"
import classes from "../../../../../stylesheets/graph.styl"
import cn from "classnames"
import {ButtonWithFocus} from "../../../../withFocus"
import {ReactComponent as Icon} from "../../../../../assets/img/buttons/switch.svg"

export default function SwitchIcon(props) {

  const {switchable, readOnly, hint, onClick, displayRawEditor} = props

  const title = () => readOnly ? "Switching to basic mode is disabled. You are in read-only mode" : hint

  return (
    <ButtonWithFocus
      id={"switch-button"}
      className={cn("inlined", "switch-icon", displayRawEditor && classes.switchIconActive, readOnly && classes.switchIconReadOnly)}
      onClick={onClick}
      disabled={!switchable || readOnly}
      title={title()}
    >
      <Icon/>
    </ButtonWithFocus>
  )
}

SwitchIcon.propTypes = {
  switchable: PropTypes.bool,
  hint: PropTypes.string,
  onClick: PropTypes.func,
  displayRawEditor: PropTypes.bool,
  readOnly: PropTypes.bool,
}
