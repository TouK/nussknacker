import PropTypes from "prop-types"
import React from "react"
import * as LoaderUtils from "../../../../../common/LoaderUtils"

export default function SwitchIcon(props) {

  const {switchable, readOnly, hint, onClick, displayRawEditor, shouldShowSwitch} = props

  const title = () => readOnly ? "Switching to basic mode is disabled. You are in read-only mode" : hint

  return (
    shouldShowSwitch ?
      <button className={`inlined switch-icon${displayRawEditor ? " active " : ""}`}
              onClick={onClick}
              disabled={!switchable || readOnly}
              title={title(readOnly)}>
        <div dangerouslySetInnerHTML={{__html: LoaderUtils.loadSvgContent("buttons/switch.svg")}}/>
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
