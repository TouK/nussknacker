import {v4 as uuid4} from "uuid"
import {Link} from "react-router-dom"
import React from "react"

export default function NodeErrorLink(props) {

  const {onClick, nodeId, addSeparator} = props

  const separator = ', '

  return (
    <Link key={uuid4()} className={"node-error-link"} to={""} onClick={onClick}>
      {nodeId}
      {addSeparator ? separator : null}
    </Link>
  )
}