import { ExpressionObj } from "../components/graph/node-modal/editors/expression/types";
import { Opaque, TaggedUnion } from "type-fest";
import { TypingResult } from "./typingResult";
import { EditorProps } from "./parameterEditorProps";
import { TypedProcessType } from "./displayableProcess";

type ComponentGroupName = Opaque<string, "ComponentGroupName">;

enum BaseComponentType {
    Filter = "filter",
    Split = "split",
    Switch = "switch",
    Variable = "variable",
    MapVariable = "mapVariable",
}

enum GenericComponentType {
    Processor = "processor",
    Enricher = "enricher",
    Sink = "sink",
    Source = "source",
    Fragments = "fragments",
    CustomNode = "customNode",
}

enum FragmentComponentType {
    FragmentInput = "input",
    FragmentOutput = "output",
}

type ComponentType = BaseComponentType | GenericComponentType | FragmentComponentType | string;

type LayoutData = {
    x: number;
    y: number;
};

type UserDefinedAdditionalNodeFields = {
    description?: string;
    layoutData?: LayoutData;
};

type NodeData = {
    id: Opaque<string, NodeData>;
    additionalFields?: UserDefinedAdditionalNodeFields;
};

type Disableable = {
    isDisabled?: boolean;
};

type Expression = ExpressionObj;

type Parameter = {
    name: string;
    expression: Expression;
};

type CustomNodeData = NodeData & {
    nodeType: string;
    outputVar?: string;
};

type SourceRef = {
    typ: string;
    parameters: Parameter[];
};

type Source = NodeData & {
    ref: SourceRef;
};

type BranchParameters = {
    branchId: string;
    parameters: Parameter[];
};

type Join = CustomNodeData & {
    branchParameters: BranchParameters[];
};

type Filter = NodeData &
    Disableable & {
        expression: Expression;
    };

type Switch = NodeData & {
    /** @deprecated */
    expression?: Expression;
    /** @deprecated */
    exprVal?: string;
};

type Field = {
    name: string;
    expression: Expression;
};

type VariableBuilder = NodeData & {
    varName: string;
    fields: Field[];
};

type Variable = NodeData & {
    varName: string;
    value: Expression;
};

type Split = NodeData;

type ServiceRef = {
    id: Opaque<string, ServiceRef>;
    parameters: Parameter[];
};

type Enricher = NodeData & {
    service: ServiceRef;
    output: string;
};

type CustomNode = CustomNodeData & {
    parameters: Parameter[];
};

type Processor = NodeData &
    Disableable & {
        service: ServiceRef;
    };

type SinkRef = {
    typ: string;
    parameters: Parameter[];
};

type Sink = NodeData &
    Disableable & {
        ref: SinkRef;
        endResult?: Expression;
    };

type FragmentRef = {
    id: Opaque<string, FragmentRef>;
    parameters: Parameter[];
    outputVariableNames: Record<string, string>;
};

type FragmentClazzRef = {
    refClazzName: string;
};

type FragmentParameter = {
    name: string;
    typ: FragmentClazzRef;
};

export type FragmentInput = NodeData &
    Disableable & {
        ref: FragmentRef;
        fragmentParams?: FragmentParameter[];
    };

type FragmentInputDefinition = NodeData & {
    parameters: FragmentParameter[];
};

type FragmentOutputDefinition = NodeData & {
    outputName: string;
    fields: Field[];
};

export type Category = TypedProcessType["processCategory"];

export type NodeDataTaggedUnion = TaggedUnion<
    "type",
    {
        Source: Source;
        Join: Join;
        Filter: Filter;
        Switch: Switch;
        VariableBuilder: VariableBuilder;
        Variable: Variable;
        Split: Split;
        Enricher: Enricher;
        CustomNode: CustomNode;
        Processor: Processor;
        Sink: Sink;
        FragmentInput: FragmentInput;
        FragmentInputDefinition: FragmentInputDefinition;
        FragmentOutputDefinition: FragmentOutputDefinition;
    }
>;

export type ComponentTemplate = {
    branchParametersTemplate: Parameter[];
    categories: Category[];
    label: string;
    node: NodeDataTaggedUnion;
    type: ComponentType;
};

export type ComponentGroup = {
    name: ComponentGroupName;
    components: ComponentTemplate[];
};

type ParameterEditor = EditorProps;

