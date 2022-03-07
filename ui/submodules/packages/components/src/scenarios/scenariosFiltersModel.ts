export interface ScenariosFiltersModel {
    NAME?: string;
    CATEGORY?: string[];
    SHOW_ARCHIVED?: boolean;
    HIDE_ACTIVE?: boolean;
    HIDE_FRAGMENTS?: boolean;
    HIDE_SCENARIOS?: boolean;
    CREATED_BY?: string[];
    STATUS?: string[];
    SORT_BY?: `${string}!${"asc" | "desc"}`;
}
