import React from "react"
import PropTypes from "prop-types"
import * as LoaderUtils from "../../../../../common/LoaderUtils"
import {Types} from "./EditorType"

export default function SwitchIcon(props) {

  const {switchable, onClick, shouldShowSwitch, displayRawEditor, title, readOnly, fieldType} = props

  function hint() {
    if (readOnly)
      return "Switching to basic mode is disabled. You are in read-only mode"

    if (!displayRawEditor)
      return "Switch to expression mode"

    if (switchable) {
      return "Switch to basic mode"
    } else if (fieldType === Types.BOOLEAN || fieldType === Types.EXPRESSION) {
      return "Expression must be equal to true or false to switch to basic mode"
    } else if (fieldType === Types.STRING) {
      return "Expression must be a simple string literal i.e. text surrounded by single or double quotation marks or be empty to switch to basic mode"
    }
  }

  return (
    shouldShowSwitch ?
      <button className={`inlined switch-icon${  displayRawEditor ? " active " : ""}`}
              onClick={onClick}
              disabled={!switchable || readOnly}
              title={hint()}>
        <div dangerouslySetInnerHTML={{__html: LoaderUtils.loadSvgContent("buttons/switch.svg")}}/>
      </button> : null
  )
}

SwitchIcon.propTypes = {
  switchable: PropTypes.bool,
  onClick: PropTypes.func,
  shouldShowSwitch: PropTypes.bool
}