import { createFilterRules } from "../../common";
import { ScenariosFiltersModel, ScenariosFiltersModelType } from "./scenariosFiltersModel";
import { RowType } from "../list/listPart";

export const filterRules = createFilterRules<RowType, ScenariosFiltersModel>({
    NAME: (row, filter) => {
        const text = filter?.toString();
        if (!text?.length) return true;
        const segments = text.trim().split(/\s/);
        return segments.every((segment) =>
            ["name"]
                .map((field) => row[field]?.toString().toLowerCase())
                .filter(Boolean)
                .some((value) => value.includes(segment.toLowerCase())),
        );
    },
    ARCHIVED: (row, filter) => (filter ? row.isArchived : !row.isArchived),
    TYPE: (row, value) =>
        !value?.length ||
        []
            .concat(value)
            .some(
                (f) =>
                    (f === ScenariosFiltersModelType.SCENARIOS && !row.isFragment) ||
                    (f === ScenariosFiltersModelType.FRAGMENTS && row.isFragment),
            ),
    CATEGORY: (row, value) => !value?.length || [].concat(value).some((f) => row["processCategory"] === f),
    CREATED_BY: (row, value) =>
        !value?.length || [].concat(value).some((f) => row["createdBy"]?.includes(f) || row["modifiedBy"]?.includes(f)),
    STATUS: (row, value) => !value?.length || [].concat(value).some((f) => row["state"]?.status.name.includes(f)),
    PROCESSING_MODE: (row, value) => !value?.length || [].concat(value).some((f) => row["processingMode"].includes(f)),
    LABEL: (row, value) => !value?.length || [].concat(value).some((f) => (row["labels"] ? row["labels"].includes(f) : false)),
});
