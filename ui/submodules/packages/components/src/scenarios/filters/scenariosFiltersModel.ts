import { SortKey } from "../list/itemsList";

export interface ScenariosFiltersModel {
    NAME?: string;
    CATEGORY?: string[];
    SHOW_ARCHIVED?: boolean;
    HIDE_ACTIVE?: boolean;
    HIDE_FRAGMENTS?: boolean;
    HIDE_SCENARIOS?: boolean;
    HIDE_DEPLOYED?: boolean;
    HIDE_NOT_DEPLOYED?: boolean;
    CREATED_BY?: string[];
    STATUS?: string[];
    SORT_BY?: SortKey;
}
