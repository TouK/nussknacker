import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {render} from 'react-dom';
import {Scrollbars} from 'react-custom-scrollbars';
import {connect} from 'react-redux';
import ActionsUtils from '../actions/ActionsUtils';
import ProcessUtils from '../common/ProcessUtils';

import InlinedSvgs from '../assets/icons/InlinedSvgs'


export class Tips extends Component {

  static propTypes = {
    currentProcess: PropTypes.object,
    grouping: PropTypes.bool.isRequired,
    isHighlighted: PropTypes.bool,
    testing: PropTypes.bool.isRequired
  }

  constructor(props) { super(props) }

  tipText = () => {
    if (ProcessUtils.hasNoErrorsNorWarnings(this.props.currentProcess)) {
      return (<div>{this.validTip()}</div>)
    } else {
      const errors = (this.props.currentProcess.validationResult || {}).errors
      const globalErrors = (errors.globalErrors || []).map((error, idx) => this.printError(error, null, idx))
      const invalidNodeErrors = this.customError(Object.keys(errors.invalidNodes), "Errors in nodes: " + Object.keys(errors.invalidNodes).join(', '))
      const processPropertiesErrors = this.customError(errors.processPropertiesErrors, "Errors in process properties")
      const warnings = (this.props.currentProcess.validationResult || {}).warnings
      const nodesWarnings = ProcessUtils.extractInvalidNodes(warnings.invalidNodes).map((error, idx) => this.printError(error.error, error.key, idx))
      return _.concat(globalErrors, nodesWarnings, invalidNodeErrors, processPropertiesErrors)
    }
  }

  customError(errors, message) {
    return (
      errors.length > 0 ?
        <div>
          {message}
        </div> : null
    )
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

  className = () =>
    this.props.isHighlighted ? "tipsPanelHighlighted" : "tipsPanel"

  render() {
    var tipsIcon = ProcessUtils.hasNoErrorsNorWarnings(this.props.currentProcess) ? InlinedSvgs.tipsInfo : InlinedSvgs.tipsWarning
    return (
        <div id="tipsPanel" className={this.className()}>
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
    grouping: state.graphReducer.groupingState != null,
    isHighlighted: state.ui.isToolTipsHighlighted,
    testing: !!state.graphReducer.testResults
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Tips);
