import React, { Dispatch, SetStateAction } from "react";
import { CategoriesFilter } from "../components/table/CategoriesFilter";
import { StatusFilter } from "../components/table/StatusFilter";
import SearchFilter from "../components/table/SearchFilter";
import { SubprocessFilter } from "../components/table/SubprocessFilter";
import { ensureArray } from "../common/arrayUtils";

export enum SearchItem {
    categories = "categories",
    isSubprocess = "isSubprocess",
    isDeployed = "isDeployed",
}

export type CategoryName = string;

export type FiltersState = Partial<{
    search: string;
    categories: CategoryName[];
    isSubprocess: boolean;
    isDeployed: boolean;
}>;

type Props = {
    filters: SearchItem[];
    value: FiltersState;
    onChange: Dispatch<SetStateAction<FiltersState>>;
};

export function TableFilters(props: Props): JSX.Element {
    const { filters = [] } = props;
    const { value, onChange } = props;

    return (
        <>
            <SearchFilter onChange={(search) => onChange((s) => ({ ...s, search }))} value={value.search} />

            {filters.includes(SearchItem.categories) && (
                <CategoriesFilter
                    onChange={(categories) => onChange((s) => ({ ...s, categories }))}
                    value={ensureArray(value.categories)}
                />
            )}

            {filters.includes(SearchItem.isSubprocess) && (
                <SubprocessFilter onChange={(isSubprocess) => onChange((s) => ({ ...s, isSubprocess }))} value={value.isSubprocess} />
            )}

            {filters.includes(SearchItem.isDeployed) && (
                <StatusFilter onChange={(isDeployed) => onChange((s) => ({ ...s, isDeployed }))} value={value.isDeployed} />
            )}
        </>
    );
}
