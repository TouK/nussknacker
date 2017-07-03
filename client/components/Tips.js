import React, { PropTypes, Component } from 'react';
import { render } from 'react-dom';
import { Scrollbars } from 'react-custom-scrollbars';
import { connect } from 'react-redux';
import _ from 'lodash'
import ActionsUtils from '../actions/ActionsUtils';
import ProcessUtils from '../common/ProcessUtils';

import InlinedSvgs from '../assets/icons/InlinedSvgs'


export class Tips extends Component {

  static propTypes = {
    currentProcess: React.PropTypes.object,
    testing: React.PropTypes.bool.isRequired,
    grouping: React.PropTypes.bool.isRequired

  }

  tipText = () => {
    if (ProcessUtils.hasNoErrorsNorWarnings(this.props.currentProcess)) {
      return (<div>{this.validTip()}</div>)
    } else {
      const errors = (this.props.currentProcess.validationResult || {}).errors
      const nodesErrors = ProcessUtils.extractInvalidNodes(errors.invalidNodes).map((error, idx) => this.printError(error.error, error.key, idx))
      const globalErrors = (errors.globalErrors || []).map((error, idx) => this.printError(error, null, idx))
      const processProperties = (errors.processPropertiesErrors || []).map((error, idx) => this.printError(error, 'Properties', idx))
      const warnings = (this.props.currentProcess.validationResult || {}).warnings
      const nodesWarnings = ProcessUtils.extractInvalidNodes(warnings.invalidNodes).map((error, idx) => this.printError(error.error, error.key, idx))
      return globalErrors.concat(processProperties.concat(nodesErrors).concat(nodesWarnings))
    }
  }

  validTip = () => {
    if (this.props.testing) {
      return "Testing mode enabled"
    } else if (this.props.grouping) {
      return "Grouping mode enabled"
    } else {
      return "Everything seems to be OK"
    }
  }

  printError = (error, suffix, idx) => {
    return (
      <div key={idx + suffix} title={error.description}>
      {(suffix ? suffix + ": " : '') + error.message + (error.fieldName ? `(${error.fieldName})` : "")}
    </div>
    )
  }

  constructor(props) {
    super(props);
  }


  render() {
    var tipsIcon = ProcessUtils.hasNoErrorsNorWarnings(this.props.currentProcess) ? InlinedSvgs.tipsInfo : InlinedSvgs.tipsWarning
    return (
        <div id="tipsPanel">
          <Scrollbars renderThumbVertical={props => <div {...props} className="thumbVertical"/>} hideTracksWhenNotNeeded={true}>
          <div className="icon" title="" dangerouslySetInnerHTML={{__html: tipsIcon}} />
          {this.tipText()}
          </Scrollbars>
        </div>
    );
  }
}

function mapState(state) {
  return {
    currentProcess: state.graphReducer.processToDisplay || {},
    testing: state.graphReducer.testResults || false,
    grouping: state.graphReducer.groupingState != null
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Tips);