import {isEmpty} from "lodash"
import React from "react"
import {useSelector} from "react-redux"
import {Link} from "react-router-dom"
import NodeUtils from "../../components/graph/NodeUtils"
import {getProcessUnsavedNewName} from "../../reducers/selectors/graph"
import {Process} from "../../types"

interface NodeErrorsLinkSectionProps {
  nodeIds: $TodoType[],
  message: $TodoType,
  showDetails: $TodoType,
  currentProcess: Process,
  className?: string,
}

export default function NodeErrorsLinkSection(props: NodeErrorsLinkSectionProps): JSX.Element {
  const {nodeIds, message, showDetails, currentProcess, className} = props
  const name = useSelector(getProcessUnsavedNewName)

  return !isEmpty(nodeIds) && (
    <div className={className}>
      <ErrorHeader message={message}/>
      {
        nodeIds.map((nodeId, index) => {
          const details = nodeId === "properties" ?
            NodeUtils.getProcessProperties(currentProcess, name) :
            NodeUtils.getNodeById(nodeId, currentProcess)
          return (
            <NodeErrorLink
              key={nodeId}
              onClick={event => showDetails(event, details)}
              nodeId={nodeId}
              addSeparator={index < nodeIds.length - 1}
            />
          )
        })
      }
    </div>
  )
}

const ErrorHeader = (props) => {
  const {message, className} = props

  return <span className={className}>{message}</span>
}

const NodeErrorLink = (props) => {
  const {onClick, nodeId, addSeparator} = props

  const separator = ", "

  return (
    <Link key={nodeId} className={"node-error-link"} to={""} onClick={onClick}>
      {nodeId}
      {addSeparator ? separator : null}
    </Link>
  )
}
