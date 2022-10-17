import React, {useEffect, useState} from "react"
import Field, {FieldType} from "./editors/field/Field"
import {allValid, errorValidator, Validator} from "./editors/Validators"
import {NodeType, NodeValidationError, ProcessDefinitionData} from "../../../types"
import ProcessUtils from "../../../common/ProcessUtils"
import {useDiffMark} from "./PathsToMark"
import {useTranslation} from "react-i18next"

type OutputFieldProps = {
    autoFocus?: boolean,
    value: string,
    fieldProperty: string,
    fieldType: FieldType,
    isEditMode?: boolean,
    readonly?: boolean,
    renderedFieldLabel: JSX.Element,
    onChange: (value: string | boolean) => void,
    showValidation?: boolean,
    validators?: Validator[],
}

function OutputField({
  autoFocus,
  value,
  fieldProperty,
  fieldType,
  isEditMode,
  readonly,
  renderedFieldLabel,
  onChange,
  showValidation,
  validators = [],
}: OutputFieldProps): JSX.Element {
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
      {renderedFieldLabel}
    </Field>
  )
}

const outputVariablePath = "ref.outputVariableNames"

export default function OutputParametersList({
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
  const outputParameters = ProcessUtils.findNodeObjectTypeDefinition(editedNode, processDefinitionData.processDefinition)?.outputParameters
  const [params, setParams] = useState(() => outputParameters.reduce((previousValue, currentValue) => ({
    ...previousValue, [currentValue]: editedNode.ref?.outputVariableNames[currentValue],
  }), {}))

  const {t} = useTranslation()

  useEffect(() => {
    setProperty(outputVariablePath, params)
  }, [params, setProperty])

  outputParameters.filter(paramName => params[paramName] === undefined).forEach(paramName => {
    setParams(prevState => ({...prevState, [paramName]: paramName}))
  })
  
  return outputParameters && outputParameters.length === 0 ?
    null :
    (
      <div className="node-row" key="outputVariableNames">
        <div
          className="node-label" 
          title={t("parameterOutputs.outputsTitle", "Fragment outputs names")}
        >
          {t("parameterOutputs.outputsText", "Outputs names:")}
        </div>
        <div className="node-value">
          <div className="fieldsControl">
            {
              outputParameters.map(paramName => (
                <OutputField
                  key={paramName}
                  isEditMode={isEditMode}
                  showValidation={showValidation}
                  value={params[paramName] === undefined ? paramName : params[paramName]}
                  renderedFieldLabel={renderFieldLabel(paramName)}
                  onChange={value => setParams(prevState => ({...prevState, [paramName]: value}))}
                  fieldType={FieldType.input}
                  fieldProperty={paramName}
                  validators={[errorValidator(fieldErrors || [], `${outputVariablePath}.${paramName}`)]}
                />
              ))
            }
          </div>
        </div>
      </div>
    )
}
