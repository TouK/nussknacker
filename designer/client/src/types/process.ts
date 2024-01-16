import { EditorProps } from "../components/graph/node-modal/editors/expression/Editor";
import { TypingResult, UIParameter } from "./definition";
import { Edge, EdgeType } from "./edge";
import { NodeType, PropertiesType } from "./node";
import { ValidationResult } from "./validation";
import { ComponentGroup } from "./component";
import { ProcessingType } from "../actions/nk";
import { ScenarioPropertyConfig } from "../components/graph/node-modal/ScenarioProperty";
import { FixedValuesOption } from "../components/graph/node-modal/fragment-input-definition/item";

export type Process = {
    name: string;
    nodes: NodeType[];
    edges: Edge[];
    properties: PropertiesType;
    validationResult: ValidationResult;
    processingType?: ProcessingType;
    category?: string; // optional - see the comment for a field with the same name in DisplayableProcess.scala
};

export type ProcessName = Process["name"];

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
    parameters: UIParameter[];
    returnType: ReturnedType | null;
    icon: string;
    docsUrl?: string;
    // For fragments only
    outputParameters?: string[] | null;
}

export type FixedValuesPresets = Record<string, FixedValuesOption[]>;

export interface ProcessDefinitionData {
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
