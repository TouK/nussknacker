import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {Scrollbars} from 'react-custom-scrollbars';
import {connect} from 'react-redux';
import ActionsUtils from '../actions/ActionsUtils';
import ProcessUtils from '../common/ProcessUtils';
import {v4 as uuid4} from "uuid";
import InlinedSvgs from "../assets/icons/InlinedSvgs"

import NodeUtils from "./graph/NodeUtils"
import {Link} from "react-router-dom"

export class Tips extends Component {

  static propTypes = {
    currentProcess: PropTypes.object,
    grouping: PropTypes.bool.isRequired,
    isHighlighted: PropTypes.bool,
    testing: PropTypes.bool.isRequired
  }

  tipsText = () => {
    if (ProcessUtils.hasNoErrorsNorWarnings(this.props.currentProcess)) {
      return (<div key={uuid4()}>{this.validTip()}</div>)
    } else {
      const errors = (this.props.currentProcess.validationResult || {}).errors
      const errorTips = this.errors((this.props.currentProcess.validationResult || {}).errors)

      const warnings = (this.props.currentProcess.validationResult || {}).warnings
      const warningTips = this.warnings(ProcessUtils.extractInvalidNodes(warnings.invalidNodes))

      return _.concat(errorTips, warningTips)
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

  errors = (errors) =>
    <div key={uuid4()} className={"error-tips"}>
      {this.headerIcon(errors)}
      {this.errorTips(errors)}
    </div>

  headerIcon = (errors) =>
    _.isEmpty(errors.globalErrors) && _.isEmpty(errors.invalidNodes) && _.isEmpty(errors.processPropertiesErrors) ? null :
      <div className="icon" title="" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsError}}/>

  errorTips = (errors) => {
    const globalErrors = errors.globalErrors || []
    const nodeErrors = errors.invalidNodes || {}
    const propertiesErrors = errors.processPropertiesErrors || []
    const nodeIds = Object.keys(nodeErrors)
    const separator = ', '

    return _.isEmpty(nodeErrors) && _.isEmpty(propertiesErrors) && _.isEmpty(globalErrors) ? null :
      <div className={"node-error-section"}>
        <div>
          {this.globalErrorsTips(globalErrors)}
          {this.nodeErrorsTips(nodeIds, propertiesErrors, separator, nodeErrors)}
        </div>
      </div>
  }

  globalErrorsTips = (globalErrors) =>
    <div>
      {
        globalErrors.map((error, idx) => this.globalError(error, null))
      }
    </div>

  globalError = (error, suffix) =>
    <span key={uuid4()} title={error.description}>
        {(suffix ? suffix + ": " : '') + error.message + (error.fieldName ? `(${error.fieldName})` : "")}
    </span>

  nodeErrorsTips = (nodeIds, propertiesErrors, separator, nodeErrors) =>
    <div className={"node-error-tips"}>
      {
        _.isEmpty(nodeIds) && _.isEmpty(propertiesErrors) ? null :
          <span>Errors in </span>
      }
      <div className={"node-error-links"}>
        {
          _.isEmpty(nodeIds) ? null :
            <div>
              {
                nodeIds.map((nodeId, index) =>
                  <Link key={uuid4()}
                        className={"node-error-link"}
                        to={""}
                        onClick={event => this.showDetails(event, NodeUtils.getNodeById(nodeId, this.props.currentProcess))}>
                    {nodeId}
                    {index < nodeIds.length - 1 ? separator : null}
                  </Link>)
              }
            </div>
        }
        {
          _.isEmpty(propertiesErrors) ? null :
            <Link key={uuid4()}
                  className={"node-error-link"}
                  to={""}
                  onClick={event => this.showDetails(event, this.props.currentProcess.properties)}>
              {_.isEmpty(nodeErrors) ? null : separator}{"properties"}
            </Link>
        }
      </div>
    </div>

  warnings = (warnings) =>
    _.isEmpty(warnings) ? null :
      <div key={uuid4()}>
        <div className="icon" title="" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}}/>
        {this.warningTips(warnings)}
      </div>

  warningTips = (warnings) =>
    <div className={"warning-tips"}>
      {
        warnings.map(warning =>
          <div key={uuid4()}
               title={warning.error.description}>
            <Link key={uuid4()}
                  className={"node-warning-link"}
                  to={""}
                  onClick={event => this.showDetails(event, NodeUtils.getNodeById(warning.key, this.props.currentProcess))}><span>{warning.key}</span></Link>
            <span>{warning.error.message.replace("${nodeId}", "")}</span>
          </div>)
      }
    </div>

  showDetails = (event, element) => {
    event.preventDefault()
    this.props.actions.displayModalNodeDetails(element)
  }

  className = () =>
    this.props.isHighlighted ? "tipsPanelHighlighted" : "tipsPanel"

  render() {
    return (
      <div id="tipsPanel" className={this.className()}>
        <Scrollbars renderThumbVertical={props => <div key={uuid4()} {...props} className="thumbVertical"/>}
                    hideTracksWhenNotNeeded={true}>
          {this.tipsText()}
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
