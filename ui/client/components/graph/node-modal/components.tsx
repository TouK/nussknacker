/* eslint-disable i18next/no-literal-string */
import {NodeContentMethods, NodeDetailsContentProps3} from "./NodeDetailsContentProps3"
import {SourceSinkCommon} from "./SourceSinkCommon"
import {DisableField} from "./DisableField"
import React from "react"
import {EdgeKind, NodeType} from "../../../types"
import SubprocessInputDefinition from "./subprocess-input-definition/SubprocessInputDefinition"
import {IdField} from "./IdField"
import {NodeField} from "./NodeField"
import {FieldType} from "./editors/field/Field"
import {errorValidator} from "./editors/Validators"
import {isEqual, sortBy} from "lodash"
import AdditionalProperty from "./AdditionalProperty"
import {DescriptionField} from "./DescriptionField"
import {StaticExpressionField} from "./StaticExpressionField"
import {EdgesDndComponent} from "./EdgesDndComponent"
import {serviceParameters} from "./NodeDetailsContent/helpers"
import {ParameterExpressionField} from "./ParameterExpressionField"
import {DEFAULT_EXPRESSION_ID} from "../../../common/graph/constants"
import Variable from "./Variable"
import ProcessUtils from "../../../common/ProcessUtils"
import SubprocessOutputDefinition from "./SubprocessOutputDefinition"
import {StateForSelectTestResults} from "../../../common/TestResultUtils"
import {hasOutputVar} from "./NodeDetailsContentUtils"
import NodeUtils from "../NodeUtils"
import BranchParameters from "./BranchParameters"
import ParameterList from "./ParameterList"
import {InputWithFocus} from "../../withFocus"
import MapVariable from "./MapVariable"

export type ArrayElement<A extends readonly unknown[]> = A extends readonly (infer E)[] ? E : never

export function Source({
  isMarked,
  renderFieldLabel,
  setProperty,
  ...props
}: NodeDetailsContentProps3 & NodeContentMethods): JSX.Element {
  return (
    <SourceSinkCommon
      {...props}
      isMarked={isMarked}
      renderFieldLabel={renderFieldLabel}
      setProperty={setProperty}
    />
  )
}

export function Sink({
  isMarked,
  renderFieldLabel,
  setProperty,
  ...props
}: NodeDetailsContentProps3 & NodeContentMethods): JSX.Element {
  return (
    <SourceSinkCommon
      {...props}
      isMarked={isMarked}
      renderFieldLabel={renderFieldLabel}
      setProperty={setProperty}
    >
      <div>
        <DisableField
          {...props}
          isMarked={isMarked}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      </div>
    </SourceSinkCommon>
  )
}

interface AddRemoveMethods {
  addElement: (...args: any[]) => any,
  removeElement: (property: keyof NodeType, index: number) => void,
}

type SubprocessInputDefinitionProps =
  AddRemoveMethods
  & NodeContentMethods
  & Pick<NodeDetailsContentProps3, "isEditMode" | "fieldErrors" | "editedNode" | "showValidation" | "variableTypes">

export function SubprocessInputDef({
  isMarked,
  renderFieldLabel,
  setProperty,
  addElement,
  removeElement,
  isEditMode,
  fieldErrors,
  editedNode,
  showValidation,
  variableTypes,
}: SubprocessInputDefinitionProps): JSX.Element {
  return (
    <SubprocessInputDefinition
      addElement={addElement}
      onChange={setProperty}
      node={editedNode}
      isMarked={isMarked}
      readOnly={!isEditMode}
      removeElement={removeElement}
      showValidation={showValidation}
      renderFieldLabel={renderFieldLabel}
      errors={fieldErrors || []}
      variableTypes={variableTypes}
    />
  )
}

type SubprocessOutputDefinitionProps =
  SubprocessInputDefinitionProps
  & Pick<NodeDetailsContentProps3, "expressionType" | "nodeTypingInfo">

