import React from "react"

export default function NodeTip(props) {

  const {title, icon, className} = props

  return <div className={`node-tip ${  className ? className : null}`}
              title={title}
              dangerouslySetInnerHTML={{__html: icon}}
  />
}