import { StatusFilterOption } from "../../scenarios/filters/otherOptionsStack";

export interface UsagesFiltersModel {
    TEXT?: string;
    CATEGORY?: string[];
    CREATED_BY?: string[];
    TYPE: string[];
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
