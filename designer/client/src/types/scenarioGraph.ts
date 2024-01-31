import { EditorProps } from "../components/graph/node-modal/editors/expression/Editor";
import { TypingResult, UIParameter } from "./definition";
import { Edge, EdgeType } from "./edge";
import { NodeType, PropertiesType } from "./node";
import { ComponentGroup } from "./component";
import { ScenarioPropertyConfig } from "../components/graph/node-modal/ScenarioProperty";
import { FixedValuesOption } from "../components/graph/node-modal/fragment-input-definition/item";

export type ScenarioGraphWithName = {
    processName: string;
    scenarioGraph: ScenarioGraph;
};

export type ScenarioGraph = {
    nodes: NodeType[];
    edges: Edge[];
    properties: PropertiesType;
};

export type Category = string;

export type ProcessAdditionalFields = {
    description: string | null;
    properties: { [key: string]: string };
    metaDataType: string;
};

export enum CustomActionDisplayPolicy {
  CurrentlyViewedProcessVersionIsDeployed
}

export type CustomAction = {
    name: string;
    allowedStateStatusNames: Array<string>;
    displayPolicy?: CustomActionDisplayPolicy;
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
