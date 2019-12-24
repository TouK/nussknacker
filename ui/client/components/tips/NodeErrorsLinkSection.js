import React from 'react'
import {v4 as uuid4} from "uuid"
import {Link} from "react-router-dom"
import NodeUtils from '../../components/graph/NodeUtils'

export default function NodeErrorsLinkSection(props) {

  const {nodeIds, message, showDetails, currentProcess, className} = props

  return !_.isEmpty(nodeIds) &&
    <div className={className}>
      <ErrorHeader message={message}/>
      {
        nodeIds.map((nodeId, index) =>
          <NodeErrorLink
            key={uuid4()}
            onClick={nodeId === "properties" ?
              event => showDetails(event, currentProcess.properties) :
              event => showDetails(event, NodeUtils.getNodeById(nodeId, currentProcess))
            }
            nodeId={nodeId}
            addSeparator={index < (nodeIds.length - 1)}
          />
        )
      }
    </div>
}


const ErrorHeader = (props) => {
  const {message, className} = props

  return <span className={className}>{message}</span>
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