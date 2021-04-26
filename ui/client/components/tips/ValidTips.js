import PropTypes from "prop-types"
import React from "react"
import InlinedSvgs from "../../assets/icons/InlinedSvgs"
import ValidTip from "./ValidTip"

export default function ValidTips(props) {
  const {hasNeitherErrorsNorWarnings, testing} = props

  return (
    <React.Fragment>
      {hasNeitherErrorsNorWarnings && <ValidTip icon={InlinedSvgs.tipsSuccess} message={"Everything seems to be OK"}/>}
      {testing && <ValidTip icon={InlinedSvgs.testingMode} message={"Testing mode enabled"}/>}
    </React.Fragment>
  )
}

ValidTips.propTypes = {
  testing: PropTypes.bool.isRequired,
}
