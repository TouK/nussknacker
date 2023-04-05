import React from "react"
import ReactSelect from "react-select"
import styles from "../../../../../stylesheets/select.styl"
import {LabeledInputProps} from "./LabeledInput"

export interface Option {
  label: string,
  value: boolean,
}

export interface LabeledSelectProps extends Pick<LabeledInputProps, "children" | "autoFocus" | "isMarked" | "onChange" | "readOnly"> {
  value: Option,
  onChange: (Option) => void,
  options: Option[],
}

export default function Select(props: LabeledSelectProps): JSX.Element {
  const {children, autoFocus, value, options, isMarked, readOnly, onChange} = props

  return (
    <div className="node-row">
      {children}
      <div className={`node-value${isMarked ? " marked" : ""}`}>
        <ReactSelect
          classNamePrefix={styles.nodeValueSelect}
          onChange={onChange}
          value={value}
          options={options}
          autoFocus={autoFocus}
          isDisabled={readOnly}
        />
      </div>
    </div>
  )
}
