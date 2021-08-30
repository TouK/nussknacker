import React, {useCallback, useState} from "react"
import Select from "react-select"
import styles from "../../../../stylesheets/select.styl"
import {Option} from "./FieldsSelect"
import {NodeValue} from "./NodeValue"

interface RowSelectProps {
  index: number,
  changeValue: (value: string) => void,
  options: Option[],
  readOnly?: boolean,
  isMarked: (i: number) => boolean,
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

export function RowSelect({
  index,
  isMarked,
  options,
  readOnly,
  value,
  changeValue,
}: RowSelectProps): JSX.Element {
  const {setCaptureEsc, preventEsc} = useCaptureEsc()

  return (
    <>
      <NodeValue className="field" marked={isMarked(index)} onKeyDown={preventEsc}>
        <Select
          className="node-value node-value-select node-value-type-select"
          classNamePrefix={styles.nodeValueSelect}
          isDisabled={readOnly}
          maxMenuHeight={190}
          onMenuOpen={() => setCaptureEsc(true)}
          onMenuClose={() => setCaptureEsc(false)}
          options={options}
          value={value}
          onChange={(option) => changeValue(option.value)}
          styles={{menuPortal: (base) => ({...base, zIndex: 9999})}}
          menuPortalTarget={document.body}
        />
      </NodeValue>
    </>
  )
}

