import { ProcessAdditionalFields, ReturnedType } from "./scenarioGraph";
import { FragmentInputParameter } from "../components/graph/node-modal/fragment-input-definition/item";

type Type = "Properties" | "FragmentInput" | string;

export type LayoutData = { x: number; y: number };

export interface BranchParams {
    branchId: string;
    parameters: Field[];
}

export type BranchParametersTemplate = $TodoType;

//FIXME: something wrong here, process and node mixed?
export type NodeType<F extends Field = Field> = {
    id: string;
    type: Type;
    isDisabled?: boolean;
    additionalFields?: {
        description: string;
        layoutData?: LayoutData;
    };
    parameters?: Parameter[];
    branchParameters?: BranchParams[];
    branchParametersTemplate?: BranchParametersTemplate;
    ref?: {
        id: string;
        typ: string;
        parameters: Parameter[];
        outputVariableNames: Record<string, string>;
    };
    varName?: string;
    value?: $TodoType;
    fields?: Array<F>;
    outputName?: string;
    service?: {
        id: string;
        parameters?: $TodoType[];
    };
    nodeType: string;
    [key: string]: any;
};

export type FragmentNodeType = NodeType;

export type Field = Parameter | FragmentInputParameter;

export interface Parameter {
    uuid: string;
    name: string;
    expression: Expression;
    typ?: ReturnedType;
    hintText?: string;
}

export interface Expression {
    language: string;
    expression: string;
}

//TODO: Add other process properties...
export type PropertiesType = NodeType & {
    type: "Properties";
    additionalFields: ProcessAdditionalFields;
};

export type NodeId = NodeType["id"];

export type UINodeType = NodeType;
