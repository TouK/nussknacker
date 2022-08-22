/* eslint-disable i18next/no-literal-string */
import {NodeContentMethods, NodeDetailsContentProps3} from "./NodeDetailsContentProps3"
import {SourceSinkCommon} from "./SourceSinkCommon"
import {DisableField} from "./DisableField"
import React, {useCallback} from "react"
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
import {NodeTableBody} from "./NodeDetailsContent/NodeTable"

export type ArrayElement<A extends readonly unknown[]> = A extends readonly (infer E)[] ? E : never

export function Source(props: Pick<NodeDetailsContentProps3,
  | "node"
  | "originalNodeId"
  | "isEditMode"
  | "showValidation"
  | "showSwitch"
  | "editedNode"
  | "findAvailableVariables"
  | "parameterDefinitions"
  | "fieldErrors"> & NodeContentMethods): JSX.Element {
  const {
    isMarked,
    renderFieldLabel,
    setProperty,
    showSwitch,
    fieldErrors,
    findAvailableVariables,
    editedNode,
    parameterDefinitions,
    node,
    isEditMode,
    originalNodeId,
    showValidation,
  } = props
  return (
    <SourceSinkCommon
      node={node}
      originalNodeId={originalNodeId}
      isEditMode={isEditMode}
      showValidation={showValidation}
      showSwitch={showSwitch}
      editedNode={editedNode}
      findAvailableVariables={findAvailableVariables}
      parameterDefinitions={parameterDefinitions}
      fieldErrors={fieldErrors}
      isMarked={isMarked}
      renderFieldLabel={renderFieldLabel}
      setProperty={setProperty}
    />
  )
}

export function Sink(props: Pick<NodeDetailsContentProps3,
  | "node"
  | "originalNodeId"
  | "isEditMode"
  | "showValidation"
  | "showSwitch"
  | "editedNode"
  | "findAvailableVariables"
  | "parameterDefinitions"
  | "fieldErrors"> & NodeContentMethods): JSX.Element {
  const {
    isMarked,
    renderFieldLabel,
    setProperty,
    showSwitch,
    fieldErrors,
    findAvailableVariables,
    editedNode,
    parameterDefinitions,
    node,
    isEditMode,
    originalNodeId,
    showValidation,
  } = props
  return (
    <SourceSinkCommon
      node={node}
      originalNodeId={originalNodeId}
      isEditMode={isEditMode}
      showValidation={showValidation}
      showSwitch={showSwitch}
      editedNode={editedNode}
      findAvailableVariables={findAvailableVariables}
      parameterDefinitions={parameterDefinitions}
      fieldErrors={fieldErrors}
      isMarked={isMarked}
      renderFieldLabel={renderFieldLabel}
      setProperty={setProperty}
    >
      <div>
        <DisableField
          isEditMode={isEditMode}
          showValidation={showValidation}
          editedNode={editedNode}
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
  originalNodeId,
  editedEdges,
  setEditedEdges,
  showValidation,
  editedNode,
  parameterDefinitions,
  showSwitch,
  findAvailableVariables,
}: { isCompareView?: boolean } & NodeContentMethods & Pick<NodeDetailsContentProps3, "editedEdges" | "setEditedEdges" | "isEditMode" | "fieldErrors" | "originalNodeId" | "showValidation" | "editedNode" | "parameterDefinitions" | "showSwitch" | "findAvailableVariables">): JSX.Element {
  return (
    <NodeTableBody>
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
            value={editedEdges}
            onChange={setEditedEdges}
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
    </NodeTableBody>
  )
}

