import {Edge, EdgeKind} from "../../../types"
import {SelectWithFocus} from "../../withFocus"
import React from "react"

export interface EdgeTypeOption {
  value: EdgeKind,
  label: string,
  disabled?: boolean,
}

interface Props {
  id?: string,
  readOnly?: boolean,
  edge: Edge,
  onChange: (value: string) => void,
  options: EdgeTypeOption[],
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
        <option key={o.value} value={o.value} disabled={o.disabled}>{o.label}</option>
      ))}
    </SelectWithFocus>
  )
}
