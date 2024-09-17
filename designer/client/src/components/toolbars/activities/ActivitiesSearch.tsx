import React from "react";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import i18next from "i18next";
import { Box } from "@mui/material";

interface Props {
    handleSearch: (value: string) => void;
    searchQuery: string;
    selectedResult: number;
    foundResults: string[];
    changeResult: (index: number) => void;
}

export const ActivitiesSearch = ({ handleSearch, searchQuery, selectedResult, foundResults, changeResult }: Props) => {
    return (
        <>
            <SearchInputWithIcon
                placeholder={i18next.t("activities.search.placeholder", "type here to find past event")}
                onChange={handleSearch}
                value={searchQuery}
            />
            <Box>
                Results {selectedResult + 1}/{foundResults.length}
                <div onClick={() => changeResult(selectedResult - 1)}>-</div>
                <div onClick={() => changeResult(selectedResult + 1)}>+</div>
            </Box>
        </>
    );
};
