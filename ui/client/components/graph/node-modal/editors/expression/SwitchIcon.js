import PropTypes from "prop-types"
import React from "react"
import {ReactComponent as Icon} from "../../../../../assets/img/buttons/switch.svg"

export default function SwitchIcon(props) {

  const {switchable, readOnly, hint, onClick, displayRawEditor, shouldShowSwitch} = props

  const title = () => readOnly ? "Switching to basic mode is disabled. You are in read-only mode" : hint

  return (
    shouldShowSwitch ?
      <button id={"switch-button"}
              className={`inlined switch-icon${displayRawEditor ? " active " : ""}${readOnly ? " read-only " : ""}`}
              onClick={onClick}
              disabled={!switchable || readOnly}
              title={title()}>
        <Icon/>
      </button> : null
  )
}

SwitchIcon.propTypes = {
  switchable: PropTypes.bool,
  hint: PropTypes.string,
  onClick: PropTypes.func,
  shouldShowSwitch: PropTypes.bool,
  displayRawEditor: PropTypes.bool,
  readOnly: PropTypes.bool,
}
