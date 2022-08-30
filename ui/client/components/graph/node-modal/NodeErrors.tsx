import React from "react"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import NodeTip from "./NodeTip"
import {css} from "@emotion/css"
import {NodeValidationError} from "../../../types"

//TODO: remove style overrides, cleanup
export default function NodeErrors(props: { errors: NodeValidationError[], message: string }): JSX.Element {
  const {errors = [], message: errorMessage} = props

  if (!errors.length) {
    return null
  }

  return (
    <div className={css({
      display: "flex",
      margin: 0,
      "&&& .node-tip": {
        margin: ".5em 1em .5em 0",
      },
      "&&& .node-error": {
        padding: 0,
        margin: "0 0 .5em 0",
      },
    })}
    >
      <NodeTip title={errorMessage} icon={InlinedSvgs.tipsError}/>
      <div>
        {errors.map(({description, fieldName, message}, index) => (
          <div
            className="node-error"
            key={index}
            title={description}
          >{message + (fieldName ? ` (field: ${fieldName})` : "")}</div>
        ))}
      </div>
    </div>
  )
}
