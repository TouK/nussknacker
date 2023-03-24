export interface UsagesFiltersModel {
    TEXT?: string;
    CATEGORY?: string[];
    CREATED_BY?: string[];
    HIDE_FRAGMENTS?: boolean; // TODO: apply the same filtering approach as is in ScenariosFiltersModel
    HIDE_SCENARIOS?: boolean;
    STATUS?: string[];
}
