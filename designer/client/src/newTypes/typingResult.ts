import { TaggedUnion } from "type-fest";

type TypingResultBase = {
    display: string;
};

type TypedClass = TypingResultBase & {
    refClazzName: string;
    params: TypingResult[];
};

type TypedObjectTypingResult = TypedClass & {
    fields: Record<string, TypingResult>;
    additionalInfo?: Record<string, string | number | boolean>;
};

type TypedDict = TypingResultBase & {
    dict: {
        id: string;
        valueType: TypedObjectTypingResult | TypedClass | TypedTaggedValue | TypedObjectWithValue | TypedDict;
    };
};

type TypedTaggedValue = (TypedObjectTypingResult | TypedDict | TypedClass | TypedObjectWithValue) & {
    tag: string;
};

type TypedObjectWithValue = TypedClass & {
    value: number | boolean | string;
};

type TypedNull = TypingResultBase & {
    refClazzName: string;
    params: [];
};

type Unknown = TypingResultBase & {
    refClazzName: string;
    params: [];
};

type UnionTyping = TypingResultBase & {
    union: TypingResult[];
};

export enum TypingResultType {
    TypedClass = "TypedClass",
    TypedDict = "TypedDict",
    TypedNull = "TypedNull",
    TypedObjectTypingResult = "TypedObjectTypingResult",
    TypedObjectWithValue = "TypedObjectWithValue",
    TypedTaggedValue = "TypedTaggedValue",
    TypedUnion = "UnionTyping",
    Unknown = "Unknown",
}

export type TypingResult = TaggedUnion<
    "type",
    {
        [TypingResultType.TypedClass]: TypedClass;
        [TypingResultType.TypedDict]: TypedDict;
        [TypingResultType.TypedNull]: TypedNull;
        [TypingResultType.TypedObjectTypingResult]: TypedObjectTypingResult;
        [TypingResultType.TypedObjectWithValue]: TypedObjectWithValue;
        [TypingResultType.TypedTaggedValue]: TypedTaggedValue;
        [TypingResultType.TypedUnion]: UnionTyping;
        [TypingResultType.Unknown]: Unknown;
    }
>;
