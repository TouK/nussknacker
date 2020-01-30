import PropTypes from "prop-types"
import React from "react"
import {v4 as uuid4} from "uuid"
import InlinedSvgs from "../../assets/icons/InlinedSvgs"
import HeaderIcon from "./HeaderIcon"
import NodeErrorsLinkSection from "./NodeErrorsLinkSection"

export default class Errors extends React.Component {

  static propTypes = {
    errors: PropTypes.object.isRequired,
  }

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
      <HeaderIcon className={"icon"} icon={InlinedSvgs.tipsError}/>

  errorTips = (errors) => {
    const globalErrors = errors.globalErrors
    const nodeErrors = errors.invalidNodes
    const propertiesErrors = errors.processPropertiesErrors

    return _.isEmpty(nodeErrors) && _.isEmpty(propertiesErrors) && _.isEmpty(globalErrors) ? null :
      <div className={"node-error-section"}>
        <div>
          {this.globalErrorsTips(globalErrors)}
          {this.nodeErrorsTips(propertiesErrors, nodeErrors)}
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
        {(suffix ? `${suffix  }: ` : "") + error.message + (error.fieldName ? `(${error.fieldName})` : "")}
    </span>

  nodeErrorsTips = (propertiesErrors, nodeErrors) => {
    const {showDetails, currentProcess} = this.props
    const nodeIds = Object.keys(nodeErrors)
    const looseNodeIds = nodeIds.filter(nodeId => nodeErrors[nodeId].some(error => error.message === "Loose node"))
    const invalidEndNodeIds = nodeIds.filter(nodeId => nodeErrors[nodeId].some(error => error.message === "Invalid end of process"))
    const otherNodeErrorIds = _.difference(nodeIds, _.concat(looseNodeIds, invalidEndNodeIds))
    const errorsOnTop = this.errorsOnTopPresent(otherNodeErrorIds, propertiesErrors)

    return (
      <div className={"node-error-tips"}>
        <div className={"node-error-links"}>
          <NodeErrorsLinkSection
            nodeIds={_.concat(otherNodeErrorIds, _.isEmpty(propertiesErrors) ? [] : "properties")}
            message={"Errors in: "}
            showDetails={showDetails}
            currentProcess={currentProcess}
          />
          <NodeErrorsLinkSection
            nodeIds={looseNodeIds}
            message={"Loose nodes: "}
            showDetails={showDetails}
            currentProcess={currentProcess}
            className={errorsOnTop ? "error-secondary-container" : null}
          />
          <NodeErrorsLinkSection
            nodeIds={invalidEndNodeIds}
            message={"Invalid end of process: "}
            showDetails={showDetails}
            currentProcess={currentProcess}
            className={errorsOnTop ? "error-secondary-container" : null}
          />
        </div>
      </div>
    )
  }

  errorsOnTopPresent(otherNodeErrorIds, propertiesErrors) {
    return !_.isEmpty(otherNodeErrorIds) || !_.isEmpty(propertiesErrors)
  }
}

Errors.defaultProps = {
  errors: {
    globalErrors: [],
    invalidNodes: {},
    processPropertiesErrors: [],
  },
}
