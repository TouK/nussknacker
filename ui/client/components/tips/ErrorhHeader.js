import React from "react"

export function ErrorHeader(props) {

  const {message} = props

  return <span className={"error-tip-header"}>{message}</span>
}