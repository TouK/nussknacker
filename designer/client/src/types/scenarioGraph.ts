import { TypingResult, UIParameter } from "./definition";
import { Edge, EdgeType } from "./edge";
import { NodeType, PropertiesType } from "./node";
import { ComponentGroup } from "./component";
import { ScenarioPropertyConfig } from "../components/properties/ScenarioProperty";

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
    showDescription?: boolean;
};

export interface UiScenarioProperties {
    propertiesConfig: { [key: string]: ScenarioPropertyConfig };
    docsUrl?: string;
}
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

export interface ProcessDefinitionData {
    components?: Record<string, ComponentDefinition>;
    classes?: TypingResult[];
    componentGroups?: ComponentGroup[];
    scenarioProperties?: UiScenarioProperties;
    edgesForNodes?: EdgesForNode[];
}

export type EdgesForNode = {
    componentId: string;
    edges: EdgeType[];
    canChooseNodes: boolean;
    isForInputDefinition: boolean;
};
