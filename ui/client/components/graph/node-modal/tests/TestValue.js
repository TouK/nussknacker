import Textarea from "react-textarea-autosize"
import React from "react"

export default function TestValue(props) {

  const {value, shouldHideTestResults} = props
  const hiddenClassPart = (shouldHideTestResults ? " partly-hidden" : "")

  return (
    <div className={`node-value${  hiddenClassPart}`}>
      {
        _.get(value, "original") ?
          <Textarea className="node-input"
                    readOnly={true}
                    value={value.original}/> : null
      }
      <Textarea
        className="node-input"
        readOnly={true}
        value={value !== null ? prettyPrint(value.pretty) : "null"}
      />
      {shouldHideTestResults ? <div className="fadeout"/> : null}
    </div>
  )

  function prettyPrint(obj) {
    return JSON.stringify(obj, null, 2)
  }
}