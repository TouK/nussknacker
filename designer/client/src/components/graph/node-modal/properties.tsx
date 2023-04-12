import {NodeType, NodeValidationError, ProcessDefinitionData} from "../../../types"
import {useSelector} from "react-redux"
import {getAdditionalPropertiesConfig, getTypeSpecificPropertiesConfig} from "./NodeDetailsContent/selectors"
import React, {useMemo} from "react"
import {sortBy} from "lodash"
import {NodeTableBody} from "./NodeDetailsContent/NodeTable"
import {IdField} from "./IdField"
import {NodeField} from "./NodeField"
import {FieldType} from "./editors/field/Field"
import {errorValidator} from "./editors/Validators"
import AdditionalProperty from "./AdditionalProperty"
import {DescriptionField} from "./DescriptionField"

export function Properties({
  fieldErrors,
  isEditMode,
  node,
  processDefinitionData,
  renderFieldLabel,
  setProperty,
  showSwitch,
  showValidation,
}: {
  isEditMode?: boolean,
  node: NodeType,
  processDefinitionData?: ProcessDefinitionData,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showSwitch?: boolean,
  fieldErrors?: NodeValidationError[],
  showValidation?: boolean,
}): JSX.Element {
  const additionalPropertiesConfig = useSelector(getAdditionalPropertiesConfig)
  const typeSpecificPropertiesConfig = Object.entries(useSelector(getTypeSpecificPropertiesConfig))
  const type = node.typeSpecificProperties.type
  //fixme move this configuration to some better place?
  //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
  const additionalPropertiesSorted = useMemo(() => sortBy(Object.entries(additionalPropertiesConfig), ([name]) => name), [additionalPropertiesConfig])
  console.log(typeSpecificPropertiesConfig)
  return (
    <NodeTableBody>
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
        additionalValidators={[errorValidator(fieldErrors || [], "id")]}

      />
      {typeSpecificPropertiesConfig.map(([propName, propConfig]) => (
          <AdditionalProperty
              key={propName}
              showSwitch={showSwitch}
              showValidation={showValidation}
              propertyName={propName}
              propertyPathPrefix={"typeSpecificProperties"}
              propertyConfig={propConfig}
              propertyErrors={fieldErrors || []}
              onChange={setProperty}
              renderFieldLabel={renderFieldLabel}
              editedNode={node}
              readOnly={!isEditMode}
          />
      ))}
      {additionalPropertiesSorted.map(([propName, propConfig]) => (
        <AdditionalProperty
          key={propName}
          showSwitch={showSwitch}
          showValidation={showValidation}
          propertyName={propName}
          propertyPathPrefix={"additionalFields.properties"}
          propertyConfig={propConfig}
          propertyErrors={fieldErrors || []}
          onChange={setProperty}
          renderFieldLabel={renderFieldLabel}
          editedNode={node}
          readOnly={!isEditMode}
        />
      ))}
      <DescriptionField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </NodeTableBody>
  )
}
