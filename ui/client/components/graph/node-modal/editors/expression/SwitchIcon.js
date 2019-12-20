import React from "react"
import PropTypes from "prop-types"
import * as LoaderUtils from "../../../../../common/LoaderUtils"

export default function SwitchIcon(props) {

  const {switchable, onClick, shouldShowSwitch, displayRawEditor, title, readOnly} = props

  function hint(switchable, displayRawEditor, readOnly) {
    if (readOnly) {
      return "Switching to basic mode is disabled. You are in read-only mode"
    } else {
      return !displayRawEditor ? "Switch to expression mode" :
        (switchable ? "Switch to basic mode" :
          "Expression must be equal to true, false or be empty to switch to basic mode")
    }
  }

  return (
    shouldShowSwitch ?
      <button className={"inlined switch-icon" + (displayRawEditor ? " active " : "")}
              onClick={onClick}
              disabled={!switchable || readOnly}
              title={hint(switchable, displayRawEditor, readOnly)}>
        <div dangerouslySetInnerHTML={{__html: LoaderUtils.loadSvgContent("buttons/switch.svg")}}/>
      </button> : null
  )
}

SwitchIcon.propTypes = {
  switchable: PropTypes.bool,
  onClick: PropTypes.func,
  shouldShowSwitch: PropTypes.bool
}