import {EditorProps} from "../components/graph/node-modal/editors/expression/Editor"
import {TypingResult, UIParameter} from "./definition"
import {Edge} from "./edge"
import {NodeType} from "./node"
import {ValidationResult} from "./validation"
import {ComponentGroup, SingleComponentConfig} from "./component"
import {ProcessingType} from "../actions/nk"

export type Process = {
  id: string,
  nodes: NodeType[],
  edges: Edge[],
  properties: NodeType,
  validationResult: ValidationResult,
  processingType?: ProcessingType,
}

export type ProcessId = Process["id"]

export type Category = string

export type CustomAction = {
  name: string,
  allowedStateStatusNames: Array<string>,
  icon?: string,
  parameters?: Array<CustomActionParameter>,
}

export type CustomActionParameter = {
  name: string,
  editor: EditorProps,
}

export type AdditionalPropertiesConfig = $TodoType
export type DynamicParameterDefinitions = $TodoType

export interface NodeObjectTypeDefinition {
  parameters?: UIParameter[]
  returnType?: $TodoType
}

export interface ProcessDefinition {
  services?: Record<string, NodeObjectTypeDefinition>
  sourceFactories?: Record<string, NodeObjectTypeDefinition>
  sinkFactories?: Record<string, NodeObjectTypeDefinition>
  customStreamTransformers?: Record<string, NodeObjectTypeDefinition>
  signalsWithTransformers?: Record<string, NodeObjectTypeDefinition>
  subprocessInputs?: Record<string, NodeObjectTypeDefinition>
  globalVariables?: GlobalVariables,
  typesInformation?: ClassDefinition[],
}

export type ComponentsConfig = Record<string, SingleComponentConfig>

export interface ProcessDefinitionData {
  componentsConfig?: ComponentsConfig,
  processDefinition?: ProcessDefinition,
  componentGroups?: ComponentGroup[],
  additionalPropertiesConfig?: AdditionalPropertiesConfig,
  edgesForNodes?: $TodoType[],
  customActions?: Array<CustomAction>,
  defaultAsyncInterpretation?: boolean,
}

export type GlobalVariables = Record<string, {
  returnType: $TodoType | null,
  categories: string[],
}>

export type ClassDefinition = {
  clazzName: TypingResult,
  methods: Record<string, $TodoType>,
}
