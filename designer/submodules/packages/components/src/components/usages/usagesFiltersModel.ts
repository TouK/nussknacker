import type { StatusFilterOption } from "../../scenarios/filters/typeOptionsStack";
import type { ProcessingMode } from "../../scenarios/list/processingMode";

export interface UsagesFiltersModel {
    TEXT?: string;
    CATEGORY?: string[];
    CREATED_BY?: string[];
    TYPE?: UsagesFiltersModelType[];
    USAGE_TYPE?: UsagesFiltersUsageType[];
    STATUS?: string[];
    PROCESSING_MODE?: ProcessingMode[];
}

export interface UsagesFiltersValues {
    CATEGORY?: { name: string }[];
    CREATED_BY?: { name: string }[];
    STATUS?: StatusFilterOption[];
}

export enum UsagesFiltersModelType {
    SCENARIOS = "SCENARIOS",
    FRAGMENTS = "FRAGMENTS",
}

export enum UsagesFiltersUsageType {
    INDIRECT = "INDIRECT",
    DIRECT = "DIRECT",
}

export type UsagesFilterKey = keyof Required<UsagesFiltersModel>;

export const USAGES_FILTER: { [K in UsagesFilterKey]: K } = {
    TEXT: "TEXT",
    CATEGORY: "CATEGORY",
    CREATED_BY: "CREATED_BY",
    TYPE: "TYPE",
    USAGE_TYPE: "USAGE_TYPE",
    STATUS: "STATUS",
    PROCESSING_MODE: "PROCESSING_MODE",
} as const;
