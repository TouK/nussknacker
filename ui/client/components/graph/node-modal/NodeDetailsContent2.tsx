/* eslint-disable i18next/no-literal-string */
import {Edge, EdgeKind, NodeType, Parameter, UIParameter} from "../../../types"
import {EdgesDndComponent, WithTempId} from "./EdgesDndComponent"
import {DispatchWithCallback, hasOutputVar, IdField} from "./NodeDetailsContentUtils"
import React, {SetStateAction} from "react"
import {adjustParameters} from "./ParametersUtils"
import {cloneDeep, get, has, isEqual, partition, set, sortBy, startsWith} from "lodash"
import {v4 as uuid4} from "uuid"
import {allValid, Error, errorValidator, Validator} from "./editors/Validators"
import {StateForSelectTestResults} from "../../../common/TestResultUtils"
import NodeUtils from "../NodeUtils"
import Field, {FieldType} from "./editors/field/Field"
import SubprocessInputDefinition from "./subprocess-input-definition/SubprocessInputDefinition"
import SubprocessOutputDefinition from "./SubprocessOutputDefinition"
import {refParameters, serviceParameters} from "./NodeDetailsContent/helpers"
import ParameterList from "./ParameterList"
import {InputWithFocus} from "../../withFocus"
import BranchParameters from "./BranchParameters"
import MapVariable from "./MapVariable"
import {DEFAULT_EXPRESSION_ID} from "../../../common/graph/constants"
import Variable from "./Variable"
import ProcessUtils from "../../../common/ProcessUtils"
import AdditionalProperty from "./AdditionalProperty"
import {NodeDetails} from "./NodeDetailsContent/NodeDetails"
import ExpressionField from "./editors/expression/ExpressionField"
import {FieldLabel, findParamDefinitionByName} from "./FieldLabel"
import NodeErrors from "./NodeErrors"
import {TestResultsWrapper} from "./TestResultsWrapper"
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox"
import {NodeDetailsContentProps} from "./NodeDetailsContent"

interface State {
  editedNode: NodeType,
  edges: WithTempId<Edge>[],
}

export interface NodeDetailsContentProps2 extends NodeDetailsContentProps {
  parameterDefinitions: UIParameter[],
  originalNode: NodeType,
  editedNode: NodeType,
  setEditedNode: DispatchWithCallback<SetStateAction<NodeType>>,
}

const generateUUIDs = (editedNode: NodeType, properties: string[]): NodeType => {
  const node = cloneDeep(editedNode)
  properties.forEach((property) => {
    if (has(node, property)) {
      get(node, property, []).forEach((el) => el.uuid = el.uuid || uuid4())
    }
  })
  return node
}

export class NodeDetailsContent2 extends React.Component<NodeDetailsContentProps2, State> {
  constructor(props: NodeDetailsContentProps2) {
    super(props)

    const {adjustedNode} = adjustParameters(props.node, props.parameterDefinitions)
    const withUuids = generateUUIDs(adjustedNode, ["fields", "parameters"])

    this.state = {
      editedNode: withUuids,
      edges: props.edges,
    }

    //In most cases this is not needed, as parameter definitions should be present in validation response
    //However, in dynamic cases (as adding new topic/schema version) this can lead to stale parameters
    if (props.isEditMode) {
      this.updateNodeData(withUuids)
    }
  }

  componentDidUpdate(prevProps: NodeDetailsContentProps2, prevState: State): void {
    const nextState = this.state
    const nextProps = this.props
    let node = nextProps.node
    if (!isEqual(prevProps.parameterDefinitions, nextProps.parameterDefinitions)) {
      console.log("test")
      node = adjustParameters(node, nextProps.parameterDefinitions).adjustedNode
    }

    if (
      !isEqual(prevProps.edges, nextProps.edges) ||
      !isEqual(prevProps.node, node)
    ) {
      console.log(isEqual(prevProps.node, node), prevProps.node)
      this.updateNodeState(() => ({editedNode: node}))
      //In most cases this is not needed, as parameter definitions should be present in validation response
      //However, in dynamic cases (as adding new topic/schema version) this can lead to stale parameters
      if (nextProps.isEditMode) {
        this.updateNodeData(node)
      }
    }

    if (
      !isEqual(prevState.edges, nextState.edges) ||
      !isEqual(prevState.editedNode, nextState.editedNode)
    ) {
      if (nextProps.isEditMode) {
        this.updateNodeData(nextState.editedNode)
      }
    }
  }

