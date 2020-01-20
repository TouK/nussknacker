import PropTypes from "prop-types"
import React from "react"
import InlinedSvgs from "../../assets/icons/InlinedSvgs"

const ProcessDialogWarnings = (props) => {
  return(
    props.processHasWarnings ?
    <div className="warning">
      <div className="icon" title="Warning" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}}/>
      <p> Warnings found - please look at left panel to see details. Proceed with caution </p>
    </div> : null
  )
}

ProcessDialogWarnings.propTypes = {
  processHasWarnings: PropTypes.bool.isRequired,
}

export default ProcessDialogWarnings
