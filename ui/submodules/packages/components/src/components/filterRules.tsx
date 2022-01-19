import { createFilterRules } from "../common";
import { ComponentType } from "nussknackerUi/HttpService";
import { ComponentsFiltersModel } from "./filters";

export const filterRules = createFilterRules<ComponentType, ComponentsFiltersModel>({
    NAME: (row, value) => !value?.length || row["name"]?.toLowerCase().includes(value),
    GROUP: (row, value) => !value?.length || [].concat(value).some((f) => row["componentGroupName"]?.includes(f)),
    CATEGORY: (row, value) => !value?.length || [].concat(value).every((f) => row["categories"]?.includes(f)),
    UNUSED_ONLY: (row, value) => (value ? row["usageCount"] === 0 : true),
    USED_ONLY: (row, value) => (value ? row["usageCount"] > 0 : true),
});
