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

  tipText = () => {
    if (ProcessUtils.hasNoErrorsNorWarnings(this.props.currentProcess)) {
      return (<div key={uuid4()}>{this.validTip()}</div>)
    } else {
      const errors = (this.props.currentProcess.validationResult || {}).errors
      const errorTips = this.errorTips(errors)

      const warnings = (this.props.currentProcess.validationResult || {}).warnings
      const warningTips = this.warningTips(ProcessUtils.extractInvalidNodes(warnings.invalidNodes))

      return _.concat(errorTips, warningTips)
    }
  }

  errorTips = (errors) =>
    <div key={uuid4()} className={"error-tips"}>
      {this.headerIcon(errors)}
      {this.nodeErrors(errors)}
    </div>

  headerIcon = (errors) =>
    _.isEmpty(errors.globalErrors) && _.isEmpty(errors.invalidNodes) && _.isEmpty(errors.processPropertiesErrors) ? null :
      <div key={uuid4()} className="icon" title="" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsError}}/>

  nodeErrors = (errors) => {
    const globalErrors = errors.globalErrors || []
    const nodeErrors = errors.invalidNodes || {}
    const propertiesErrors = errors.processPropertiesErrors || []
    const nodeIds = Object.keys(nodeErrors)
    const separator = ', '

    return _.isEmpty(nodeErrors) && _.isEmpty(propertiesErrors) && _.isEmpty(globalErrors) ? null :
      <div key={uuid4()} className={"node-error-section"}>
        <div>
          <div>
            {
              globalErrors.map((error, idx) => this.printGlobalError(error, null))
            }
          </div>
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
        </div>
      </div>
  }

  printGlobalError = (error, suffix) => {
    return (
      <span key={uuid4()} title={error.description}>
        {(suffix ? suffix + ": " : '') + error.message + (error.fieldName ? `(${error.fieldName})` : "")}
      </span>
    )
  }

  warningTips = (warnings) => {
    return (
      _.isEmpty(warnings) ? null :
        <div>
          <div key={uuid4()} className="icon" title="" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}}/>
          <div className={"warning-tips"}>
            {
              warnings.map(warning =>
                <div key={uuid4()} title={warning.error.description}>
                  <Link key={uuid4()}
                        className={"node-warning-link"}
                        to={""}
                        onClick={event => this.showDetails(event, NodeUtils.getNodeById(warning.key, this.props.currentProcess))}><span>{warning.key}</span></Link>
                  <span>{warning.error.message.replace("${nodeId}", "")}</span>
                </div>)
            }
          </div>
        </div>
    )
  }

  showDetails = (event, element) => {
    event.preventDefault()
    this.props.actions.displayModalNodeDetails(element)
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

  className = () =>
    this.props.isHighlighted ? "tipsPanelHighlighted" : "tipsPanel"

  render() {
    return (
      <div id="tipsPanel" className={this.className()}>
        <Scrollbars renderThumbVertical={props => <div key={uuid4()} {...props} className="thumbVertical"/>}
                    hideTracksWhenNotNeeded={true}>
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
