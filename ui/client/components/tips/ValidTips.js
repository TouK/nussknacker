import React from "react";
import PropTypes from "prop-types"
import InlinedSvgs from "../../assets/icons/InlinedSvgs";
import ValidTip from "./ValidTip";

export default function ValidTips(props) {
  const {hasNeitherErrorsNorWarnings, testing, grouping} = props

  return (
    <React.Fragment>
      {hasNeitherErrorsNorWarnings && <ValidTip icon={InlinedSvgs.tipsSuccess} message={"Everything seems to be OK"}/>}
      {testing && <ValidTip icon={InlinedSvgs.testingMode} message={"Testing mode enabled"}/>}
      {grouping && <ValidTip icon={InlinedSvgs.groupingMode} message={"Grouping mode enabled"}/>}
    </React.Fragment>
  )
}

ValidTips.propTypes = {
  grouping: PropTypes.bool.isRequired,
  testing: PropTypes.bool.isRequired
}