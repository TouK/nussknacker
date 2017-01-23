import React from 'react'
import {render} from "react-dom";
import {connect} from "react-redux";
import ActionsUtils from "../actions/ActionsUtils";
import _ from "lodash";

class ProcessTopBar extends React.Component {

  static propTypes = {
    currentProcess: React.PropTypes.object
  };

  validationResult = () => (this.props.currentProcess || {}).validationResult

  isProcessValid = () => {
    const result = this.validationResult()
    return !result || (Object.keys(result.invalidNodes || {}).length == 0
      && (result.globalErrors || []).length == 0 && (result.processPropertiesErrors || []).length == 0)
  }

  invalidTooltip = () => {
    if (this.isProcessValid()) {
      return "";
    } else {
      const result = this.validationResult()
      const nodesErrors = _.flatten(Object.keys(result.invalidNodes || {}).map((key) => result.invalidNodes[key].map(error => this.printError(error, key))))
      const globalErrors = (result.globalErrors || []).map(this.printError)
      const processProperties = (result.processPropertiesErrors || []).map(error => this.printError(error, 'Properties'))
      return globalErrors.concat(processProperties.concat(nodesErrors)).join('\n')
    }
  }

  printError = (error, suffix) => (suffix ? suffix + ": " : '') + error.message + (error.fieldName ? `(${error.fieldName})` : "")

  render() {
    return (
      <span id="app-name" className="vert-middle" title={this.invalidTooltip()}>
        <span>{this.props.currentProcess.id}</span>
        { this.isProcessValid() ? null : (
          <span className="invalidProcess"/>
        )}
      </span>
    )
  }
}


function mapState(state) {
  return {
    currentProcess: (state.graphReducer.processToDisplay || {})
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessTopBar);
