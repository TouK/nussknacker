import type { ProcessingMode } from "../list/processingMode";
import type { SortableFiltersModel } from "./common/sortableFiltersModel";

export interface ScenariosFiltersModel extends SortableFiltersModel {
    NAME?: string;
    CATEGORY?: string[];
    ARCHIVED?: boolean;
    TYPE?: string[];
    CREATED_BY?: string[];
    STATUS?: string[];
    LABEL?: string[];
    PROCESSING_MODE?: ProcessingMode[];
}

export enum ScenariosFiltersModelType {
    SCENARIOS = "SCENARIOS",
    FRAGMENTS = "FRAGMENTS",
}

export type ScenariosFilterKey = keyof Required<ScenariosFiltersModel>;

export const SCENARIOS_FILTER: { [K in ScenariosFilterKey]: K } = {
    NAME: "NAME",
    CATEGORY: "CATEGORY",
    ARCHIVED: "ARCHIVED",
    TYPE: "TYPE",
    CREATED_BY: "CREATED_BY",
    STATUS: "STATUS",
    LABEL: "LABEL",
    PROCESSING_MODE: "PROCESSING_MODE",
    SORT_BY: "SORT_BY",
} as const;
