import InlinedSvgs from "../../assets/icons/InlinedSvgs"
import NodeUtils from "../graph/NodeUtils"
import HeaderIcon from "./HeaderIcon"
import React from "react"
import {v4 as uuid4} from "uuid"
import PropTypes from "prop-types"
import {Link} from "react-router-dom"

export default class Errors extends React.Component {

  static propTypes = {
    errors: PropTypes.object.isRequired
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
        {(suffix ? suffix + ": " : '') + error.message + (error.fieldName ? `(${error.fieldName})` : "")}
    </span>

  nodeErrorsTips = (propertiesErrors, nodeErrors) => {
    const {showDetails, currentProcess} = this.props
    const nodeIds = Object.keys(nodeErrors)
    const looseNodeIds = nodeIds.filter(nodeId => nodeErrors[nodeId].some(error => error.message === "Loose node"))
    const otherNodeErrorIds = _.difference(nodeIds, looseNodeIds)

    return (
      <div className={"node-error-tips"}>
        {_.isEmpty(otherNodeErrorIds) && _.isEmpty(propertiesErrors) ? null : <ErrorHeader message={"Errors in: "}/>}
        <div className={"node-error-links"}>
          {
            !_.isEmpty(nodeIds) && otherNodeErrorIds.map((nodeId, index) =>
              <NodeErrorLink
                nodeId={nodeId}
                onClick={event => showDetails(event, NodeUtils.getNodeById(nodeId, currentProcess))}
                addSeparator={(index < nodeIds.length - 1) || !_.isEmpty(propertiesErrors)}
              />
            )
          }
          {
            !_.isEmpty(propertiesErrors) &&
            <NodeErrorLink
              onClick={event => showDetails(event, currentProcess.properties)}
              nodeId={"properties"}
            />
          }
          {
            !_.isEmpty(looseNodeIds) &&
            <React.Fragment>
              <ErrorHeader message={"Loose nodes: "}/>
              {
                looseNodeIds.map((nodeId, index) =>
                  <NodeErrorLink
                    onClick={event => showDetails(event, NodeUtils.getNodeById(nodeId, currentProcess))}
                    nodeId={nodeId}
                    addSeparator={index < (looseNodeIds.length - 1)}
                  />
                )
              }
            </React.Fragment>
          }
        </div>
      </div>
    )
  }
}

Errors.defaultProps = {
  errors: {
    globalErrors: [],
    invalidNodes: {},
    processPropertiesErrors: []
  }
}

const ErrorHeader = (props) => {
  const {message} = props

  return <span className={"error-tip-header"}>{message}</span>
}

const NodeErrorLink = (props) => {
  const {onClick, nodeId, addSeparator} = props

  const separator = ', '

  return (
    <Link key={uuid4()} className={"node-error-link"} to={""} onClick={onClick}>
      {nodeId}
      {addSeparator ? separator : null}
    </Link>
  )
}