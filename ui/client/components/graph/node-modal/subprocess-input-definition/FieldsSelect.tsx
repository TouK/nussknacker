import {isEqual} from "lodash"
import React, {useCallback, useMemo} from "react"
import {Parameter} from "../../../../types"
import MapKey from "../editors/map/MapKey"
import {mandatoryValueValidator} from "../editors/Validators"
import {DndItems} from "./DndItems"
import {FieldsRow} from "./FieldsRow"
import {NodeRowFields} from "./NodeRowFields"
import {TypeSelect} from "./TypeSelect"

export interface Option {
  value: string,
  label: string,
}

interface FieldsSelectProps {
  addField: () => void,
  fields: Parameter[],
  label: string,
  namespace: string,
  onChange: (path: string, value: any) => void,
  options: Option[],
  removeField: (path: string, index: number) => void,
  readOnly?: boolean,

  isMarked: (path: string) => boolean,
  showValidation?: boolean,
}

function FieldsSelect(props: FieldsSelectProps): JSX.Element {
  const {addField, fields, label, onChange, namespace, options, readOnly, removeField, isMarked, showValidation} = props

  const validators = useMemo(() => [mandatoryValueValidator], [])

  const getCurrentOption = useCallback(field => {
    const fallbackValue = {label: field?.typ?.refClazzName, value: field?.typ?.refClazzName}
    const foundValue = options.find((item) => isEqual(field?.typ?.refClazzName, item.value))
    return foundValue || fallbackValue
  }, [options])

  const Item = useCallback(
    ({index, item}: {index: number, item}) => {
      const path = `${namespace}[${index}]`
      return (
        <FieldsRow index={index}>
          <MapKey
            readOnly={readOnly}
            showValidation={showValidation}
            isMarked={isMarked(`${path}.name`)}
            onChange={value => onChange(`${path}.name`, value)}
            value={item.name}
            validators={validators}
            autofocus={false}
          />
          <TypeSelect
            readOnly={readOnly}
            onChange={value => onChange(`${path}.typ.refClazzName`, value)}
            value={getCurrentOption(item)}
            isMarked={isMarked(`${path}.typ.refClazzName`)}
            options={options}
          />
        </FieldsRow>
      )
    },
    [getCurrentOption, isMarked, namespace, onChange, options, readOnly, showValidation, validators],
  )

  const changeOrder = useCallback(value => onChange(namespace, value), [namespace, onChange])

  const items = useMemo(
    () => fields.map((item, index) => ({item, el: <Item key={index} index={index} item={item}/>})),
    [Item, fields],
  )

  return (
    <NodeRowFields
      label={label}
      path={namespace}
      onFieldAdd={addField}
      onFieldRemove={removeField}
      readOnly={readOnly}
    >
      <DndItems disabled={readOnly} items={items} onChange={changeOrder}/>
    </NodeRowFields>
  )
}

export default FieldsSelect
