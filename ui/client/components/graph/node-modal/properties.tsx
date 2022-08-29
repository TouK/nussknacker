import {NodeType, NodeValidationError, ProcessDefinitionData} from "../../../types"
import {useSelector} from "react-redux"
import {getAdditionalPropertiesConfig} from "./NodeDetailsContent/selectors"
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
  const type = node.typeSpecificProperties.type
  //fixme move this configuration to some better place?
  //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
  const sorted = useMemo(() => sortBy(Object.entries(additionalPropertiesConfig), ([name]) => name), [additionalPropertiesConfig])
  return (
    <NodeTableBody>
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {node.isSubprocess ?
        (
          <NodeField
            isEditMode={isEditMode}
            showValidation={showValidation}
            node={node}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
            fieldType={FieldType.input}
            fieldLabel={"Documentation url"}
            fieldProperty={"typeSpecificProperties.docsUrl"}
            validators={[errorValidator(fieldErrors || [], "docsUrl")]}
            autoFocus
          />
        ) :
        type === "StreamMetaData" ?
          (
            <>
              <NodeField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                fieldType={FieldType.input}
                fieldLabel={"Parallelism"}
                fieldProperty={"typeSpecificProperties.parallelism"}
                validators={[errorValidator(fieldErrors || [], "parallelism")]}
                autoFocus
              />
              <NodeField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                fieldType={FieldType.input}
                fieldLabel={"Checkpoint interval in seconds"}
                fieldProperty={"typeSpecificProperties.checkpointIntervalInSeconds"}
                validators={[errorValidator(fieldErrors || [], "checkpointIntervalInSeconds")]}
              />
              <NodeField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                fieldType={FieldType.checkbox}
                fieldLabel={"Spill state to disk"}
                fieldProperty={"typeSpecificProperties.spillStateToDisk"}
                validators={[errorValidator(fieldErrors || [], "spillStateToDisk")]}
              />
              <NodeField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                fieldType={FieldType.checkbox}
                fieldLabel={"Should use async interpretation"}
                fieldProperty={"typeSpecificProperties.useAsyncInterpretation"}
                validators={[errorValidator(fieldErrors || [], "useAsyncInterpretation")]}
                defaultValue={processDefinitionData?.defaultAsyncInterpretation}
              />
            </>
          ) :
          type === "LiteStreamMetaData" ?
            (
              <NodeField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                fieldType={FieldType.input}
                fieldLabel={"Parallelism"}
                fieldProperty={"typeSpecificProperties.parallelism"}
                validators={[errorValidator(fieldErrors || [], "parallelism")]}
                autoFocus
              />
            ) :
            (
              <NodeField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                fieldType={FieldType.input}
                fieldLabel={"Query path"}
                fieldProperty={"typeSpecificProperties.path"}
                validators={[errorValidator(fieldErrors || [], "path")]}
              />
            )
      }
      {sorted.map(([propName, propConfig]) => (
        <AdditionalProperty
          key={propName}
          showSwitch={showSwitch}
          showValidation={showValidation}
          propertyName={propName}
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
