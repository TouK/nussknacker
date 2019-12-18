import React from "react"
import PropTypes from "prop-types"
import * as LoaderUtils from "../../../../../common/LoaderUtils"

export default function SwitchIcon(props) {

  const {switchable, onClick, shouldShowSwitch, displayRawEditor, title} = props

  function hint(switchable, displayRawEditor) {
    return !displayRawEditor ? "Switch to advanced mode" :
      (switchable ? "Switch to more intuitive mode" :
        "Expression must be equal to true or false to switch to more intuitive mode")
  }

  return (
    shouldShowSwitch ?
      <button className={"inlined switch-icon" + (displayRawEditor ? " active " : "")}
              onClick={onClick}
              disabled={!switchable}
              title={hint(switchable, displayRawEditor)}>
        <div dangerouslySetInnerHTML={{__html: LoaderUtils.loadSvgContent("buttons/switch.svg")}}/>
      </button> : null
  )
}

SwitchIcon.propTypes = {
  switchable: PropTypes.bool,
  onClick: PropTypes.func,
  shouldShowSwitch: PropTypes.bool
}