import { ProcessingMode } from "../../scenarios/list/processingMode";

export interface ComponentsFiltersModel {
    NAME?: string;
    GROUP?: string[];
    CATEGORY?: string[];
    SHOW_ARCHIVED?: boolean;
    USAGES?: number[];
    PROCESSING_MODE: ProcessingMode[];
}
