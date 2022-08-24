/* eslint-disable i18next/no-literal-string */
import {NodeContentMethods, NodeDetailsContentProps3} from "./NodeDetailsContentProps3"
import {SourceSinkCommon} from "./SourceSinkCommon"
import {DisableField} from "./DisableField"
import React, {useCallback, useMemo} from "react"
import {EdgeKind, NodeType, TypedObjectTypingResult, TypingInfo, TypingResult, VariableTypes} from "../../../types"
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
import Variable from "./Variable"
import ProcessUtils from "../../../common/ProcessUtils"
import SubprocessOutputDefinition from "./SubprocessOutputDefinition"
import NodeUtils from "../NodeUtils"
import BranchParameters from "./BranchParameters"
import ParameterList from "./ParameterList"
import {InputWithFocus} from "../../withFocus"
import MapVariable from "./MapVariable"
import {NodeTableBody} from "./NodeDetailsContent/NodeTable"
import {useSelector} from "react-redux"
import {getAdditionalPropertiesConfig, getExpressionType, getNodeTypingInfo} from "./NodeDetailsContent/selectors"
import {useTestResults} from "./TestResultsWrapper"
import {useDiffMark} from "./PathsToMark"
import {RootState} from "../../../reducers"

export const DEFAULT_EXPRESSION_ID = "$expression"

function getTypingResult(expressionType: TypedObjectTypingResult, nodeTypingInfo: TypingInfo): TypedObjectTypingResult | TypingResult {
  return expressionType || nodeTypingInfo?.[DEFAULT_EXPRESSION_ID]
}

function getNodeExpressionType(expressionType: TypedObjectTypingResult, nodeTypingInfo: TypingInfo): Pick<TypedObjectTypingResult, "fields"> {
  return {fields: expressionType?.fields || nodeTypingInfo}
}

export type ArrayElement<A extends readonly unknown[]> = A extends readonly (infer E)[] ? E : never

export function Source({
  renderFieldLabel,
  setProperty,
  showSwitch,
  fieldErrors,
  findAvailableVariables,
  node,
  parameterDefinitions,
  isEditMode,
  originalNodeId,
  showValidation,
}: Pick<NodeDetailsContentProps3,
  | "originalNodeId"
  | "showSwitch"
  | "findAvailableVariables"
  | "parameterDefinitions"
  | "fieldErrors"> & NodeContentMethods): JSX.Element {
  return (
    <SourceSinkCommon
      originalNodeId={originalNodeId}
      isEditMode={isEditMode}
      showValidation={showValidation}
      showSwitch={showSwitch}
      node={node}
      findAvailableVariables={findAvailableVariables}
      parameterDefinitions={parameterDefinitions}
      fieldErrors={fieldErrors}
      renderFieldLabel={renderFieldLabel}
      setProperty={setProperty}
    />
  )
}

