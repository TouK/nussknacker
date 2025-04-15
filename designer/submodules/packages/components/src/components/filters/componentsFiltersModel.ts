import type { ProcessingMode } from "../../scenarios/list/processingMode";

export interface ComponentsFiltersModel {
    NAME?: string;
    GROUP?: string[];
    CATEGORY?: string[];
    SHOW_ARCHIVED?: boolean;
    USAGES?: number[];
    PROCESSING_MODE: ProcessingMode[];
}

export type ComponentsFilterKey = keyof Required<ComponentsFiltersModel>;

export const COMPONENTS_FILTER: { [K in ComponentsFilterKey]: K } = {
    NAME: "NAME",
    GROUP: "GROUP",
    CATEGORY: "CATEGORY",
    SHOW_ARCHIVED: "SHOW_ARCHIVED",
    USAGES: "USAGES",
    PROCESSING_MODE: "PROCESSING_MODE",
} as const;
