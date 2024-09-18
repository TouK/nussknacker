import { SortableFiltersModel } from "./common/sortableFiltersModel";
import { ProcessingMode } from "../list/processingMode";

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
