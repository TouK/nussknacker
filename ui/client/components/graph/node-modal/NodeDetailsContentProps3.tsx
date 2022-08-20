import {
  Edge,
  NodeId,
  NodeType,
  NodeValidationError,
  Parameter,
  ProcessDefinitionData,
  ProcessId,
  UIParameter,
  VariableTypes,
} from "../../../types"
import {Dispatch, SetStateAction} from "react"
import {WithTempId} from "./EdgesDndComponent"
import {AdditionalPropertyConfig} from "./AdditionalProperty"
import ProcessUtils from "../../../common/ProcessUtils"
import {DescriptionFieldProps} from "./DescriptionField"
import {FieldType} from "./editors/field/Field"
import {Validator} from "./editors/Validators"

type UpdateState<T> = (updateState: (currentState: Readonly<T>) => T) => void

export interface WithNodeErrors {
  nodeErrors?: NodeValidationError[],
}

export interface NodeDetailsContentConnectedProps {
  isEditMode?: boolean,
  showValidation?: boolean,
  showSwitch?: boolean,
  node: NodeType,
  edges?: Edge[],
  originalNodeId?: NodeId,
  pathsToMark?: string[],
  onChange?: (node: NodeType, outputEdges?: Edge[]) => void,
}

export interface NodeDetailsContentProps extends NodeDetailsContentConnectedProps {
  dynamicParameterDefinitions?: UIParameter[],
  currentErrors?: NodeValidationError[],
  processId?: ProcessId,
  additionalPropertiesConfig?: Record<string, AdditionalPropertyConfig>,
  findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  processDefinitionData?: ProcessDefinitionData,
  expressionType?,
  nodeTypingInfo?,
  updateNodeData?: (node: NodeType, edges: WithTempId<Edge>[]) => void,
  findAvailableBranchVariables?,
  processProperties?,
  variableTypes?: VariableTypes,
}

export interface EditableEdges {
  editedEdges: WithTempId<Edge>[],
  setEditedEdges: Dispatch<SetStateAction<WithTempId<Edge>[]>>,
}

export interface EditableNode {
  originalNode: NodeType,
  editedNode: NodeType,
  setEditedNode: Dispatch<SetStateAction<NodeType>>,
}

export interface NodeDetailsContentProps2 extends NodeDetailsContentProps, EditableNode {
  parameterDefinitions: UIParameter[],
}

export interface NodeDetailsContentProps3 extends NodeDetailsContentProps2 {
  fieldErrors?: NodeValidationError[],
  updateNodeState: UpdateState<NodeType>,
  setEdgesState: (edges: Edge[]) => void,
}

export interface NodeContentMethods {
  isMarked: (path?: string) => boolean,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
}

export type IdFieldProps =
  NodeContentMethods
  & Pick<NodeDetailsContentProps3, "isEditMode" | "showValidation" | "editedNode">

export interface ParameterExpressionFieldProps extends NodeContentMethods, Pick<NodeDetailsContentProps3, "isEditMode" | "editedNode" | "showValidation" | "showSwitch" | "parameterDefinitions" | "findAvailableVariables" | "originalNodeId" | "fieldErrors"> {
  parameter: Parameter,
  listFieldPath: string,
}

export type SourceSinkCommonProps =
  NodeContentMethods
  & IdFieldProps
  & Omit<ParameterExpressionFieldProps, "parameter" | "listFieldPath">
  & DescriptionFieldProps
  & Pick<NodeDetailsContentProps3, "node">

export interface NodeFieldProps<N extends string, V> extends NodeContentMethods, Pick<NodeDetailsContentProps3, "editedNode" | "isEditMode" | "showValidation"> {
  fieldType: FieldType,
  fieldLabel: string,
  fieldProperty: N,
  autoFocus?: boolean,
  readonly?: boolean,
  defaultValue?: V,
  validators?: Validator[],
}

export interface StaticExpressionFieldProps extends NodeContentMethods, Pick<NodeDetailsContentProps3, "isEditMode" | "editedNode" | "showValidation" | "showSwitch" | "parameterDefinitions" | "findAvailableVariables" | "originalNodeId" | "fieldErrors"> {
  fieldLabel: string,
}
