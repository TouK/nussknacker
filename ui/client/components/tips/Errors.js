import InlinedSvgs from "../../assets/icons/InlinedSvgs"
import NodeUtils from "../graph/NodeUtils"
import HeaderIcon from "./HeaderIcon"
import React from "react"
import {Link} from "react-router-dom"
import {v4 as uuid4} from "uuid";

export default class Errors extends React.Component {

  render() {
    const {errors} = this.props
    return (
      <div key={uuid4()} className={"error-tips"}>
        {this.headerIcon(errors)}
        {this.errorTips(errors)}
      </div>
    )
  }

  headerIcon = (errors) =>
    _.isEmpty(errors.globalErrors) && _.isEmpty(errors.invalidNodes) && _.isEmpty(errors.processPropertiesErrors) ? null :
      <HeaderIcon icon={InlinedSvgs.tipsError}/>

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

  nodeErrorsTips = (nodeIds, propertiesErrors, separator, nodeErrors) => {
    const {showDetails, currentProcess} = this.props
    return <div className={"node-error-tips"}>
      {
        _.isEmpty(nodeIds) && _.isEmpty(propertiesErrors) ? null :
          <span className={"error-tip-header"}>Errors in</span>
      }
      <div className={"node-error-links"}>
        {
          _.isEmpty(nodeIds) ? null :
            nodeIds.map((nodeId, index) =>
              <Link key={uuid4()}
                    className={"node-error-link"}
                    to={""}
                    onClick={event => showDetails(event, NodeUtils.getNodeById(nodeId, currentProcess))}>
                {nodeId}
                {(index < nodeIds.length - 1) || !_.isEmpty(propertiesErrors) ? separator : null}
              </Link>)
        }
        {
          _.isEmpty(propertiesErrors) ? null :
            <Link key={uuid4()}
                  className={"node-error-link"}
                  to={""}
                  onClick={event => showDetails(event, currentProcess.properties)}>
              {"properties"}
            </Link>
        }
      </div>
    </div>
  }

}