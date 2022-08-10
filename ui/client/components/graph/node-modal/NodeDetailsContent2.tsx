/* eslint-disable i18next/no-literal-string */
import {Edge, NodeType, UIParameter} from "../../../types"
import {WithTempId} from "./EdgesDndComponent"
import {DispatchWithCallback} from "./NodeDetailsContentUtils"
import React, {SetStateAction} from "react"
import {adjustParameters} from "./ParametersUtils"
import {cloneDeep, get, has, isEqual, partition} from "lodash"
import {v4 as uuid4} from "uuid"
import NodeErrors from "./NodeErrors"
import {TestResultsWrapper} from "./TestResultsWrapper"
import {NodeDetailsContentProps} from "./NodeDetailsContent"
import {NodeDetailsContent3} from "./NodeDetailsContent3"

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
      node = adjustParameters(node, nextProps.parameterDefinitions).adjustedNode
    }

    if (
      !isEqual(prevProps.edges, nextProps.edges) ||
      !isEqual(prevProps.node, node)
    ) {
      this.updateNodeState(() => node)
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

  updateNodeState = (updateNode: (current: NodeType) => NodeType): void => {
    this.setState(
      s => ({
        editedNode: updateNode(s.editedNode),
      }),
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

  setEdgesState = (nextEdges: Edge[]) => {
    this.setState(
      ({edges}) => {
        if (nextEdges !== edges) {
          return {edges: nextEdges}
        }
      },
      this.publishNodeChange
    )
  }

  render(): JSX.Element {
    const {
      props,
      updateNodeState,
      publishNodeChange,
      setEdgesState,
    } = this

    const {currentErrors = [], node} = props

    const [fieldErrors, otherErrors] = partition(currentErrors, error => !!error.fieldName)

    return (
      <>
        <NodeErrors errors={otherErrors} message="Node has errors"/>
        <TestResultsWrapper nodeId={node.id}>
          {testResultsState => (
            <NodeDetailsContent3
              {...this.props}
              editedNode={this.state.editedNode}
              edges={this.state.edges}
              publishNodeChange={publishNodeChange}
              setEdgesState={setEdgesState}
              updateNodeState={updateNodeState}
              testResultsState={testResultsState}
              fieldErrors={fieldErrors}
            />
          )}
        </TestResultsWrapper>
      </>
    )
  }
}