export function SubprocessOutputDef(
  {
    renderFieldLabel,
    removeElement,
    setProperty,
    addElement,
    isMarked,
    variableTypes,
    expressionType,
    nodeTypingInfo,
    showValidation,
    fieldErrors,
    isEditMode,
    editedNode,
  }: SubprocessOutputDefinitionProps
): JSX.Element {
  return (
    <SubprocessOutputDefinition
      renderFieldLabel={renderFieldLabel}
      removeElement={removeElement}
      onChange={setProperty}
      node={editedNode}
      addElement={addElement}
      isMarked={isMarked}
      readOnly={!isEditMode}
      showValidation={showValidation}
      errors={fieldErrors || []}

      variableTypes={variableTypes}
      expressionType={expressionType || nodeTypingInfo && {fields: nodeTypingInfo}}
    />
  )
}

export function Filter({
  isMarked,
  renderFieldLabel,
  setProperty,
  isCompareView,
  isEditMode,
  fieldErrors,
  edges,
  originalNodeId,
  setEdgesState,
  showValidation,
  editedNode,
  parameterDefinitions,
  showSwitch,
  findAvailableVariables,
}: { isCompareView?: boolean } & NodeContentMethods & Pick<NodeDetailsContentProps3, "edges" | "setEdgesState" | "isEditMode" | "fieldErrors" | "originalNodeId" | "showValidation" | "editedNode" | "parameterDefinitions" | "showSwitch" | "findAvailableVariables">): JSX.Element {
  return (
    <div className="node-table-body">
      <IdField
        isMarked={isMarked}
        isEditMode={isEditMode}
        showValidation={showValidation}
        editedNode={editedNode}
        setProperty={setProperty}
        renderFieldLabel={renderFieldLabel}

      />
      <StaticExpressionField
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
        fieldLabel={"Expression"}
        parameterDefinitions={parameterDefinitions}
        showSwitch={showSwitch}
        findAvailableVariables={findAvailableVariables}
        showValidation={showValidation}
        fieldErrors={fieldErrors}
        originalNodeId={originalNodeId}
        isEditMode={isEditMode}
        editedNode={editedNode}

      />
      <DisableField
        editedNode={editedNode}
        isEditMode={isEditMode}
        showValidation={showValidation}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {!isCompareView ?
        (
          <EdgesDndComponent
            label={"Outputs"}
            nodeId={originalNodeId}
            value={edges}
            onChange={(nextEdges) => setEdgesState(nextEdges)}
            edgeTypes={[
              {value: EdgeKind.filterTrue, onlyOne: true},
              {value: EdgeKind.filterFalse, onlyOne: true},
            ]}
            readOnly={!isEditMode}
            fieldErrors={fieldErrors || []}
          />
        ) :
        null}
      <DescriptionField
        editedNode={editedNode}
        isEditMode={isEditMode}
        showValidation={showValidation}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </div>
  )
}

export function EnricherProcessor({
  isMarked,
  renderFieldLabel,
  setProperty,
  ...props
}: NodeDetailsContentProps3 & NodeContentMethods): JSX.Element {
  const {fieldErrors, node, editedNode, isEditMode, showValidation} = props
  return (
    <div className="node-table-body">
      <IdField
        isMarked={isMarked}
        isEditMode={isEditMode}
        showValidation={showValidation}
        editedNode={editedNode}
        setProperty={setProperty}
        renderFieldLabel={renderFieldLabel}
      />
      {serviceParameters(editedNode).map((param, index) => {
        return (
          <div className="node-block" key={node.id + param.name + index}>
            <ParameterExpressionField
              {...props}
              isMarked={isMarked}
              renderFieldLabel={renderFieldLabel}
              setProperty={setProperty}
              parameter={param}
              listFieldPath={`service.parameters[${index}]`}
            />
          </div>
        )
      })}
      {node.type === "Enricher" ?
        (
          <NodeField
            {...props}
            isMarked={isMarked}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
            fieldType={FieldType.input}
            fieldLabel={"Output"}
            fieldProperty={"output"}
            validators={[errorValidator(fieldErrors || [], "output")]}
          />
        ) :
        null}
      {node.type === "Processor" ?
        (
          <DisableField
            editedNode={editedNode}
            isEditMode={isEditMode}
            showValidation={showValidation}
            isMarked={isMarked}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
          />
        ) :
        null}
      <DescriptionField
        editedNode={editedNode}
        isEditMode={isEditMode}
        showValidation={showValidation}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </div>
  )
}

