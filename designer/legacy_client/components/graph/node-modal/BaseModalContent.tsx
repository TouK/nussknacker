import PropTypes from "prop-types"
import React, {PropsWithChildren} from "react"
import {UnknownFunction} from "../../../types/common"
import {InputWithFocus} from "../../withFocus"
import NodeErrors from "./NodeErrors"
import {EdgeKind, NodeValidationError} from "../../../types"
import {EdgeTypeSelect} from "./EdgeTypeSelect"
import {NodeTable, NodeTableBody} from "./NodeDetailsContent/NodeTable"

interface Props {
  edge?,
  edgeErrors?: NodeValidationError[],
  readOnly?: boolean,
  isMarked?: UnknownFunction,
  changeEdgeTypeValue?: UnknownFunction,
}

BaseModalContent.propTypes = {
  edge: PropTypes.object,
  edgeErrors: PropTypes.array,
  readOnly: PropTypes.bool,
  isMarked: PropTypes.func,
  changeEdgeTypeValue: PropTypes.func,
}

export default function BaseModalContent(props: PropsWithChildren<Props>): JSX.Element {
  const {edge, edgeErrors, readOnly, isMarked, changeEdgeTypeValue} = props

  const types = [
    {value: EdgeKind.switchDefault, label: "Default"},
    {value: EdgeKind.switchNext, label: "Condition"},
  ]

  return (
    <NodeTable>
      <NodeErrors errors={edgeErrors} message={"Edge has errors"}/>
      <NodeTableBody>
        <div className="node-row">
          <div className="node-label">From</div>
          <div className="node-value"><InputWithFocus
            readOnly={true}
            type="text"
            className="node-input"
            value={edge.from}
          />
          </div>
        </div>
        <div className="node-row">
          <div className="node-label">To</div>
          <div className="node-value"><InputWithFocus
            readOnly={true}
            type="text"
            className="node-input"
            value={edge.to}
          /></div>
        </div>
        <div className="node-row">
          <div className="node-label">Type</div>
          <div className={`node-value${isMarked("edgeType.type") ? " marked" : ""}`}>
            <EdgeTypeSelect
              id="processCategory"
              readOnly={readOnly}
              edge={edge}
              onChange={changeEdgeTypeValue}
              options={types}
            />
          </div>
        </div>
        {props.children}
      </NodeTableBody>
    </NodeTable>
  )
}
