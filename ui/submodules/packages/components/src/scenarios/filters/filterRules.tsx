import { createFilterRules } from "../../common";
import { ScenariosFiltersModel } from "./scenariosFiltersModel";
import { RowType } from "../list/listPart";

export const filterRules = createFilterRules<RowType, ScenariosFiltersModel>({
    NAME: (row, filter) => {
        const text = filter?.toString();
        if (!text?.length) return true;
        const segments = text.split(/\s/);
        return ["id"]
            .map((field) => row[field]?.toString().toLowerCase())
            .filter(Boolean)
            .some((value) => segments.every((t) => value.includes(t.toLowerCase())));
    },
    SHOW_ARCHIVED: (row, filter) => filter || !row.isArchived,
    HIDE_ACTIVE: (row, filter) => (filter ? row.isArchived : true),
    HIDE_FRAGMENTS: (row, filter) => (filter ? !row.isSubprocess : true),
    HIDE_SCENARIOS: (row, filter) => (filter ? row.isSubprocess : true),
    CATEGORY: (row, value) => !value?.length || [].concat(value).some((f) => row["processCategory"] === f),
    CREATED_BY: (row, value) => !value?.length || [].concat(value).some((f) => row["createdBy"]?.includes(f)),
    STATUS: (row, value) => !value?.length || [].concat(value).some((f) => row["state"]?.status.name.includes(f)),
});
