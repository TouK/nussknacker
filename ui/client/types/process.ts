import {EditorProps} from "../components/graph/node-modal/editors/expression/Editor"
import {TypingResult} from "./definition"
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

export type ProcessId = string

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

export type ProcessDefinitionData = {
  componentsConfig?: Record<string, SingleComponentConfig>,
  componentGroups?: ComponentGroup[],
  processDefinition?: $TodoType,
  customActions?: Array<CustomAction>,
  defaultAsyncInterpretation?: boolean,
  additionalPropertiesConfig?: AdditionalPropertiesConfig,
}

export type ProcessDefinition = {
  typesInformation: ClassDefinition[],
}

export type ClassDefinition = {
  clazzName: TypingResult,
  methods: Record<string, $TodoType>,
}
