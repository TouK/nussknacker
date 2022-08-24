import {
  Edge,
  NodeId,
  NodeType,
  NodeValidationError,
  Parameter,
  ProcessDefinitionData,
  UIParameter,
} from "../../../types"
import {WithTempId} from "./EdgesDndComponent"
import ProcessUtils from "../../../common/ProcessUtils"
import {FieldType} from "./editors/field/Field"
import {Validator} from "./editors/Validators"

export type UpdateState<T> = (updateState: (currentState: Readonly<T>) => T) => void

export interface NodeDetailsContentProps3 {
  editedEdges: WithTempId<Edge>[],
  editedNode: NodeType,
  fieldErrors?: NodeValidationError[],
  findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  isEditMode?: boolean,
  originalNodeId?: NodeId,
  parameterDefinitions: UIParameter[],
  processDefinitionData?: ProcessDefinitionData,
  setEditedEdges: (edges: Edge[]) => void,
  showSwitch?: boolean,
  showValidation?: boolean,
  updateNodeState: UpdateState<NodeType>,
}

export interface NodeContentMethods {
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
