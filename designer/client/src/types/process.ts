import { EditorProps } from "../components/graph/node-modal/editors/expression/Editor";
import { TypingResult, UIParameter } from "./definition";
import { Edge, EdgeType } from "./edge";
import { NodeType, PropertiesType } from "./node";
import { ValidationResult } from "./validation";
import { ComponentGroup, SingleComponentConfig } from "./component";
import { ProcessingType } from "../actions/nk";
import { ScenarioPropertyConfig } from "../components/graph/node-modal/ScenarioProperty";
import { FixedValuesOption } from "../components/graph/node-modal/fragment-input-definition/item";

export type Process = {
    id: string;
    nodes: NodeType[];
    edges: Edge[];
    properties: PropertiesType;
    validationResult: ValidationResult;
    processingType?: ProcessingType;
    category?: string; // optional - see the comment for a field with the same name in DisplayableProcess.scala
};

export type ProcessId = Process["id"];

export type Category = string;

export type ProcessAdditionalFields = {
    description: string | null;
    properties: { [key: string]: string };
    metaDataType: string;
};

export type CustomAction = {
    name: string;
    allowedStateStatusNames: Array<string>;
    icon?: string;
    parameters?: Array<CustomActionParameter>;
};

export type CustomActionParameter = {
    name: string;
    editor: EditorProps;
};

export type ScenarioPropertiesConfig = Record<string, ScenarioPropertyConfig>;

//"ReturnType" is builtin type alias
export interface ReturnedType {
    display: string;
    type: string;
    refClazzName: string;
    params: [];
}

export interface NodeObjectTypeDefinition {
    parameters: UIParameter[] | null;
    outputParameters?: string[] | null;
    returnType: ReturnedType | null;
}

export interface ProcessDefinition {
    services?: Record<string, NodeObjectTypeDefinition>;
    sourceFactories?: Record<string, NodeObjectTypeDefinition>;
    sinkFactories?: Record<string, NodeObjectTypeDefinition>;
    customStreamTransformers?: Record<string, NodeObjectTypeDefinition>;
    fragmentInputs?: Record<string, NodeObjectTypeDefinition>;
    globalVariables?: GlobalVariables;
    typesInformation?: ClassDefinition[];
}

export type ComponentsConfig = Record<string, SingleComponentConfig>;

export type FixedValuesPresets = Record<string, FixedValuesOption[]>;

export interface ProcessDefinitionData {
    componentsConfig?: ComponentsConfig;
    processDefinition?: ProcessDefinition;
    componentGroups?: ComponentGroup[];
    scenarioPropertiesConfig?: ScenarioPropertiesConfig;
    edgesForNodes?: EdgesForNode[];
    customActions?: Array<CustomAction>;
    defaultAsyncInterpretation?: boolean;
    fixedValuesPresets?: FixedValuesPresets;
}

export type EdgesForNode = {
    nodeId: NodeTypeId;
    edges: EdgeType[];
    canChooseNodes: boolean;
    isForInputDefinition: boolean;
};

export type NodeTypeId = {
    type: string;
    id?: string;
};

export interface GlobalVariable {
    returnType: ReturnedType | null;
    categories: string[];
    parameters: [];
    componentConfig: Record<string, any>;
}

export type GlobalVariables = Record<string, GlobalVariable>;

export type ClassDefinition = {
    clazzName: TypingResult;
};