export enum BackendValidator {
    MandatoryParameterValidator = "MandatoryParameterValidator",
    NotBlankParameterValidator = "NotBlankParameterValidator",
    FixedValuesValidator = "FixedValuesValidator",
    RegExpParameterValidator = "RegExpParameterValidator",
    LiteralIntegerValidator = "LiteralIntegerValidator",
    MinimalNumberValidator = "MinimalNumberValidator",
    MaximalNumberValidator = "MaximalNumberValidator",
    JsonValidator = "JsonValidator",
}

export interface ParameterValidator {
    type: BackendValidator | string;
}

export type UIParameter = {
    name: string;
    typ: TypingResult;
    editor: ParameterEditor;
    validators: ParameterValidator[];
    defaultValue: Expression;
    additionalVariables: Record<string, TypingResult>;
    variablesToHide: string[];
    branchParam?: boolean;
};

export type ParameterConfig = {
    defaultValue?: string;
    editor?: ParameterEditor;
    validators?: ParameterValidator[];
    label?: string;
};

type ComponentId = Opaque<string, "ComponentId">;

type SingleComponentConfig = {
    params?: Record<string, ParameterConfig>;
    icon?: string;
    docsUrl?: string;
    componentGroup?: ComponentGroupName;
    componentId?: ComponentId;
    disabled?: boolean;
};

export type UIObjectDefinition = {
    parameters: UIParameter[];
    returnType?: TypingResult;
    categories: Category[];
    componentConfig: SingleComponentConfig;
};

type UIBasicParameter = {
    name: string;
    refClazz: TypingResult;
};

type UIMethodInfo = {
    parameters: UIBasicParameter[];
    refClazz: TypingResult;
    description?: string;
    varArgs?: boolean;
};

type UIClazzDefinition = {
    clazzName: TypingResult;
    methods: Record<string, UIMethodInfo>;
    staticMethods: Record<string, UIMethodInfo>;
};

type UIFragmentObjectDefinition = {
    parameters: UIParameter[];
    outputParameters: string[];
    returnType?: TypingResult;
    categories: Category[];
    componentConfig: SingleComponentConfig;
};

export type UIProcessDefinition = {
    services: Record<string, UIObjectDefinition>;
    sourceFactories: Record<string, UIObjectDefinition>;
    sinkFactories: Record<string, UIObjectDefinition>;
    customStreamTransformers: Record<string, UIObjectDefinition>;
    globalVariables: Record<string, UIObjectDefinition>;
    typesInformation: UIClazzDefinition[];
    fragmentInputs: Record<string, UIFragmentObjectDefinition>;
};

export type UiAdditionalPropertyConfig = {
    defaultValue?: string;
    editor: ParameterEditor;
    validators: ParameterValidator[];
    label?: string;
};

type NodeTypeId = {
    type: string;
    id?: string;
};

type EdgeType = NonNullable<unknown>;
type FilterEdge = EdgeType;
type FilterTrue = FilterEdge;
type FilterFalse = FilterEdge;
type SwitchEdge = EdgeType;
type NextSwitch = SwitchEdge & {
    condition: Expression;
};
type SwitchDefault = SwitchEdge;
type FragmentOutput = EdgeType & {
    name: string;
};

export enum EdgeKind {
    filterFalse = "FilterFalse",
    filterTrue = "FilterTrue",
    switchDefault = "SwitchDefault",
    switchNext = "NextSwitch",
    fragmentOutput = "FragmentOutput",
}

export type EdgeTypeTaggedUnion = TaggedUnion<
    "type",
    {
        [EdgeKind.filterTrue]: FilterTrue;
        [EdgeKind.filterFalse]: FilterFalse;
        [EdgeKind.switchNext]: NextSwitch;
        [EdgeKind.switchDefault]: SwitchDefault;
        [EdgeKind.fragmentOutput]: FragmentOutput;
    }
>;
type NodeEdges = {
    nodeId: NodeTypeId;
    edges: EdgeTypeTaggedUnion[];
    canChooseNodes?: boolean;
    isForInputDefinition?: boolean;
};

export type URI = Opaque<string, "URI">;

type UICustomActionParameter = {
    name: string;
    editor: ParameterEditor;
};

type UICustomAction = {
    name: string;
    allowedStateStatusNames: string[];
    icon?: URI;
    parameters: UICustomActionParameter[];
};

export type UIProcessObjects = {
    componentGroups: ComponentGroup[];
    processDefinition: UIProcessDefinition;
    componentsConfig: Record<string, SingleComponentConfig>;
    additionalPropertiesConfig: Record<string, UiAdditionalPropertyConfig>;
    edgesForNodes: NodeEdges[];
    customActions: UICustomAction[];
    defaultAsyncInterpretation: boolean;
};
