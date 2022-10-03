import React, {useCallback, useState} from "react"
import Select from "react-select"
import styles from "../../../../stylesheets/select.styl"
import {NodeValue} from "./NodeValue"

export interface Option {
  value: string,
  label: string,
}

interface RowSelectProps {
  onChange: (value: string) => void,
  options: Option[],
  readOnly?: boolean,
  isMarked?: boolean,
  value: Option,
}

//to prevent dragging on specified elements, see https://stackoverflow.com/a/51911875
const preventDragProps = {
  draggable: true,
  onDragStart: (event: React.DragEvent) => {
    event.preventDefault()
    event.stopPropagation()
  },
}

function useCaptureEsc() {
  const [captureEsc, setCaptureEsc] = useState(false)

  //prevent modal close by esc
  const preventEsc = useCallback((event: React.KeyboardEvent) => {
    if (captureEsc && event.key === "Escape") {
      event.stopPropagation()
    }
  }, [captureEsc])

  return {setCaptureEsc, preventEsc}
}

export function TypeSelect({
  isMarked,
  options,
  readOnly,
  value,
  onChange,
}: RowSelectProps): JSX.Element {
  const {setCaptureEsc, preventEsc} = useCaptureEsc()

  return (
    <NodeValue className="field" marked={isMarked} onKeyDown={preventEsc}>
      <Select
        className="node-value node-value-select node-value-type-select"
        classNamePrefix={styles.nodeValueSelect}
        isDisabled={readOnly}
        maxMenuHeight={190}
        onMenuOpen={() => setCaptureEsc(true)}
        onMenuClose={() => setCaptureEsc(false)}
        options={options}
        value={value}
        onChange={(option) => onChange(option.value)}
        styles={{menuPortal: (base) => ({...base, zIndex: 9999})}}
        menuPortalTarget={document.body}
      />
    </NodeValue>
  )
}

