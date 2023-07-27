import { StatusFilterOption } from "../../scenarios/filters/otherOptionsStack";

export interface UsagesFiltersModel {
    TEXT?: string;
    CATEGORY?: string[];
    CREATED_BY?: string[];
    TYPE?: UsagesFiltersModelType[];
    USAGE_TYPE?: UsagesFiltersUsageType[];
    STATUS?: string[];
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
    STRAIGHT = "STRAIGHT",
}
