import React, { Dispatch, SetStateAction } from "react";
import { CategoriesFilter } from "../components/table/CategoriesFilter";
import { StatusFilter } from "../components/table/StatusFilter";
import SearchFilter from "../components/table/SearchFilter";
import { FragmentFilter } from "../components/table/FragmentFilter";
import { ensureArray } from "../common/arrayUtils";

export enum SearchItem {
    categories = "categories",
    isFragment = "isFragment",
    isDeployed = "isDeployed",
}

export type CategoryName = string;

export type FiltersState = Partial<{
    search: string;
    categories: CategoryName[];
    isFragment: boolean;
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

            {filters.includes(SearchItem.isFragment) && (
                <FragmentFilter onChange={(isFragment) => onChange((s) => ({ ...s, isFragment }))} value={value.isFragment} />
            )}

            {filters.includes(SearchItem.isDeployed) && (
                <StatusFilter onChange={(isDeployed) => onChange((s) => ({ ...s, isDeployed }))} value={value.isDeployed} />
            )}
        </>
    );
}
