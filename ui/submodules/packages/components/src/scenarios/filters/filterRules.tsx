import { createFilterRules } from "../../common";
import { ScenariosFiltersModel } from "./scenariosFiltersModel";
import { RowType } from "../list/listPart";

export const filterRules = createFilterRules<RowType, ScenariosFiltersModel>({
    NAME: (row, filter) => {
        const text = filter?.toString();
        if (!text?.length) return true;
        const segments = text.trim().split(/\s/);
        return segments.every((segment) =>
            ["id"]
                .map((field) => row[field]?.toString().toLowerCase())
                .filter(Boolean)
                .some((value) => value.includes(segment.toLowerCase())),
        );
    },
    SHOW_ARCHIVED: (row, filter) => filter || !row.isArchived,
    HIDE_ACTIVE: (row, filter) => (filter ? row.isArchived : true),
    HIDE_FRAGMENTS: (row, filter) => (filter ? !row.isSubprocess : true),
    HIDE_SCENARIOS: (row, filter) => (filter ? row.isSubprocess : true),
    HIDE_DEPLOYED: (row, filter) => (filter ? row.lastAction?.action !== "DEPLOY" : true),
    HIDE_NOT_DEPLOYED: (row, filter) => (filter ? row.lastAction?.action === "DEPLOY" : true),
    CATEGORY: (row, value) => !value?.length || [].concat(value).some((f) => row["processCategory"] === f),
    CREATED_BY: (row, value) =>
        !value?.length || [].concat(value).some((f) => row["createdBy"]?.includes(f) || row["modifiedBy"]?.includes(f)),
    STATUS: (row, value) => !value?.length || [].concat(value).some((f) => row["state"]?.status.name.includes(f)),
});