export function SubprocessInput({
  isMarked,
  renderFieldLabel,
  setProperty,
  editedNode,
  isEditMode,
  showValidation,
  ...props
}: NodeDetailsContentProps3 & NodeContentMethods): JSX.Element {
  const {processDefinitionData} = props
  return (
    <div className="node-table-body">
      <IdField
        editedNode={editedNode}
        isEditMode={isEditMode}
        showValidation={showValidation}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      <DisableField
        editedNode={editedNode}
        isEditMode={isEditMode}
        showValidation={showValidation}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      <ParameterList
        processDefinitionData={processDefinitionData}
        editedNode={editedNode}
        savedNode={editedNode}
        setNodeState={newParams => setProperty("ref.parameters", newParams)}
        createListField={(param, index) => (
          <ParameterExpressionField
            {...props}
            editedNode={editedNode}
            isEditMode={isEditMode}
            showValidation={showValidation}
            isMarked={isMarked}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
            parameter={param}
            listFieldPath={`ref.parameters[${index}]`}
          />
        )}
        createReadOnlyField={params => (
          <div className="node-row">
            {renderFieldLabel(params.name)}
            <div className="node-value">
              <InputWithFocus
                type="text"
                className="node-input"
                value={params.expression.expression}
                disabled={true}
              />
            </div>
          </div>
        )}
      />
      <DescriptionField
        editedNode={editedNode}
        isEditMode={isEditMode}
        showValidation={showValidation}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </div>
  )
}

export function JoinCustomNode({
  isMarked,
  renderFieldLabel,
  setProperty,
  testResultsState,
  editedNode,
  isEditMode,
  showValidation,
  ...props
}: NodeDetailsContentProps3 & NodeContentMethods & { testResultsState: StateForSelectTestResults }): JSX.Element {
  return (
    <div className="node-table-body">
      <IdField
        editedNode={editedNode}
        isEditMode={isEditMode}
        showValidation={showValidation}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {
        hasOutputVar(props.node, props.processDefinitionData) && (
          <NodeField
            {...props}
            editedNode={editedNode}
            isEditMode={isEditMode}
            showValidation={showValidation}
            isMarked={isMarked}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
            fieldType={FieldType.input}
            fieldLabel={"Output variable name"}
            fieldProperty={"outputVar"}
            validators={[errorValidator(props.fieldErrors || [], "outputVar")]}
          />
        )
      }
      {NodeUtils.nodeIsJoin(editedNode) && (
        <BranchParameters
          node={editedNode}
          isMarked={isMarked}
          showValidation={showValidation}
          showSwitch={props.showSwitch}
          isEditMode={isEditMode}
          errors={props.fieldErrors || []}
          parameterDefinitions={props.parameterDefinitions}
          setNodeDataAt={setProperty}
          testResultsToShow={testResultsState.testResultsToShow}
          findAvailableVariables={props.findAvailableVariables}
        />
      )}
      {editedNode.parameters?.map((param, index) => {
        return (
          <div className="node-block" key={props.node.id + param.name + index}>
            <ParameterExpressionField
              {...props}
              editedNode={editedNode}
              isEditMode={isEditMode}
              showValidation={showValidation}
              isMarked={isMarked}
              renderFieldLabel={renderFieldLabel}
              setProperty={setProperty}
              parameter={param}
              listFieldPath={`parameters[${index}]`}
            />
          </div>
        )
      })}
      <DescriptionField
        editedNode={editedNode}
        isEditMode={isEditMode}
        showValidation={showValidation}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </div>
  )
}

