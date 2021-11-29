import { ComponentType } from "nussknackerUi/HttpService";

export type Row = Record<string, any>;

interface FilterRule<R extends Row, V = any> {
    (row: R, value: V): boolean;
}

type FilterRules<R extends Row, M extends Record<string, any>> = {
    [key in keyof M]: FilterRule<R, M[key]>;
};

export interface FiltersModel {
    NAME?: string;
    GROUP?: string[];
    CATEGORY?: string[];
    UNUSED_ONLY?: boolean;
    USED_ONLY?: boolean;
}

export const FILTER_RULES: FilterRules<ComponentType, FiltersModel> = {
    NAME: (row, value) => !value.length || row["name"]?.toLowerCase().includes(value),
    GROUP: (row, value = []) => !value.length || [].concat(value).some((f) => row["componentGroupName"]?.includes(f)),
    CATEGORY: (row, value = []) => !value.length || [].concat(value).every((f) => row["categories"]?.includes(f)),
    UNUSED_ONLY: (row, value) => value && row["usageCount"] === 0,
    USED_ONLY: (row, value) => value && row["usageCount"] > 0,
};
