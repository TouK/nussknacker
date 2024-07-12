import { createFilterRules } from "../common";
import { ComponentType } from "nussknackerUi/HttpService";
import { ComponentsFiltersModel } from "./filters";

export const filterRules = createFilterRules<ComponentType, ComponentsFiltersModel>({
    NAME: (row, value) => {
        const text = value?.toString().toLowerCase().trim();
        if (!text?.length) return true;
        const segments = text.split(/\s/);
        return segments.every((segment) => row["name"]?.toLowerCase().includes(segment));
    },
    GROUP: (row, value) => !value?.length || [].concat(value).some((f) => row["componentGroupName"]?.includes(f)),
    CATEGORY: (row, value) => !value?.length || [].concat(value).some((f) => row["categories"]?.includes(f)),
    USAGES: (row, values = []) => {
        return [].concat(values).every((value) => {
            if (value === 0) {
                return row["usageCount"] === 0;
            }
            if (value > 0) {
                return row["usageCount"] >= value;
            }
            if (value < 0) {
                return row["usageCount"] < Math.abs(value);
            }
            return true;
        });
    },
    PROCESSING_MODE: (row, value) => {
        return !value?.length || [].concat(value).some((f) => row["allowedProcessingModes"]?.includes(f));
    },
});
