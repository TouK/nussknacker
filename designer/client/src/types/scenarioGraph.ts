import { EditorProps } from "../components/graph/node-modal/editors/expression/Editor";
import { TypingResult, UIParameter } from "./definition";
import { Edge, EdgeType } from "./edge";
import { NodeType, PropertiesType } from "./node";
import { ComponentGroup, SingleComponentConfig } from "./component";
import { ScenarioPropertyConfig } from "../components/graph/node-modal/ScenarioProperty";
import { FixedValuesOption } from "../components/graph/node-modal/fragment-input-definition/item";
import { ValidationResult } from "./validation";

export type ScenarioGraph = {
    nodes: NodeType[];
    edges: Edge[];
    properties: PropertiesType;
    validationResult: ValidationResult;
};

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

export interface ComponentDefinition {
    parameters: UIParameter[] | null;
    returnType: ReturnedType | null;
    // For fragments only
    outputParameters?: string[] | null;
}

export type ComponentsConfig = Record<string, SingleComponentConfig>;

export type FixedValuesPresets = Record<string, FixedValuesOption[]>;

export interface ProcessDefinitionData {
    componentsConfig?: ComponentsConfig;
    components?: Record<string, ComponentDefinition>;
    classes?: TypingResult[];
    componentGroups?: ComponentGroup[];
    scenarioPropertiesConfig?: ScenarioPropertiesConfig;
    edgesForNodes?: EdgesForNode[];
    customActions?: Array<CustomAction>;
    fixedValuesPresets?: FixedValuesPresets;
}

export type EdgesForNode = {
    componentId: string;
    edges: EdgeType[];
    canChooseNodes: boolean;
    isForInputDefinition: boolean;
};
