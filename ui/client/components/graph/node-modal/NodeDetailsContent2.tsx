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

export function generateUUIDs(editedNode: NodeType, properties: string[]): NodeType {
  const node = cloneDeep(editedNode)
  properties.forEach((property) => {
    if (has(node, property)) {
      get(node, property, []).forEach((el) => el.uuid = el.uuid || uuid4())
    }
  })
  return node
}

export interface NodeDetailsContentProps2 extends NodeDetailsContentProps {
  parameterDefinitions: UIParameter[],
  originalNode: NodeType,

  editedNode: NodeType,
  setEditedNode: DispatchWithCallback<SetStateAction<NodeType>>,

  editedEdges: WithTempId<Edge>[],
  setEditedEdges: DispatchWithCallback<SetStateAction<WithTempId<Edge>[]>>,
}

export class NodeDetailsContent2 extends React.Component<NodeDetailsContentProps2> {
  componentDidUpdate(prevProps: NodeDetailsContentProps2): void {
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
      !isEqual(prevProps.editedEdges, nextProps.editedEdges) ||
      !isEqual(prevProps.editedNode, nextProps.editedNode)
    ) {
      if (nextProps.isEditMode) {
        this.updateNodeData(nextProps.editedNode)
      }
    }
  }

  updateNodeData(currentNode: NodeType): void {
    this.props.updateNodeData(this.props.processId, {
      variableTypes: this.props.findAvailableVariables(this.props.originalNodeId),
      branchVariableTypes: this.props.findAvailableBranchVariables(this.props.originalNodeId),
      nodeData: currentNode,
      processProperties: this.props.processProperties,
      outgoingEdges: this.props.editedEdges.map(e => ({...e, to: e._id || e.to})),
    })
  }

  publishNodeChange = (): void => {
    this.props.onChange?.(this.props.editedNode, this.props.editedEdges)
  }

  updateNodeState = (updateNode: (current: NodeType) => NodeType): void => {
    this.props.setEditedNode(
      updateNode,
      this.publishNodeChange
    )
  }

  setEdgesState = (nextEdges: Edge[]) => {
    this.props.setEditedEdges(
      currentEdges => nextEdges !== currentEdges ? nextEdges : currentEdges,
      this.publishNodeChange
    )
  }

  render(): JSX.Element {
    const {currentErrors = [], node, editedNode, editedEdges} = this.props

    const [fieldErrors, otherErrors] = partition(currentErrors, error => !!error.fieldName)

    return (
      <>
        <NodeErrors errors={otherErrors} message="Node has errors"/>
        <TestResultsWrapper nodeId={node.id}>
          <NodeDetailsContent3
            {...this.props}
            editedNode={editedNode}
            edges={editedEdges}
            setEdgesState={this.setEdgesState}
            updateNodeState={this.updateNodeState}
            fieldErrors={fieldErrors}
          />
        </TestResultsWrapper>
      </>
    )
  }
}

