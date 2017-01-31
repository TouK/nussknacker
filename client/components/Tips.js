import React, { PropTypes, Component } from 'react';
import { render } from 'react-dom';
import { Scrollbars } from 'react-custom-scrollbars';
import { connect } from 'react-redux';
import _ from 'lodash'
import ActionsUtils from '../actions/ActionsUtils';
import InlinedSvgs from '../assets/icons/InlinedSvgs'


export class Tips extends Component {

  static propTypes = {
    currentProcess: React.PropTypes.object,
    testing: React.PropTypes.bool.isRequired,
    grouping: React.PropTypes.bool.isRequired

  }

  validationResult = () => this.props.currentProcess.validationResult

  isProcessValid = () => {
    const result = this.validationResult()
    return !result || (Object.keys(result.invalidNodes || {}).length == 0
      && (result.globalErrors || []).length == 0 && (result.processPropertiesErrors || []).length == 0)
  }

  tipText = () => {
    if (this.isProcessValid()) {
      return this.validTip()
    } else {
      const result = this.validationResult()
      const nodesErrors = _.flatten(Object.keys(result.invalidNodes || {}).map((key) => result.invalidNodes[key].map(error => this.printError(error, key))))
      const globalErrors = (result.globalErrors || []).map(this.printError)
      const processProperties = (result.processPropertiesErrors || []).map(error => this.printError(error, 'Properties'))
      return globalErrors.concat(processProperties.concat(nodesErrors)).join('\n')
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

  printError = (error, suffix) => (suffix ? suffix + ": " : '') + error.message + (error.fieldName ? `(${error.fieldName})` : "")


  constructor(props) {
    super(props);
  }


  render() {
    var tipsIcon = this.isProcessValid() ? InlinedSvgs.tipsInfo : InlinedSvgs.tipsWarning
    return (
        <div id="tipsPanel">
          <Scrollbars renderThumbVertical={props => <div {...props} className="thumbVertical"/>}>
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
    testing: state.graphReducer.testResults != null,
    grouping: state.graphReducer.groupingState != null
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Tips);