  publishNodeChange = (): void => {
    this.props.onChange?.(this.state.editedNode, this.state.edges)
  }

  updateNodeState = (updateNode: (current: NodeType) => { editedNode: NodeType }): void => {
    this.setState(
      s => updateNode(s.editedNode),
      this.publishNodeChange
    )
  }

  updateNodeData(currentNode: NodeType): void {
    const {props, state} = this
    props.updateNodeData(props.processId, {
      variableTypes: props.findAvailableVariables(props.originalNodeId),
      branchVariableTypes: props.findAvailableBranchVariables(props.originalNodeId),
      nodeData: currentNode,
      processProperties: props.processProperties,
      outgoingEdges: state.edges.map(e => ({...e, to: e._id || e.to})),
    })
  }

  render(): JSX.Element {
    const {
      props,
      state,
      updateNodeState,
      publishNodeChange,
    } = this

    const {pathsToMark} = props
    const {currentErrors = [], processId, node} = props

    const [fieldErrors, otherErrors] = partition(currentErrors, error => !!error.fieldName)

    const setEdgesState = (nextEdges: Edge[]) => {
      this.setState(
        ({edges}) => {
          if (nextEdges !== edges) {
            return {edges: nextEdges}
          }
        },
        publishNodeChange
      )
    }

    const isMarked = (path: string): boolean => {
      return pathsToMark?.some(toMark => startsWith(toMark, path))
    }

    const removeElement = (property: string, index: number): void => {
      if (has(this.state.editedNode, property)) {
        const node = cloneDeep(this.state.editedNode)
        get(node, property).splice(index, 1)

        updateNodeState(() => ({editedNode: node}))
      }
    }

    const renderFieldLabel = (paramName: string): JSX.Element => (
      <FieldLabel
        nodeId={props.originalNodeId}
        parameterDefinitions={props.parameterDefinitions}
        paramName={paramName}
      />
    )

    const addElement = <T extends unknown>(property: string, element: T): void => {
      if (has(this.state.editedNode, property)) {
        const node = cloneDeep(this.state.editedNode)
        get(node, property).push(element)

        updateNodeState(() => ({editedNode: node}))
      }
    }

    const setNodeDataAt = <T extends unknown>(propToMutate: string, newValue: T, defaultValue?: T): void => {
      const value = newValue == null && defaultValue != undefined ? defaultValue : newValue
      updateNodeState((current) => {
        const node = cloneDeep(current)
        return {editedNode: set(node, propToMutate, value)}
      })
    }

    //this is for "static" fields like expressions in filters, switches etc.
    const createStaticExpressionField = ({
      fieldName,
      fieldLabel,
      expressionProperty,
      fieldErrors,
      testResultsState,
    }: { fieldName: string, fieldLabel: string, expressionProperty: string, fieldErrors: Error[], testResultsState?: StateForSelectTestResults }): JSX.Element => {
      return (
        <ExpressionField
          fieldName={fieldName}
          fieldLabel={fieldLabel}
          exprPath={`${expressionProperty}`}
          isEditMode={props.isEditMode}
          editedNode={this.state.editedNode}
          isMarked={isMarked}
          showValidation={props.showValidation}
          showSwitch={props.showSwitch}
          parameterDefinition={findParamDefinitionByName(props.parameterDefinitions, fieldName)}
          setNodeDataAt={setNodeDataAt}
          testResultsToShow={testResultsState.testResultsToShow}
          renderFieldLabel={renderFieldLabel}
          variableTypes={props.findAvailableVariables(props.originalNodeId, undefined)}
          errors={fieldErrors}
        />
      )
    }

    const idField1 = (): JSX.Element => (
      <IdField
        isMarked={isMarked("id")}
        isEditMode={props.isEditMode}
        showValidation={props.showValidation}
        editedNode={this.state.editedNode}
        onChange={(newValue) => setNodeDataAt("id", newValue)}
      >
        {renderFieldLabel("Name")}
      </IdField>
    )

    const createField = <K extends keyof NodeType & string, T extends NodeType[K]>({
      fieldType,
      fieldLabel,
      fieldProperty,
      autoFocus,
      readonly,
      defaultValue,
      validators = [],
    }: {
      fieldType: FieldType,
      fieldLabel: string,
      fieldProperty: K,
      autoFocus?: boolean,
      readonly?: boolean,
      defaultValue?: T,
      validators?: Validator[],
    }): JSX.Element => {
      const {isEditMode, showValidation} = props
      const readOnly = !isEditMode || readonly
      const value: T = get(this.state.editedNode, fieldProperty, null) ?? defaultValue
      const className = !showValidation || allValid(validators, [value]) ? "node-input" : "node-input node-input-with-error"
      const onChange = (newValue) => setNodeDataAt(fieldProperty, newValue, defaultValue)
      return (
        <Field
          type={fieldType}
          isMarked={isMarked(fieldProperty)}
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

    const descriptionField = (): JSX.Element => {
      return createField({
        fieldType: FieldType.plainTextarea,
        fieldLabel: "Description",
        fieldProperty: "additionalFields.description",
      })
    }

    //this is for "dynamic" parameters in sources, sinks, services etc.
    const createParameterExpressionField = ({
      parameter,
      expressionProperty,
      listFieldPath,
      fieldErrors,
      testResultsState,
    }: { parameter: Parameter, expressionProperty: string, listFieldPath: string, fieldErrors: Error[], testResultsState: StateForSelectTestResults }): JSX.Element => {
      return (
        <ExpressionField
          fieldName={parameter.name}
          fieldLabel={parameter.name}
          exprPath={`${listFieldPath}.${expressionProperty}`}
          isEditMode={props.isEditMode}
          editedNode={this.state.editedNode}
          isMarked={isMarked}
          showValidation={props.showValidation}
          showSwitch={props.showSwitch}
          parameterDefinition={findParamDefinitionByName(props.parameterDefinitions, parameter.name)}
          setNodeDataAt={setNodeDataAt}
          testResultsToShow={testResultsState.testResultsToShow}
          renderFieldLabel={renderFieldLabel}
          variableTypes={props.findAvailableVariables(props.originalNodeId, props.parameterDefinitions?.find(p => p.name === parameter.name))}
          errors={fieldErrors}
        />
      )
    }

    const sourceSinkCommon = ({
      fieldErrors,
      children,
      testResultsState,
    }: { fieldErrors: Error[], children?: JSX.Element, testResultsState: StateForSelectTestResults }): JSX.Element => {
      const {node} = props
      return (
        <div className="node-table-body">
          {idField1()}
          {refParameters(this.state.editedNode).map((param, index) => (
            <div className="node-block" key={node.id + param.name + index}>
              {createParameterExpressionField(
                {
                  parameter: param,
                  expressionProperty: "expression",
                  listFieldPath: `ref.parameters[${index}]`,
                  fieldErrors: fieldErrors,
                  testResultsState,
                }
              )}
            </div>
          ))}
          {children}
          {descriptionField()}
        </div>
      )
    }

    const customNode = (fieldErrors: Error[], testResultsState: StateForSelectTestResults): JSX.Element => {
      const {
        edges,
      } = state
      const {
        originalNode,
        findAvailableVariables,
        originalNodeId,
        node,
        isEditMode,
        showValidation,
        expressionType,
        nodeTypingInfo,
        processDefinitionData,
        showSwitch,
        additionalPropertiesConfig,
        pathsToMark,
      } = props

      const {testResultsToShow} = testResultsState

      const variableTypes = findAvailableVariables(originalNodeId)
      //compare window uses legacy egde component
      const isCompareView = pathsToMark !== undefined

      switch (NodeUtils.nodeType(node)) {
        case "Source":
          return sourceSinkCommon({fieldErrors, testResultsState})
        case "Sink":
          const toAppend: JSX.Element = (
            <div>
              {createField({
                fieldType: FieldType.checkbox,
                fieldLabel: "Disabled",
                fieldProperty: "isDisabled",
              })}
            </div>
          )
          return sourceSinkCommon({fieldErrors, children: toAppend, testResultsState})
        case "SubprocessInputDefinition":
          return (
            <SubprocessInputDefinition
              addElement={addElement}
              onChange={setNodeDataAt}
              node={this.state.editedNode}
              isMarked={isMarked}
              readOnly={!isEditMode}
              removeElement={removeElement}
              showValidation={showValidation}
              renderFieldLabel={renderFieldLabel}
              errors={fieldErrors}
              variableTypes={variableTypes}
            />
          )
        case "SubprocessOutputDefinition":
          return (
            <SubprocessOutputDefinition
              renderFieldLabel={renderFieldLabel}
              removeElement={removeElement}
              onChange={setNodeDataAt}
              node={this.state.editedNode}
              addElement={addElement}
              isMarked={isMarked}
              readOnly={!isEditMode}
              showValidation={showValidation}
              errors={fieldErrors}

              variableTypes={variableTypes}
              expressionType={expressionType || nodeTypingInfo && {fields: nodeTypingInfo}}
            />
          )
        case "Filter":
          return (
            <div className="node-table-body">
              {idField1()}
              {createStaticExpressionField(
                {
                  fieldName: "expression",
                  fieldLabel: "Expression",
                  expressionProperty: "expression",
                  fieldErrors: fieldErrors,
                  testResultsState,
                }
              )}
              {createField({
                fieldType: FieldType.checkbox,
                fieldLabel: "Disabled",
                fieldProperty: "isDisabled",
              })}
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
                    fieldErrors={fieldErrors}
                  />
                ) :
                null}
              {descriptionField()}
            </div>
          )
        case "Enricher":
        case "Processor":
          return (
            <div className="node-table-body">
              {idField1()}
              {serviceParameters(this.state.editedNode).map((param, index) => {
                return (
                  <div className="node-block" key={node.id + param.name + index}>
                    {createParameterExpressionField(
                      {
                        parameter: param,
                        expressionProperty: "expression",
                        listFieldPath: `service.parameters[${index}]`,
                        fieldErrors: fieldErrors,
                        testResultsState,
                      }
                    )}
                  </div>
                )
              })}
              {node.type === "Enricher" ?
                createField({
                  fieldType: FieldType.input,
                  fieldLabel: "Output",
                  fieldProperty: "output",
                  validators: [errorValidator(fieldErrors, "output")],
                }) :
                null}
              {node.type === "Processor" ?
                createField({
                  fieldType: FieldType.checkbox,
                  fieldLabel: "Disabled",
                  fieldProperty: "isDisabled",
                }) :
                null}
              {descriptionField()}
            </div>
          )
        case "SubprocessInput":
          return (
            <div className="node-table-body">
              {idField1()}
              {createField({
                fieldType: FieldType.checkbox,
                fieldLabel: "Disabled",
                fieldProperty: "isDisabled",
              })}
              <ParameterList
                processDefinitionData={processDefinitionData}
                editedNode={this.state.editedNode}
                savedNode={this.state.editedNode}
                setNodeState={newParams => setNodeDataAt("ref.parameters", newParams)}
                createListField={(param, index) => createParameterExpressionField(
                  {
                    parameter: param,
                    expressionProperty: "expression",
                    listFieldPath: `ref.parameters[${index}]`,
                    fieldErrors: fieldErrors,
                    testResultsState,
                  }
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
              {descriptionField()}
            </div>
          )

        case "Join":
        case "CustomNode":
          return (
            <div className="node-table-body">
              {idField1()}
              {
                hasOutputVar(node, processDefinitionData) && createField({
                  fieldType: FieldType.input,
                  fieldLabel: "Output variable name",
                  fieldProperty: "outputVar",
                  validators: [errorValidator(fieldErrors, "outputVar")],
                })
              }
              {NodeUtils.nodeIsJoin(this.state.editedNode) && (
                <BranchParameters
                  node={this.state.editedNode}
                  isMarked={isMarked}
                  showValidation={showValidation}
                  showSwitch={showSwitch}
                  isEditMode={isEditMode}
                  errors={fieldErrors}
                  parameterDefinitions={props.parameterDefinitions}
                  setNodeDataAt={setNodeDataAt}
                  testResultsToShow={testResultsToShow}
                  findAvailableVariables={findAvailableVariables}
                />
              )}
              {this.state.editedNode.parameters?.map((param, index) => {
                return (
                  <div className="node-block" key={node.id + param.name + index}>
                    {createParameterExpressionField(
                      {
                        parameter: param,
                        expressionProperty: "expression",
                        listFieldPath: `parameters[${index}]`,
                        fieldErrors: fieldErrors,
                        testResultsState,
                      }
                    )}
                  </div>
                )
              })}
              {descriptionField()}
            </div>
          )
        case "VariableBuilder":
          return (
            <MapVariable
              renderFieldLabel={renderFieldLabel}
              removeElement={removeElement}
              onChange={setNodeDataAt}
              node={this.state.editedNode}
              addElement={addElement}
              isMarked={isMarked}
              readOnly={!isEditMode}
              showValidation={showValidation}
              variableTypes={variableTypes}
              errors={fieldErrors}
              expressionType={expressionType || nodeTypingInfo && {fields: nodeTypingInfo}}
            />
          )
        case "Variable":
          const varExprType = expressionType || (nodeTypingInfo || {})[DEFAULT_EXPRESSION_ID]
          return (
            <Variable
              renderFieldLabel={renderFieldLabel}
              onChange={setNodeDataAt}
              node={this.state.editedNode}
              isMarked={isMarked}
              readOnly={!isEditMode}
              showValidation={showValidation}
              variableTypes={variableTypes}
              errors={fieldErrors}
              inferredVariableType={ProcessUtils.humanReadableType(varExprType)}
            />
          )
        case "Switch":
          const {node: definition} = processDefinitionData.componentGroups?.flatMap(g => g.components).find(c => c.node.type === this.state.editedNode.type)
          const currentExpression = originalNode["expression"]
          const currentExprVal = originalNode["exprVal"]
          const exprValValidator = errorValidator(fieldErrors, "exprVal")
          const showExpression = definition["expression"] ? !isEqual(definition["expression"], currentExpression) : currentExpression?.expression
          const showExprVal = !exprValValidator.isValid() || definition["exprVal"] ? definition["exprVal"] !== currentExprVal : currentExprVal
          return (
            <div className="node-table-body">
              {idField1()}
              {showExpression ?
                createStaticExpressionField({
                  fieldName: "expression",
                  fieldLabel: "Expression (deprecated)",
                  expressionProperty: "expression",
                  fieldErrors: fieldErrors,
                  testResultsState,
                }) :
                null}
              {showExprVal ?
                createField({
                  fieldType: FieldType.input,
                  fieldLabel: "exprVal (deprecated)",
                  fieldProperty: "exprVal",
                  validators: [errorValidator(fieldErrors, "exprVal")],
                }) :
                null}
              {!isCompareView ?
                (
                  <EdgesDndComponent
                    label={"Conditions"}
                    nodeId={originalNodeId}
                    value={edges}
                    onChange={(nextEdges) => setEdgesState(nextEdges)}
                    edgeTypes={[
                      {value: EdgeKind.switchNext},
                      {value: EdgeKind.switchDefault, onlyOne: true, disabled: true},
                    ]}
                    ordered
                    readOnly={!isEditMode}
                    variableTypes={this.state.editedNode["exprVal"] ?
                      {
                        ...variableTypes,
                        [this.state.editedNode["exprVal"]]: expressionType || nodeTypingInfo && {fields: nodeTypingInfo},
                      } :
                      variableTypes}
                    fieldErrors={fieldErrors}
                  />
                ) :
                null}
              {descriptionField()}
            </div>
          )
        case "Split":
          return (
            <div className="node-table-body">
              {idField1()}
              {descriptionField()}
            </div>
          )
        case "Properties":
          const type = node.typeSpecificProperties.type
          //fixme move this configuration to some better place?
          const fields =
            node.isSubprocess ?
              [
                createField({
                  fieldType: FieldType.input,
                  fieldLabel: "Documentation url",
                  fieldProperty: "typeSpecificProperties.docsUrl",
                  autoFocus: true,
                  validators: [errorValidator(fieldErrors, "docsUrl")],
                }),
              ] :
              type === "StreamMetaData" ?
                [
                  createField({
                    fieldType: FieldType.input,
                    fieldLabel: "Parallelism",
                    fieldProperty: "typeSpecificProperties.parallelism",
                    autoFocus: true,
                    validators: [errorValidator(fieldErrors, "parallelism")],
                  }),
                  createField({
                    fieldType: FieldType.input,
                    fieldLabel: "Checkpoint interval in seconds",
                    fieldProperty: "typeSpecificProperties.checkpointIntervalInSeconds",
                    validators: [errorValidator(fieldErrors, "checkpointIntervalInSeconds")],
                  }),
                  createField({
                    fieldType: FieldType.checkbox,
                    fieldLabel: "Spill state to disk",
                    fieldProperty: "typeSpecificProperties.spillStateToDisk",
                    validators: [errorValidator(fieldErrors, "spillStateToDisk")],
                  }),
                  createField({
                    fieldType: FieldType.checkbox,
                    fieldLabel: "Should use async interpretation",
                    fieldProperty: "typeSpecificProperties.useAsyncInterpretation",
                    validators: [errorValidator(fieldErrors, "useAsyncInterpretation")],
                    defaultValue: processDefinitionData.defaultAsyncInterpretation,
                  }),
                ] :
                type === "LiteStreamMetaData" ?
                  [
                    createField({
                      fieldType: FieldType.input,
                      fieldLabel: "Parallelism",
                      fieldProperty: "typeSpecificProperties.parallelism",
                      autoFocus: true,
                      validators: [errorValidator(fieldErrors, "parallelism")],
                    }),
                  ] :
                  [createField({
                    fieldType: FieldType.input,
                    fieldLabel: "Query path",
                    fieldProperty: "typeSpecificProperties.path",
                    validators: [errorValidator(fieldErrors, "path")],
                  })]
          //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
          const additionalFields = sortBy(Object.entries(additionalPropertiesConfig), e => e[0]).map(
            ([propName, propConfig]) => (
              <AdditionalProperty
                key={propName}
                showSwitch={showSwitch}
                showValidation={showValidation}
                propertyName={propName}
                propertyConfig={propConfig}
                propertyErrors={fieldErrors}
                onChange={setNodeDataAt}
                renderFieldLabel={renderFieldLabel}
                editedNode={this.state.editedNode}
                readOnly={!isEditMode}
              />
            )
          )
          return (
            <div className="node-table-body">
              {idField1()}
              {[...fields, ...additionalFields]}
              {descriptionField()}
            </div>
          )
        default:
          return (
            <div>
              Node type not known.
              <NodeDetails node={node}/>
            </div>
          )
      }
    }

    return (
      <>
        <NodeErrors errors={otherErrors} message="Node has errors"/>
        <TestResultsWrapper nodeId={node.id}>
          {testResultsState => customNode(fieldErrors, testResultsState || {})}
        </TestResultsWrapper>
        <NodeAdditionalInfoBox node={this.state.editedNode} processId={processId}/>
      </>
    )
  }
}
