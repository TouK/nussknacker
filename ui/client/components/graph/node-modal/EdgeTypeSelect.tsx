import {Edge, EdgeKind} from "../../../types"
import {SelectWithFocus} from "../../withFocus"
import React from "react"

interface Props {
  id?: string,
  readOnly?: boolean,
  edge: Edge,
  onChange: (value: string) => void,
  options: { value: EdgeKind, label: string }[],
}

export function EdgeTypeSelect(props: Props): JSX.Element {
  const {readOnly, edge, onChange, id, options} = props
  return (
    <SelectWithFocus
      id={id}
      disabled={readOnly}
      className="node-input"
      value={edge.edgeType.type}
      onChange={(e) => onChange(e.target.value)}
    >
      {options.map(o => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </SelectWithFocus>
  )
}
