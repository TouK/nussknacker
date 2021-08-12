import {Edge} from "./edge"
import {NodeType} from "./node"
import {ValidationResult} from "./validation"
import {EditorProps} from "../components/graph/node-modal/editors/expression/Editor"
import {TypingResult} from "./definition";

export type Process = {
  id: string,
  nodes: NodeType[],
  edges: Edge[],
  properties: NodeType,
  validationResult: ValidationResult,
}

export type ProcessId = string

export type NodeCategory = string

export type PossibleNode = {
  categories: NodeCategory[],
  node: NodeType,
  label: string,
  type: string,
}

export type NodesGroup = {
  possibleNodes: PossibleNode[],
  name: string,
}

export type CustomAction = {
  name: string,
  allowedStateStatusNames: Array<string>,
  icon: string | null,
  parameters: Array<CustomActionParameter> | null,
}

export type CustomActionParameter = {
  name: string,
  editor: EditorProps,
}

export type ProcessDefinitionData = {
  nodesConfig?: $TodoType,
  nodesToAdd?: NodesGroup[],
  processDefinition?: $TodoType,
  customActions?: Array<CustomAction>,
  defaultAsyncInterpretation?: boolean,
}

export type ProcessDefinition = {
  typesInformation: ClassDefinition[]
}

export type ClassDefinition = {
  clazzName: TypingResult,
  methods: Record<string, $TodoType>
}