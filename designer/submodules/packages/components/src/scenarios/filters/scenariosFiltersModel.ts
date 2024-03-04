import { SortableFiltersModel } from "./common/sortableFiltersModel";

export interface ScenariosFiltersModel extends SortableFiltersModel {
    NAME?: string;
    CATEGORY?: string[];
    ARCHIVED?: boolean;
    TYPE?: string[];
    CREATED_BY?: string[];
    STATUS?: string[];
    PROCESSING_MODE?: string[];
}

export enum ScenariosFiltersModelType {
    SCENARIOS = "SCENARIOS",
    FRAGMENTS = "FRAGMENTS",
}
