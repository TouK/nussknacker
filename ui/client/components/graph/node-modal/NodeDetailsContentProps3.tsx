import {Edge, NodeId, NodeType, NodeValidationError, ProcessDefinitionData, UIParameter,} from "../../../types"
import ProcessUtils from "../../../common/ProcessUtils"

export type UpdateState<T> = (updateState: ((currentState: Readonly<T>) => T)) => void

export interface NodeDetailsContentProps3 {
  edges: Edge[],
  fieldErrors?: NodeValidationError[],
  findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  originalNodeId?: NodeId,
  parameterDefinitions: UIParameter[],
  processDefinitionData?: ProcessDefinitionData,
  setEditedEdges: (edges: Edge[]) => void,
  showSwitch?: boolean,
  updateNodeState: UpdateState<NodeType>,
}

export interface NodeContentMethods {
  node: NodeType,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showValidation?: boolean,
  isEditMode?: boolean,
}