export function VariableBuilder({
  renderFieldLabel,
  removeElement,
  setProperty,
  addElement,
  isMarked,
  variableTypes,
  expressionType,
  nodeTypingInfo,
  showValidation,
  fieldErrors,
  isEditMode,
  editedNode,
}: NodeDetailsContentProps3 & NodeContentMethods & { removeElement: (property: keyof NodeType, index: number) => void, addElement: (...args: any[]) => any }): JSX.Element {
  return (
    <MapVariable
      renderFieldLabel={renderFieldLabel}
      removeElement={removeElement}
      onChange={setProperty}
      node={editedNode}
      addElement={addElement}
      isMarked={isMarked}
      readOnly={!isEditMode}
      showValidation={showValidation}
      variableTypes={variableTypes}
      errors={fieldErrors || []}
      expressionType={expressionType || nodeTypingInfo && {fields: nodeTypingInfo}}
    />
  )
}

export function VariableDef({
  renderFieldLabel,
  setProperty,
  isMarked,
  variableTypes,
  isEditMode,
  nodeTypingInfo,
  showValidation,
  fieldErrors = [],
  expressionType,
  editedNode,
}: NodeDetailsContentProps3 & NodeContentMethods): JSX.Element {
  const varExprType = expressionType || (nodeTypingInfo || {})[DEFAULT_EXPRESSION_ID]
  const inferredVariableType = ProcessUtils.humanReadableType(varExprType)
  return (
    <Variable
      renderFieldLabel={renderFieldLabel}
      onChange={setProperty}
      node={editedNode}
      isMarked={isMarked}
      readOnly={!isEditMode}
      showValidation={showValidation}
      variableTypes={variableTypes}
      errors={fieldErrors}
      inferredVariableType={inferredVariableType}
    />
  )
}

export function Switch({
  isMarked,
  renderFieldLabel,
  setProperty,
  isCompareView,
  variableTypes,
  ...props
}: NodeContentMethods & NodeDetailsContentProps3 & { isCompareView?: boolean }): JSX.Element {
  const {node: definition} = props.processDefinitionData.componentGroups?.flatMap(g => g.components).find(c => c.node.type === props.editedNode.type)
  const currentExpression = props.originalNode["expression"]
  const currentExprVal = props.originalNode["exprVal"]
  const exprValValidator = errorValidator(props.fieldErrors || [], "exprVal")
  const showExpression = definition["expression"] ? !isEqual(definition["expression"], currentExpression) : currentExpression?.expression
  const showExprVal = !exprValValidator.isValid() || definition["exprVal"] ? definition["exprVal"] !== currentExprVal : currentExprVal
  return (
    <div className="node-table-body">
      <IdField
        {...props}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {showExpression ?
        (
          <StaticExpressionField
            {...props}
            isMarked={isMarked}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
            fieldLabel={"Expression (deprecated)"}
          />
        ) :
        null}
      {showExprVal ?
        (
          <NodeField
            {...props}
            isMarked={isMarked}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
            fieldType={FieldType.input}
            fieldLabel={"exprVal (deprecated)"}
            fieldProperty={"exprVal"}
            validators={[errorValidator(props.fieldErrors || [], "exprVal")]}
          />
        ) :
        null}
      {!isCompareView ?
        (
          <EdgesDndComponent
            label={"Conditions"}
            nodeId={props.originalNodeId}
            value={props.edges}
            onChange={(nextEdges) => props.setEdgesState(nextEdges)}
            edgeTypes={[
              {value: EdgeKind.switchNext},
              {value: EdgeKind.switchDefault, onlyOne: true, disabled: true},
            ]}
            ordered
            readOnly={!props.isEditMode}
            variableTypes={props.editedNode["exprVal"] ?
              {
                ...variableTypes,
                [props.editedNode["exprVal"]]: props.expressionType || props.nodeTypingInfo && {fields: props.nodeTypingInfo},
              } :
              variableTypes}
            fieldErrors={props.fieldErrors || []}
          />
        ) :
        null}
      <DescriptionField
        {...props}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </div>
  )
}

