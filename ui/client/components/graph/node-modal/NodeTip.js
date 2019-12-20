import React from "react"

export default function NodeTip(props) {

  const {title, icon} = props

  return <div className="node-tip" title={title} dangerouslySetInnerHTML={{__html: icon}}/>
}