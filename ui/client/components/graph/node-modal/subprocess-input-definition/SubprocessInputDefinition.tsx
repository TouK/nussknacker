import _ from "lodash"
import React, {useCallback, useMemo} from "react"
import {useSelector} from "react-redux"
import ProcessUtils from "../../../../common/ProcessUtils"
import {getProcessDefinitionData} from "../../../../reducers/selectors/settings"
import {Field} from "../../../../types"
import {MapVariableProps} from "../MapVariable"
import {NodeCommonDetailsDefinition} from "../NodeCommonDetailsDefinition"
import FieldsSelect from "./FieldsSelect"

type Props<F extends Field> = MapVariableProps<F>

export default function SubprocessInputDefinition<F extends Field>(props: Props<F>): JSX.Element {
  const {removeElement, addElement, ...passProps} = props
  const {isMarked, node, onChange, readOnly, showValidation} = passProps

  const definitionData = useSelector(getProcessDefinitionData)
  const typeOptions = useMemo(() => (definitionData?.processDefinition?.typesInformation || []).map(type => ({
    value: type.clazzName.refClazzName,
    label: ProcessUtils.humanReadableType(type.clazzName),
  })), [definitionData?.processDefinition?.typesInformation])

  const orderedTypeOptions = useMemo(() => _.orderBy(typeOptions, (item) => [item.label, item.value], ["asc"]), [typeOptions])

  const defaultTypeOption = useMemo(() => _.find(typeOptions, {label: "String"}) || _.head(typeOptions), [typeOptions])

  const addField = useCallback(() => {
    addElement("parameters", {name: "", typ: {refClazzName: defaultTypeOption.value}})
  }, [addElement, defaultTypeOption.value])

  const fields = useMemo(() => node.parameters || [], [node.parameters])

  return (
    <NodeCommonDetailsDefinition {...passProps}>
      <FieldsSelect
        label="Parameters"
        onChange={onChange}
        addField={addField}
        removeField={removeElement}
        namespace={"parameters"}
        fields={fields}
        options={orderedTypeOptions}
        isMarked={isMarked}
        showValidation={showValidation}
        readOnly={readOnly}
      />
    </NodeCommonDetailsDefinition>
  )
}
