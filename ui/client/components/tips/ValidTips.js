import React from 'react';
import PropTypes from "prop-types"
import InlinedSvgs from "../../assets/icons/InlinedSvgs";
import ValidTip from "./ValidTip";

export default class ValidTips extends React.Component {

  static propTypes = {
    grouping: PropTypes.bool.isRequired,
    testing: PropTypes.bool.isRequired
  }

  render() {
    const {grouping, testing} = this.props
    return (
      <React.Fragment>
        <ValidTip icon={InlinedSvgs.tipsSuccess} message={"Everything seems to be OK"}/>
        {testing && <ValidTip icon={InlinedSvgs.testingMode} message={"Testing mode enabled"}/>}
        {grouping && <ValidTip icon={InlinedSvgs.groupingMode} message={"Grouping mode enabled"}/>}
      </React.Fragment>
    )
  }
}