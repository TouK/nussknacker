import Textarea from "react-textarea-autosize"
import React from "react"

export default function TestValue(props) {

  const {testValue, shouldHideTestResults} = props
  const hiddenClassPart = (shouldHideTestResults ? " partly-hidden" : "")

  return (
    <div className={"node-value" + hiddenClassPart}>
      {
        _.get(testValue, "original") ?
          <Textarea className="node-input"
                    readOnly={true}
                    value={testValue.original}/> : null
      }
      <Textarea
        className="node-input"
        readOnly={true}
        value={testValue !== null ? prettyPrint(testValue.pretty) : "null"}
      />
      {shouldHideTestResults ? <div className="fadeout"/> : null}
    </div>
  )

  function prettyPrint(obj) {
    return JSON.stringify(obj, null, 2)
  }
}