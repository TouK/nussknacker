import React from "react";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import i18next from "i18next";
import { Box, IconButton, styled, Typography } from "@mui/material";
import { isEmpty } from "lodash";
import { SearchIcon } from "../../table/SearchFilter";
import { ExpandLess, ExpandMore } from "@mui/icons-material";

export const StyledIconButton = styled(IconButton)(() => ({
    padding: 0,
    "&:focus-within": {
        outline: 0,
    },
}));

interface Props {
    handleSearch: (value: string) => void;
    searchQuery: string;
    selectedResult: number;
    foundResults: string[];
    changeResult: (index: number) => void;
    handleClearResults: () => void;
}

export const ActivitiesSearch = ({ handleSearch, searchQuery, selectedResult, foundResults, changeResult, handleClearResults }: Props) => {
    const areResults = foundResults.length > 0;

    return (
        <>
            <SearchInputWithIcon
                placeholder={i18next.t("activities.search.placeholder", "type here to find past event")}
                onChange={handleSearch}
                value={searchQuery}
                onClear={handleClearResults}
                onKeyDown={(e) => {
                    if (e.key === "Enter") {
                        changeResult(selectedResult + 1);
                    }
                }}
            >
                <SearchIcon isEmpty={isEmpty(searchQuery)} />
            </SearchInputWithIcon>
            <Box display={"flex"} alignItems={"center"} justifyContent={"flex-end"}>
                {searchQuery && (
                    <>
                        <Typography
                            variant={"overline"}
                            sx={() => ({
                                color: "secondary",
                                mr: 1,
                            })}
                        >
                            {areResults ? `Result ${selectedResult + 1}/${foundResults.length}` : "Result 0"}
                        </Typography>
                        <StyledIconButton disabled={!areResults} color={"inherit"} onClick={() => changeResult(selectedResult - 1)}>
                            <ExpandLess />
                        </StyledIconButton>
                        <StyledIconButton disabled={!areResults} color={"inherit"} onClick={() => changeResult(selectedResult + 1)}>
                            <ExpandMore />
                        </StyledIconButton>
                    </>
                )}
            </Box>
        </>
    );
};
