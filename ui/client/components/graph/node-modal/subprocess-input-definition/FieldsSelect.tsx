import {isEqual} from "lodash"
import React, {PropsWithChildren, useCallback, useMemo} from "react"
import {Parameter} from "../../../../types"
import MapKey from "../editors/map/MapKey"
import {mandatoryValueValidator} from "../editors/Validators"
import {DndItems} from "./DndItems"
import {FieldsRow} from "./FieldsRow"
import {Items} from "./Items"
import {NodeRowFields} from "./NodeRowFields"
import {RowSelect} from "./RowSelect"

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

function Input({value, onChange2}: {value: any, onChange2: (e) => void}) {
  return <input type="text" value={value} onChange={onChange2}/>
}

function FieldsSelect(props: FieldsSelectProps): JSX.Element {
  const {addField, fields, label, onChange, namespace, options, readOnly, removeField, isMarked, showValidation} = props

  const validators = useMemo(() => [mandatoryValueValidator], [])

  const getCurrentOption = useCallback(field => {
    const fallbackValue = {label: field?.typ?.refClazzName, value: field?.typ?.refClazzName}
    const foundValue = options.find((item) => isEqual(field?.typ?.refClazzName, item.value))
    return foundValue || fallbackValue
  }, [options])

  const Component = useMemo(
    () => function Component({index, item, children}: PropsWithChildren<{index: number, item}>) {
      const path = useMemo(() => `${namespace}[${index}]`, [index])

      const changeName = useCallback(value => {
        onChange(`${path}.name`, value)
      }, [path])

      const changeValue = useCallback(value => {
        onChange(`${path}.typ.refClazzName`, value)
      }, [path])

      return (
        <>
          <FieldsRow index={index}>
            <MapKey
              readOnly={readOnly}
              showValidation={showValidation}
              isMarked={isMarked(`${path}.name`)}
              onChange={changeName}
              value={item.name}
              validators={validators}
              autofocus={false}
            />
            <RowSelect
              index={index}
              changeValue={changeValue}
              value={getCurrentOption(item)}
              isMarked={() => isMarked(`${path}.typ.refClazzName`)}
              options={options}
            />
          </FieldsRow>
          {children}
        </>
      )
    },
    [getCurrentOption, isMarked, namespace, onChange, options, readOnly, showValidation, validators],
  )

  const onChange1 = useCallback(value => onChange(namespace, value), [namespace, onChange])

  const items = useMemo(() => fields.map((item, index) => ({item, el: <Component key={index} index={index} item={item}/>})), [Component, fields])

  return (
    <NodeRowFields
      label={label}
      path={namespace}
      onFieldAdd={addField}
      onFieldRemove={removeField}
      readOnly={readOnly}
    >
      <DndItems items={items} onChange={onChange1}/>
      <Items items={items}/>
    </NodeRowFields>
  )
}

export default FieldsSelect
