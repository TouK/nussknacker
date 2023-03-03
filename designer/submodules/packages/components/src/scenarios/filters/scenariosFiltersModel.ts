import { SortableFiltersModel } from "./common/sortableFiltersModel";

export interface ScenariosFiltersModel extends SortableFiltersModel {
    NAME?: string;
    CATEGORY?: string[];
    ARCHIVED?: boolean;
    TYPE?: string[];
    DEPLOYED?: string[];
    CREATED_BY?: string[];
    STATUS?: string[];
}

export enum ScenariosFiltersModelDeployed {
    DEPLOYED = "DEPLOYED",
    NOT_DEPLOYED = "NOT_DEPLOYED",
}

export enum ScenariosFiltersModelType {
    SCENARIOS = "SCENARIOS",
    FRAGMENTS = "FRAGMENTS",
}
