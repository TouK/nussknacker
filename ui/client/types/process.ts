import {EditorProps} from "../components/graph/node-modal/editors/expression/Editor"
import {TypingResult, UIParameter} from "./definition"
import {Edge} from "./edge"
import {NodeType, PropertiesType} from "./node"
import {ValidationResult} from "./validation"
import {ComponentGroup, SingleComponentConfig} from "./component"
import {ProcessingType} from "../actions/nk"
import {AdditionalPropertyConfig} from "../components/graph/node-modal/AdditionalProperty"

export type Process = {
  id: string,
  nodes: NodeType[],
  edges: Edge[],
  properties: PropertiesType,
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

export type AdditionalPropertiesConfig = Record<string, AdditionalPropertyConfig>
export type DynamicParameterDefinitions = $TodoType

//"ReturnType" is builtin type alias
export interface ReturnedType {
  display: string,
  type: string,
  refClazzName: string,
  params: [],
}

export interface NodeObjectTypeDefinition {
  parameters: UIParameter[],
  outputParameters?: UIParameter[],
  returnType: ReturnedType | null,
}

export interface ProcessDefinition {
  services?: Record<string, NodeObjectTypeDefinition>,
  sourceFactories?: Record<string, NodeObjectTypeDefinition>,
  sinkFactories?: Record<string, NodeObjectTypeDefinition>,
  customStreamTransformers?: Record<string, NodeObjectTypeDefinition>,
  signalsWithTransformers?: Record<string, NodeObjectTypeDefinition>,
  subprocessInputs?: Record<string, NodeObjectTypeDefinition>,
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

export interface GlobalVariable {
  returnType: ReturnedType | null,
  categories: string[],
  parameters: [],
  componentConfig: Record<string, any>,
}

export type GlobalVariables = Record<string, GlobalVariable>

export type ClassDefinition = {
  clazzName: TypingResult,
  methods: Record<string, $TodoType>,
}
