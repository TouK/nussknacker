import {css, cx} from "emotion"
import React, {useCallback, useEffect} from "react"
import {useSelector} from "react-redux"
import {getWritableCategories} from "../reducers/selectors/settings"
import {ChangeableValue} from "./ChangeableValue"
import {Validator} from "./graph/node-modal/editors/Validators"
import ValidationLabels from "./modals/ValidationLabels"
import {InputWithFocus, SelectWithFocus} from "./withFocus"

type FormValue = { processId: string, processCategory: string }

interface AddProcessFormProps extends ChangeableValue<FormValue> {
  nameValidators: Validator[],
}

export function AddProcessForm({nameValidators, value, onChange}: AddProcessFormProps): JSX.Element {
  const categories = useSelector(getWritableCategories)

  const onFieldChange = useCallback(
    (field: keyof FormValue, next: string) => onChange({...value, [field]: next}),
    [onChange, value],
  )

  useEffect(() => {
    if (!value.processCategory) {
      onFieldChange("processCategory", categories[0])
    }
  }, [categories, onFieldChange, value.processCategory])

  return (
    <div
      className={cx("modalContentDark", css({
        minWidth: 600,
        paddingTop: 10,
        paddingBottom: 20,
      }))}
    >
      <div className={cx("node-table")}>
        <div className="node-table-body">
          <div className="node-row">
            <div className="node-label">Name</div>
            <div className="node-value">
              <InputWithFocus
                // autoFocus
                type="text"
                id="newProcessId"
                className="node-input"
                value={value.processId}
                onChange={e => onFieldChange("processId", e.target.value)}
              />
              <ValidationLabels validators={nameValidators} values={[value.processId]}/>
            </div>
          </div>
          <div className="node-row">
            <div className="node-label">Category</div>
            <div className="node-value">
              <SelectWithFocus
                id="processCategory"
                className="node-input"
                value={value.processCategory}
                onChange={e => onFieldChange("processCategory", e.target.value)}
              >
                {categories.map((cat, index) => (
                  <option key={index} value={cat}>{cat}</option>
                ))}
              </SelectWithFocus>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