export function Sink({
  renderFieldLabel,
  setProperty,
  showSwitch,
  fieldErrors,
  findAvailableVariables,
  node,
  parameterDefinitions,
  isEditMode,
  originalNodeId,
  showValidation,
}: Pick<NodeDetailsContentProps3,
  | "originalNodeId"
  | "showSwitch"
  | "findAvailableVariables"
  | "parameterDefinitions"
  | "fieldErrors"> & NodeContentMethods): JSX.Element {
  return (
    <SourceSinkCommon
      originalNodeId={originalNodeId}
      isEditMode={isEditMode}
      showValidation={showValidation}
      showSwitch={showSwitch}
      node={node}
      findAvailableVariables={findAvailableVariables}
      parameterDefinitions={parameterDefinitions}
      fieldErrors={fieldErrors}
      renderFieldLabel={renderFieldLabel}
      setProperty={setProperty}
    >
      <div>
        <DisableField
          isEditMode={isEditMode}
          showValidation={showValidation}
          node={node}
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
  & Pick<NodeDetailsContentProps3, "fieldErrors" | "originalNodeId">
  & { variableTypes?: VariableTypes }

export function SubprocessInputDef({
  renderFieldLabel,
  setProperty,
  addElement,
  removeElement,
  isEditMode,
  fieldErrors,
  node,
  showValidation,
  variableTypes,
}: SubprocessInputDefinitionProps): JSX.Element {
  return (
    <SubprocessInputDefinition
      addElement={addElement}
      onChange={setProperty}
      node={node}
      readOnly={!isEditMode}
      removeElement={removeElement}
      showValidation={showValidation}
      renderFieldLabel={renderFieldLabel}
      errors={fieldErrors}
      variableTypes={variableTypes}
    />
  )
}

export interface NodeTypingInfo {
  expressionType: TypedObjectTypingResult,
  nodeTypingInfo: TypingInfo,
}

export function SubprocessOutputDef(
  {
    renderFieldLabel,
    removeElement,
    setProperty,
    addElement,
    variableTypes,
    showValidation,
    fieldErrors,
    isEditMode,
    node,
    originalNodeId,
  }: SubprocessInputDefinitionProps
): JSX.Element {
  const expressionType = useSelector((state: RootState) => getExpressionType(state)(originalNodeId))
  const nodeTypingInfo = useSelector((state: RootState) => getNodeTypingInfo(state)(originalNodeId))

  return (
    <SubprocessOutputDefinition
      renderFieldLabel={renderFieldLabel}
      removeElement={removeElement}
      onChange={setProperty}
      node={node}
      addElement={addElement}
      readOnly={!isEditMode}
      showValidation={showValidation}
      errors={fieldErrors || []}

      variableTypes={variableTypes}
      expressionType={getNodeExpressionType(expressionType, nodeTypingInfo)}
    />
  )
}

export function Filter({
  renderFieldLabel,
  setProperty,
  isEditMode,
  fieldErrors,
  originalNodeId,
  edges,
  setEditedEdges,
  showValidation,
  node,
  parameterDefinitions,
  showSwitch,
  findAvailableVariables,
}: NodeContentMethods & Pick<NodeDetailsContentProps3,
  | "edges"
  | "setEditedEdges"
  | "fieldErrors"
  | "originalNodeId"
  | "parameterDefinitions"
  | "showSwitch"
  | "findAvailableVariables">): JSX.Element {
  const [, isCompareView] = useDiffMark()

  return (
    <NodeTableBody>
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
        setProperty={setProperty}
        renderFieldLabel={renderFieldLabel}

      />
      <StaticExpressionField
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
        node={node}

      />
      <DisableField
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {!isCompareView ?
        (
          <EdgesDndComponent
            label={"Outputs"}
            nodeId={originalNodeId}
            value={edges}
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
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </NodeTableBody>
  )
}

export function EnricherProcessor({
  renderFieldLabel,
  setProperty,
  showSwitch,
  fieldErrors,
  findAvailableVariables,
  node,
  parameterDefinitions,
  originalNodeId,
  isEditMode,
  showValidation,
}: Pick<NodeDetailsContentProps3,
  | "showSwitch"
  | "fieldErrors"
  | "findAvailableVariables"
  | "parameterDefinitions"
  | "originalNodeId"> & NodeContentMethods): JSX.Element {
  return (
    <NodeTableBody>
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
        setProperty={setProperty}
        renderFieldLabel={renderFieldLabel}
      />
      {serviceParameters(node).map((param, index) => {
        return (
          <div className="node-block" key={node.id + param.name + index}>
            <ParameterExpressionField
              originalNodeId={originalNodeId}
              isEditMode={isEditMode}
              showValidation={showValidation}
              showSwitch={showSwitch}
              node={node}
              findAvailableVariables={findAvailableVariables}
              parameterDefinitions={parameterDefinitions}
              fieldErrors={fieldErrors}

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
            isEditMode={isEditMode}
            showValidation={showValidation}
            node={node}

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
            node={node}
            isEditMode={isEditMode}
            showValidation={showValidation}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
          />
        ) :
        null}
      <DescriptionField
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </NodeTableBody>
  )
}

export function SubprocessInput(props: Pick<NodeDetailsContentProps3,
  | "processDefinitionData"
  | "fieldErrors"
  | "showSwitch"
  | "findAvailableVariables"
  | "originalNodeId"
  | "parameterDefinitions"> & NodeContentMethods): JSX.Element {
  const {
    renderFieldLabel,
    setProperty,
    node,
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
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      <DisableField
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      <ParameterList
        processDefinitionData={processDefinitionData}
        editedNode={node}
        savedNode={node}
        setNodeState={setNodeState}
        createListField={(param, index) => {
          return (
            <ParameterExpressionField
              originalNodeId={originalNodeId}
              showSwitch={showSwitch}
              findAvailableVariables={findAvailableVariables}
              parameterDefinitions={parameterDefinitions}
              fieldErrors={fieldErrors}

              node={node}
              isEditMode={isEditMode}
              showValidation={showValidation}
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
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </NodeTableBody>
  )
}

export function JoinCustomNode({
  renderFieldLabel,
  setProperty,
  node,
  isEditMode,
  showValidation,
  parameterDefinitions,
  showSwitch,
  findAvailableVariables,
  fieldErrors,
  originalNodeId,
  processDefinitionData,
}: Pick<NodeDetailsContentProps3,
  | "parameterDefinitions"
  | "showSwitch"
  | "findAvailableVariables"
  | "fieldErrors"
  | "originalNodeId"
  | "processDefinitionData"> & NodeContentMethods): JSX.Element {
  const testResultsState = useTestResults()
  const hasOutputVar = useMemo((): boolean => {
      return !!ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)?.returnType || !!node.outputVar
    },
    [node, processDefinitionData.processDefinition])

  return (
    <NodeTableBody>
      <IdField
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {
        hasOutputVar && (
          <NodeField
            node={node}
            isEditMode={isEditMode}
            showValidation={showValidation}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
            fieldType={FieldType.input}
            fieldLabel={"Output variable name"}
            fieldProperty={"outputVar"}
            validators={[errorValidator(fieldErrors || [], "outputVar")]}
          />
        )
      }
      {NodeUtils.nodeIsJoin(node) && (
        <BranchParameters
          node={node}
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
      {node.parameters?.map((param, index) => {
        return (
          <div className="node-block" key={node.id + param.name + index}>
            <ParameterExpressionField
              originalNodeId={originalNodeId}
              showSwitch={showSwitch}
              findAvailableVariables={findAvailableVariables}
              parameterDefinitions={parameterDefinitions}
              fieldErrors={fieldErrors}

              node={node}
              isEditMode={isEditMode}
              showValidation={showValidation}
              renderFieldLabel={renderFieldLabel}
              setProperty={setProperty}
              parameter={param}
              listFieldPath={`parameters[${index}]`}
            />
          </div>
        )
      })}
      <DescriptionField
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
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
  variableTypes,
  showValidation,
  fieldErrors,
  isEditMode,
  node,
  originalNodeId,
}: Pick<NodeDetailsContentProps3,
  | "originalNodeId"
  | "fieldErrors"> & { variableTypes?: VariableTypes } & NodeContentMethods & { removeElement: (property: keyof NodeType, index: number) => void, addElement: (...args: any[]) => any }): JSX.Element {
  const expressionType = useSelector((state: RootState) => getExpressionType(state)(originalNodeId))
  const nodeTypingInfo = useSelector((state: RootState) => getNodeTypingInfo(state)(originalNodeId))
  return (
    <MapVariable
      renderFieldLabel={renderFieldLabel}
      removeElement={removeElement}
      onChange={setProperty}
      node={node}
      addElement={addElement}
      readOnly={!isEditMode}
      showValidation={showValidation}
      variableTypes={variableTypes}
      errors={fieldErrors || []}
      expressionType={getNodeExpressionType(expressionType, nodeTypingInfo)}
    />
  )
}

export function VariableDef({
  renderFieldLabel,
  setProperty,
  variableTypes,
  isEditMode,
  showValidation,
  fieldErrors = [],
  originalNodeId,
  node,
}: Pick<NodeDetailsContentProps3, "originalNodeId" | "fieldErrors"> & { variableTypes?: VariableTypes } & NodeContentMethods): JSX.Element {
  const expressionType = useSelector((state: RootState) => getExpressionType(state)(originalNodeId))
  const nodeTypingInfo = useSelector((state: RootState) => getNodeTypingInfo(state)(originalNodeId))
  const varExprType = getTypingResult(expressionType, nodeTypingInfo)
  const inferredVariableType = ProcessUtils.humanReadableType(varExprType)
  return (
    <Variable
      renderFieldLabel={renderFieldLabel}
      onChange={setProperty}
      node={node}
      readOnly={!isEditMode}
      showValidation={showValidation}
      variableTypes={variableTypes}
      errors={fieldErrors}
      inferredVariableType={inferredVariableType}
    />
  )
}

export function Switch({
  renderFieldLabel,
  setProperty,
  variableTypes,
  findAvailableVariables,
  node,
  setEditedEdges,
  showSwitch,
  isEditMode,
  parameterDefinitions,
  processDefinitionData,
  fieldErrors,
  originalNodeId,
  edges,
  showValidation,
}: NodeContentMethods & Pick<NodeDetailsContentProps3,
  | "findAvailableVariables"
  | "setEditedEdges"
  | "showSwitch"
  | "parameterDefinitions"
  | "processDefinitionData"
  | "fieldErrors"
  | "originalNodeId"
  | "edges"> & { variableTypes?: VariableTypes }): JSX.Element {
  const {node: definition} = processDefinitionData.componentGroups?.flatMap(g => g.components).find(c => c.node.type === node.type)
  const currentExpression = node["expression"]
  const currentExprVal = node["exprVal"]
  const exprValValidator = errorValidator(fieldErrors || [], "exprVal")
  const showExpression = definition["expression"] ? !isEqual(definition["expression"], currentExpression) : currentExpression?.expression
  const showExprVal = !exprValValidator.isValid() || definition["exprVal"] ? definition["exprVal"] !== currentExprVal : currentExprVal
  const [, isCompareView] = useDiffMark()
  const expressionType = useSelector((state: RootState) => getExpressionType(state)(originalNodeId))
  const nodeTypingInfo = useSelector((state: RootState) => getNodeTypingInfo(state)(originalNodeId))

  return (
    <NodeTableBody>
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
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
            node={node}
            findAvailableVariables={findAvailableVariables}
            parameterDefinitions={parameterDefinitions}
            fieldErrors={fieldErrors}

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
            node={node}

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
            value={edges}
            onChange={setEditedEdges}
            edgeTypes={[
              {value: EdgeKind.switchNext},
              {value: EdgeKind.switchDefault, onlyOne: true, disabled: true},
            ]}
            ordered
            readOnly={!isEditMode}
            variableTypes={node["exprVal"] ?
              {
                ...variableTypes,
                [node["exprVal"]]: getNodeExpressionType(expressionType, nodeTypingInfo),
              } :
              variableTypes}
            fieldErrors={fieldErrors || []}
          />
        ) :
        null}
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

export function Split({
  renderFieldLabel,
  setProperty,
  isEditMode,
  showValidation,
  node,
}: NodeContentMethods): JSX.Element {
  return (
    <NodeTableBody>
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
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

export function Properties({
  renderFieldLabel,
  setProperty,
  isEditMode,
  showValidation,
  node,
  processDefinitionData,
  fieldErrors,
  showSwitch,
}: Pick<NodeDetailsContentProps3,
  | "processDefinitionData"
  | "fieldErrors"
  | "showSwitch"> & NodeContentMethods): JSX.Element {
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
