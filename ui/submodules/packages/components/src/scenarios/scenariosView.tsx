import React from "react";
import { FiltersContextProvider } from "../common";
import { useScenariosWithStatus } from "./useScenariosQuery";
import { ScenariosFiltersModel } from "./filters/scenariosFiltersModel";
import { FiltersPart } from "./filters";
import { ListPart } from "./list/listPart";

export function ScenariosView(): JSX.Element {
    const { data = [], isLoading } = useScenariosWithStatus();

    return (
        <FiltersContextProvider<ScenariosFiltersModel>
            getValueLinker={(setNewValue) => (id, value) => {
                switch (id) {
                    case "HIDE_SCENARIOS":
                        return value && setNewValue("HIDE_FRAGMENTS", false);
                    case "HIDE_FRAGMENTS":
                        return value && setNewValue("HIDE_SCENARIOS", false);
                    case "HIDE_ACTIVE":
                        return value && setNewValue("SHOW_ARCHIVED", true);
                    case "SHOW_ARCHIVED":
                        return !value && setNewValue("HIDE_ACTIVE", false);
                }
            }}
        >
            <FiltersPart data={data} />
            <ListPart data={data} isLoading={isLoading} />
        </FiltersContextProvider>
    );
}
