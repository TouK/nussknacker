/* eslint-disable i18next/no-literal-string */
import ExpressionField from "./editors/expression/ExpressionField"
import {findParamDefinitionByName} from "./FieldLabel"
import React from "react"
import {NodeType, Parameter} from "../../../types"
import Field, {FieldType} from "./editors/field/Field"
import {allValid, mandatoryValueValidator, Validator} from "./editors/Validators"
import {get} from "lodash"
import {refParameters} from "./NodeDetailsContent/helpers"
import {NodeDetailsContentProps3} from "./NodeDetailsContent3"
import {useTestResults} from "./TestResultsWrapper"

export interface CompFunctions {
  isMarked: (path?: string) => boolean,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
}

interface StaticExpressionFieldProps extends CompFunctions, NodeDetailsContentProps3 {
  fieldLabel: string,
}

//this is for "static" fields like expressions in filters, switches etc.
export function StaticExpressionField({
  fieldLabel, isMarked, renderFieldLabel, setProperty, ...props
}: StaticExpressionFieldProps): JSX.Element {
  const fieldName = "expression"
  const expressionProperty = "expression"
  const testResultsState = useTestResults()

  return (
    <ExpressionField
      fieldName={fieldName}
      fieldLabel={fieldLabel}
      exprPath={`${expressionProperty}`}
      isEditMode={props.isEditMode}
      editedNode={props.editedNode}
      isMarked={isMarked}
      showValidation={props.showValidation}
      showSwitch={props.showSwitch}
      parameterDefinition={findParamDefinitionByName(props.parameterDefinitions, fieldName)}
      setNodeDataAt={setProperty}
      testResultsToShow={testResultsState.testResultsToShow}
      renderFieldLabel={renderFieldLabel}
      variableTypes={props.findAvailableVariables(props.originalNodeId, undefined)}
      errors={props.fieldErrors || []}
    />
  )
}

interface ParameterExpressionField extends CompFunctions, NodeDetailsContentProps3 {
  parameter: Parameter,
  listFieldPath: string,
}

//this is for "dynamic" parameters in sources, sinks, services etc.
export function ParameterExpressionField({
  parameter,
  listFieldPath,
  isMarked, renderFieldLabel, setProperty,
  ...props
}: ParameterExpressionField): JSX.Element {
  const expressionProperty = "expression"
  const testResultsState = useTestResults()

  return (
    <ExpressionField
      fieldName={parameter.name}
      fieldLabel={parameter.name}
      exprPath={`${listFieldPath}.${expressionProperty}`}
      isEditMode={props.isEditMode}
      editedNode={props.editedNode}
      isMarked={isMarked}
      showValidation={props.showValidation}
      showSwitch={props.showSwitch}
      parameterDefinition={findParamDefinitionByName(props.parameterDefinitions, parameter.name)}
      setNodeDataAt={setProperty}
      testResultsToShow={testResultsState.testResultsToShow}
      renderFieldLabel={renderFieldLabel}
      variableTypes={props.findAvailableVariables(props.originalNodeId, props.parameterDefinitions?.find(p => p.name === parameter.name))}
      errors={props.fieldErrors || []}
    />
  )
}

export function IdField({
  isMarked,
  isEditMode,
  showValidation,
  editedNode,
  setProperty,
  renderFieldLabel,
}: CompFunctions & NodeDetailsContentProps3): JSX.Element {
  const validators = [mandatoryValueValidator]

  return (
    <Field
      type={FieldType.input}
      isMarked={isMarked("id")}
      showValidation={showValidation}
      onChange={(newValue) => setProperty("id", newValue.toString())}
      readOnly={!isEditMode}
      className={!showValidation || allValid(validators, [editedNode.id]) ? "node-input" : "node-input node-input-with-error"}
      validators={validators}
      value={editedNode.id}
      autoFocus
    >
      {renderFieldLabel("Name")}

    </Field>
  )
}

export const CreateField = <K extends keyof NodeType, T extends NodeType[K]>(props: {
  fieldType: FieldType,
  fieldLabel: string,
  fieldProperty: K,
  autoFocus?: boolean,
  readonly?: boolean,
  defaultValue?: T,
  validators?: Validator[],
} & CompFunctions & Pick<NodeDetailsContentProps3, "editedNode" | "isEditMode" | "showValidation">): JSX.Element => {
  const {
    fieldType,
    fieldLabel,
    fieldProperty,
    autoFocus,
    readonly,
    defaultValue,
    validators = [],
    isMarked, renderFieldLabel, setProperty,
    editedNode, isEditMode, showValidation,
  } = props

  const readOnly = !isEditMode || readonly
  const value: T = get(editedNode, fieldProperty, null) ?? defaultValue
  const className = !showValidation || allValid(validators, [value]) ? "node-input" : "node-input node-input-with-error"
  const onChange = (newValue) => setProperty(fieldProperty, newValue, defaultValue)
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
export const DescriptionField = ({
  isMarked, renderFieldLabel, setProperty,
  ...props
}: CompFunctions & NodeDetailsContentProps3): JSX.Element => {
  return (
    <CreateField
      {...props}
      {...{isMarked, renderFieldLabel, setProperty}}
      fieldType={FieldType.plainTextarea}
      fieldLabel={"Description"}
      fieldProperty={"additionalFields.description"}
    />
  )
}

export const SourceSinkCommon = ({
  children,
  isMarked, renderFieldLabel, setProperty,
  ...props
}: { children?: JSX.Element } & CompFunctions & NodeDetailsContentProps3): JSX.Element => {
  return (
    <div className="node-table-body">
      <IdField
        {...props}
        {...{isMarked, renderFieldLabel, setProperty}}
      />
      {refParameters(props.editedNode).map((param, index) => (
        <div className="node-block" key={props.node.id + param.name + index}>
          <ParameterExpressionField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
            parameter={param}
            listFieldPath={`ref.parameters[${index}]`}
          />
        </div>
      ))}
      {children}
      <DescriptionField
        {...props}
        {...{isMarked, renderFieldLabel, setProperty}}
      />
    </div>
  )
}
