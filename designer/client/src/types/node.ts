import type { FragmentInputParameter } from "../components/graph/node-modal/fragment-input-definition/item/types";
import type { StickyNoteType } from "../components/graph/utils/stickyNotesUtils";
import type { ProcessAdditionalFields, ReturnedType } from "./scenarioGraph";
import type { NodeValidationError } from "./validation";

type Type = "FragmentInput" | typeof StickyNoteType | "Source" | "Sink" | "Enricher" | string;

export type LayoutData = { x: number; y: number };

type UnsafeOptions = Record<`_unsafe_${string}`, unknown>;

export interface BranchParams {
    branchId: string;
    parameters: Field[];
}

export type BranchParametersTemplate = $TodoType;

export type NodeType<F extends Field = Field> = {
    id: string;
    name: string;
    type: Type;
    isDisabled?: boolean;
    additionalFields?: {
        description?: string;
        layoutData?: LayoutData;
        creatorType?: string;
    } & UnsafeOptions;
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
        parameters?: Parameter[];
    };
    nodeType?: string;
    //TODO: Remove me and add correct properties
    [key: string]: any;
};

export type Dimensions = { width: number; height: number };

export type ColorValueHex = `#${string}`;

export type StickyNoteNodeType = NodeType & {
    content: string;
    dimensions: Dimensions;
    color: ColorValueHex;
    errors?: NodeValidationError[];
};

export type FragmentNodeType = NodeType;

export type Field = Parameter | FragmentInputParameter;

export interface Parameter {
    uuid?: string;
    name: string;
    expression: Expression;
    typ?: ReturnedType;
    hintText?: string;
}

export interface Expression {
    language: string;
    expression: string;
}

export type PropertiesType = {
    name?: string;
    additionalFields: ProcessAdditionalFields;
};

export type NodeId = NodeType["id"] & NonNullable<unknown>;

export type NodeOrPropertiesType = NodeType | PropertiesType;