export function EnricherProcessor({
  isMarked,
  renderFieldLabel,
  setProperty,
  ...props
}: Pick<NodeDetailsContentProps3,
  | "showSwitch"
  | "fieldErrors"
  | "findAvailableVariables"
  | "editedNode"
  | "parameterDefinitions"
  | "originalNodeId"
  | "isEditMode"
  | "showValidation"> & NodeContentMethods): JSX.Element {
  const {
    showSwitch,
    fieldErrors,
    findAvailableVariables,
    editedNode,
    parameterDefinitions,
    originalNodeId,
    isEditMode,
    showValidation,
  } = props
  return (
    <NodeTableBody>
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
          <div className="node-block" key={editedNode.id + param.name + index}>
            <ParameterExpressionField
              originalNodeId={originalNodeId}
              isEditMode={isEditMode}
              showValidation={showValidation}
              showSwitch={showSwitch}
              editedNode={editedNode}
              findAvailableVariables={findAvailableVariables}
              parameterDefinitions={parameterDefinitions}
              fieldErrors={fieldErrors}

              isMarked={isMarked}
              renderFieldLabel={renderFieldLabel}
              setProperty={setProperty}
              parameter={param}
              listFieldPath={`service.parameters[${index}]`}
            />
          </div>
        )
      })}
      {editedNode.type === "Enricher" ?
        (
          <NodeField
            isEditMode={isEditMode}
            showValidation={showValidation}
            editedNode={editedNode}

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
      {editedNode.type === "Processor" ?
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
    </NodeTableBody>
  )
}

export function SubprocessInput(props: Pick<NodeDetailsContentProps3,
  | "editedNode"
  | "isEditMode"
  | "showValidation"
  | "processDefinitionData"
  | "fieldErrors"
  | "showSwitch"
  | "findAvailableVariables"
  | "originalNodeId"
  | "parameterDefinitions"> & NodeContentMethods): JSX.Element {
  const {
    isMarked,
    renderFieldLabel,
    setProperty,
    editedNode,
    isEditMode,
    showValidation,
    processDefinitionData,
    fieldErrors,
    showSwitch,
    findAvailableVariables,
    originalNodeId,
    parameterDefinitions,
  } = props
  const setNodeState = useCallback(newParams => setProperty("ref.parameters", newParams), [setProperty])
  return (
    <NodeTableBody>
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
        setNodeState={setNodeState}
        createListField={(param, index) => {
          return (
            <ParameterExpressionField
              originalNodeId={originalNodeId}
              showSwitch={showSwitch}
              findAvailableVariables={findAvailableVariables}
              parameterDefinitions={parameterDefinitions}
              fieldErrors={fieldErrors}

              editedNode={editedNode}
              isEditMode={isEditMode}
              showValidation={showValidation}
              isMarked={isMarked}
              renderFieldLabel={renderFieldLabel}
              setProperty={setProperty}
              parameter={param}
              listFieldPath={`ref.parameters[${index}]`}
            />
          )
        }}
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
    </NodeTableBody>
  )
}

export function JoinCustomNode(props: Pick<NodeDetailsContentProps3,
  | "editedNode"
  | "isEditMode"
  | "showValidation"
  | "parameterDefinitions"
  | "showSwitch"
  | "findAvailableVariables"
  | "fieldErrors"
  | "node"
  | "originalNodeId"
  | "processDefinitionData"> & NodeContentMethods & { testResultsState: StateForSelectTestResults }): JSX.Element {
  const {
    isMarked,
    renderFieldLabel,
    setProperty,
    testResultsState,
    editedNode,
    isEditMode,
    showValidation,
    parameterDefinitions,
    showSwitch,
    findAvailableVariables,
    fieldErrors,
    node,
    originalNodeId,
    processDefinitionData,
  } = props
  return (
    <NodeTableBody>
      <IdField
        editedNode={editedNode}
        isEditMode={isEditMode}
        showValidation={showValidation}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {
        hasOutputVar(node, processDefinitionData) && (
          <NodeField
            editedNode={editedNode}
            isEditMode={isEditMode}
            showValidation={showValidation}
            isMarked={isMarked}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
            fieldType={FieldType.input}
            fieldLabel={"Output variable name"}
            fieldProperty={"outputVar"}
            validators={[errorValidator(fieldErrors || [], "outputVar")]}
          />
        )
      }
      {NodeUtils.nodeIsJoin(editedNode) && (
        <BranchParameters
          node={editedNode}
          isMarked={isMarked}
          showValidation={showValidation}
          showSwitch={showSwitch}
          isEditMode={isEditMode}
          errors={fieldErrors || []}
          parameterDefinitions={parameterDefinitions}
          setNodeDataAt={setProperty}
          testResultsToShow={testResultsState.testResultsToShow}
          findAvailableVariables={findAvailableVariables}
        />
      )}
      {editedNode.parameters?.map((param, index) => {
        return (
          <div className="node-block" key={node.id + param.name + index}>
            <ParameterExpressionField
              originalNodeId={originalNodeId}
              showSwitch={showSwitch}
              findAvailableVariables={findAvailableVariables}
              parameterDefinitions={parameterDefinitions}
              fieldErrors={fieldErrors}

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
    </NodeTableBody>
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
}: Pick<NodeDetailsContentProps3,
  | "variableTypes"
  | "expressionType"
  | "nodeTypingInfo"
  | "showValidation"
  | "fieldErrors"
  | "isEditMode"
  | "editedNode"> & NodeContentMethods & { removeElement: (property: keyof NodeType, index: number) => void, addElement: (...args: any[]) => any }): JSX.Element {
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
}: Pick<NodeDetailsContentProps3,
  | "variableTypes"
  | "isEditMode"
  | "nodeTypingInfo"
  | "showValidation"
  | "fieldErrors"
  | "expressionType"
  | "editedNode"> & NodeContentMethods): JSX.Element {
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
  expressionType,
  findAvailableVariables,
  editedNode,
  setEditedEdges,
  nodeTypingInfo,
  showSwitch,
  isEditMode,
  originalNode,
  parameterDefinitions,
  processDefinitionData,
  fieldErrors,
  originalNodeId,
  editedEdges,
  showValidation,
}: NodeContentMethods & Pick<NodeDetailsContentProps3,
  | "variableTypes"
  | "expressionType"
  | "findAvailableVariables"
  | "editedNode"
  | "setEditedEdges"
  | "nodeTypingInfo"
  | "showSwitch"
  | "isEditMode"
  | "originalNode"
  | "parameterDefinitions"
  | "processDefinitionData"
  | "fieldErrors"
  | "originalNodeId"
  | "editedEdges"
  | "showValidation"> & { isCompareView?: boolean }): JSX.Element {
  const {node: definition} = processDefinitionData.componentGroups?.flatMap(g => g.components).find(c => c.node.type === editedNode.type)
  const currentExpression = originalNode["expression"]
  const currentExprVal = originalNode["exprVal"]
  const exprValValidator = errorValidator(fieldErrors || [], "exprVal")
  const showExpression = definition["expression"] ? !isEqual(definition["expression"], currentExpression) : currentExpression?.expression
  const showExprVal = !exprValValidator.isValid() || definition["exprVal"] ? definition["exprVal"] !== currentExprVal : currentExprVal
  return (
    <NodeTableBody>
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        editedNode={editedNode}

        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {showExpression ?
        (
          <StaticExpressionField
            originalNodeId={originalNodeId}
            isEditMode={isEditMode}
            showValidation={showValidation}
            showSwitch={showSwitch}
            editedNode={editedNode}
            findAvailableVariables={findAvailableVariables}
            parameterDefinitions={parameterDefinitions}
            fieldErrors={fieldErrors}

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
            isEditMode={isEditMode}
            showValidation={showValidation}
            editedNode={editedNode}

            isMarked={isMarked}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
            fieldType={FieldType.input}
            fieldLabel={"exprVal (deprecated)"}
            fieldProperty={"exprVal"}
            validators={[errorValidator(fieldErrors || [], "exprVal")]}
          />
        ) :
        null}
      {!isCompareView ?
        (
          <EdgesDndComponent
            label={"Conditions"}
            nodeId={originalNodeId}
            value={editedEdges}
            onChange={setEditedEdges}
            edgeTypes={[
              {value: EdgeKind.switchNext},
              {value: EdgeKind.switchDefault, onlyOne: true, disabled: true},
            ]}
            ordered
            readOnly={!isEditMode}
            variableTypes={editedNode["exprVal"] ?
              {
                ...variableTypes,
                [editedNode["exprVal"]]: expressionType || nodeTypingInfo && {fields: nodeTypingInfo},
              } :
              variableTypes}
            fieldErrors={fieldErrors || []}
          />
        ) :
        null}
      <DescriptionField
        isEditMode={isEditMode}
        showValidation={showValidation}
        editedNode={editedNode}

        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </NodeTableBody>
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
    <NodeTableBody>
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
    </NodeTableBody>
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
}: Pick<NodeDetailsContentProps3,
  | "isEditMode"
  | "showValidation"
  | "editedNode"
  | "node"
  | "processDefinitionData"
  | "additionalPropertiesConfig"
  | "fieldErrors"
  | "showSwitch"> & NodeContentMethods): JSX.Element {
  const type = node.typeSpecificProperties.type
  //fixme move this configuration to some better place?
  //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
  return (
    <NodeTableBody>
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
        isEditMode={isEditMode}
        showValidation={showValidation}
        editedNode={editedNode}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </NodeTableBody>
  )
}