export function Split({
  isMarked,
  renderFieldLabel,
  setProperty,
  isEditMode,
  showValidation,
  editedNode,
}: Pick<NodeDetailsContentProps3, "isEditMode" | "showValidation" | "editedNode"> & NodeContentMethods): JSX.Element {
  return (
    <div className="node-table-body">
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        editedNode={editedNode}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      <DescriptionField
        isEditMode={isEditMode}
        showValidation={showValidation}
        editedNode={editedNode}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </div>
  )
}

export function Properties({
  isMarked,
  renderFieldLabel,
  setProperty,
  isEditMode,
  showValidation,
  editedNode,
  node,
  processDefinitionData,
  additionalPropertiesConfig,
  fieldErrors,
  showSwitch,
  ...props
}: NodeDetailsContentProps3 & NodeContentMethods): JSX.Element {
  const type = node.typeSpecificProperties.type
  //fixme move this configuration to some better place?
  //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
  return (
    <div className="node-table-body">
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        editedNode={editedNode}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {node.isSubprocess ?
        (
          <NodeField
            {...props}
            isEditMode={isEditMode}
            showValidation={showValidation}
            editedNode={editedNode}
            isMarked={isMarked}
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
                {...props}
                isEditMode={isEditMode}
                showValidation={showValidation}
                editedNode={editedNode}
                isMarked={isMarked}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                fieldType={FieldType.input}
                fieldLabel={"Parallelism"}
                fieldProperty={"typeSpecificProperties.parallelism"}
                validators={[errorValidator(fieldErrors || [], "parallelism")]}
                autoFocus
              />
              <NodeField
                {...props}
                isEditMode={isEditMode}
                showValidation={showValidation}
                editedNode={editedNode}
                isMarked={isMarked}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                fieldType={FieldType.input}
                fieldLabel={"Checkpoint interval in seconds"}
                fieldProperty={"typeSpecificProperties.checkpointIntervalInSeconds"}
                validators={[errorValidator(fieldErrors || [], "checkpointIntervalInSeconds")]}
              />
              <NodeField
                {...props}
                isEditMode={isEditMode}
                showValidation={showValidation}
                editedNode={editedNode}
                isMarked={isMarked}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                fieldType={FieldType.checkbox}
                fieldLabel={"Spill state to disk"}
                fieldProperty={"typeSpecificProperties.spillStateToDisk"}
                validators={[errorValidator(fieldErrors || [], "spillStateToDisk")]}
              />
              <NodeField
                {...props}
                isEditMode={isEditMode}
                showValidation={showValidation}
                editedNode={editedNode}
                isMarked={isMarked}
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
                {...props}
                isEditMode={isEditMode}
                showValidation={showValidation}
                editedNode={editedNode}
                isMarked={isMarked}
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
                {...props}
                isEditMode={isEditMode}
                showValidation={showValidation}
                editedNode={editedNode}
                isMarked={isMarked}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                fieldType={FieldType.input}
                fieldLabel={"Query path"}
                fieldProperty={"typeSpecificProperties.path"}
                validators={[errorValidator(fieldErrors || [], "path")]}
              />
            )
      }
      {sortBy(Object.entries(additionalPropertiesConfig), ([name]) => name).map(([propName, propConfig]) => (
        <AdditionalProperty
          key={propName}
          showSwitch={showSwitch}
          showValidation={showValidation}
          propertyName={propName}
          propertyConfig={propConfig}
          propertyErrors={fieldErrors || []}
          onChange={setProperty}
          renderFieldLabel={renderFieldLabel}
          editedNode={editedNode}
          readOnly={!isEditMode}
        />
      ))}
      <DescriptionField
        {...props}
        isEditMode={isEditMode}
        showValidation={showValidation}
        editedNode={editedNode}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </div>
  )
}
