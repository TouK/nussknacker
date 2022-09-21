import React, {useEffect, useState} from "react"
import Field, {FieldType} from "./editors/field/Field"
import {allValid, errorValidator, Validator} from "./editors/Validators"
import {NodeType, NodeValidationError, ProcessDefinitionData} from "../../../types"
import ProcessUtils from "../../../common/ProcessUtils"
import {useDiffMark} from "./PathsToMark"
import {useTranslation} from "react-i18next"

type OutputFieldProps<N extends string, V> = {
    autoFocus?: boolean,
    value: string,
    fieldLabel: string,
    fieldProperty: N,
    fieldType: FieldType,
    isEditMode?: boolean,
    readonly?: boolean,
    renderFieldLabel: (paramName: string) => JSX.Element,
    onChange: (value: string | boolean) => void,
    showValidation?: boolean,
    validators?: Validator[],
}

function OutputField<N extends string, V>({
  autoFocus,
  value,
  fieldLabel,
  fieldProperty,
  fieldType,
  isEditMode,
  readonly,
  renderFieldLabel,
  onChange,
  showValidation,
  validators = [],
}: OutputFieldProps<N, V>): JSX.Element {
  const readOnly = !isEditMode || readonly
  const className = !showValidation || allValid(validators, [value]) ? "node-input" : "node-input node-input-with-error"
  const [isMarked] = useDiffMark()

  return (
    <Field
      type={fieldType}
      isMarked={isMarked(`${fieldProperty}`)}
      readOnly={readOnly}
      showValidation={showValidation}
      autoFocus={autoFocus}
      className={className}
      validators={validators}
      value={value}
      onChange={onChange}
    >
      {renderFieldLabel(fieldLabel)}
    </Field>
  )
}

const outputParamsPath = "ref.outputParameters"

export default function ParameterOutputList({
  editedNode,
  processDefinitionData,
  fieldErrors,
  isEditMode,
  showValidation,
  renderFieldLabel,
  setProperty,
}: {
    editedNode: NodeType,
    processDefinitionData: ProcessDefinitionData,
    renderFieldLabel: (paramName: string) => JSX.Element,
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
    fieldErrors?: NodeValidationError[],
    showValidation?: boolean,
    isEditMode?: boolean,
}): JSX.Element {
  const parameters = ProcessUtils.findNodeObjectTypeDefinition(editedNode, processDefinitionData.processDefinition)?.outputParameters
  const [params, setParams] = useState(() => parameters.reduce((previousValue, currentValue) => ({
    ...previousValue, [currentValue]: editedNode.ref?.outputParameters[currentValue],
  }), {}))

  const {t} = useTranslation()

  useEffect(() => {
    setProperty(outputParamsPath, params)
  }, [params, setProperty])

  return parameters && parameters.length === 0 ?
    null :
    (
      <div className="node-row" key="outputParameters">
        <div
          className="node-label" 
          title={t("parameterOutputs.outputsTitle", "Fragment outputs names")}
        >
          {t("parameterOutputs.outputsText", "Outputs names:")}
        </div>
        <div className="node-value">
          <div className="fieldsControl">
            {
              parameters.map(paramName => (
                <OutputField
                  key={paramName}
                  isEditMode={isEditMode}
                  showValidation={showValidation}
                  value={params[paramName]}
                  renderFieldLabel={renderFieldLabel}
                  onChange={value => setParams(prevState => ({...prevState, [paramName]: value}))}
                  fieldType={FieldType.input}
                  fieldLabel={paramName}
                  fieldProperty={paramName}
                  validators={[errorValidator(fieldErrors || [], `${outputParamsPath}.${paramName}`)]}
                />
              ))
            }
          </div>
        </div>
      </div>
    )
}
