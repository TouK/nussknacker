import { SortableFiltersModel } from "./common/sortableFiltersModel";

export interface ScenariosFiltersModel extends SortableFiltersModel {
    NAME?: string;
    CATEGORY?: string[];
    ARCHIVED?: boolean;
    HIDE_FRAGMENTS?: boolean;
    HIDE_SCENARIOS?: boolean;
    DEPLOYED?: string[];
    CREATED_BY?: string[];
    STATUS?: string[];
}

export enum ScenariosFiltersModelDeployed {
    DEPLOYED = "DEPLOYED",
    NOT_DEPLOYED = "NOT_DEPLOYED",
}
