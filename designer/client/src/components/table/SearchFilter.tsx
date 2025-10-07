import { styled } from "@mui/material";
import React from "react";

import AdvancedSearchSvg from "../../assets/img/advanced-search.svg";
import SearchSvg from "../../assets/img/search.svg";
import DeleteSvg from "../../assets/img/toolbarButtons/delete.svg";

const flex = {
    width: 0, // edge 18. why? because! 🙃
    flex: 1,
};

const AdvancedSearchIcon = styled(AdvancedSearchSvg)<{ isActive?: boolean }>(({ theme, isActive }) => ({
    ...flex,
    cursor: "pointer",
    transform: "scale(1.5)",
    path: {
        fill: "none",
        stroke: isActive ? theme.palette.primary.main : theme.palette.text.secondary,
    },
}));

export const AdvancedOptionsIcon = ({
    collapseHandler,
    isActive,
}: {
    isActive?: boolean;
    collapseHandler: React.Dispatch<React.SetStateAction<boolean>>;
}) => (
    <AdvancedSearchIcon
        id="advanced-search-icon"
        onClick={() => {
            collapseHandler((p) => !p);
        }}
        isActive={isActive}
    />
);

export const SearchIcon = styled(SearchSvg)<{ isEmpty?: boolean }>(({ isEmpty, theme }) => ({
    ...flex,
    transform: "scale(0.8)",
    path: {
        fill: isEmpty ? theme.palette.text.secondary : theme.palette.primary.main,
    },
}));

export const ClearIcon = styled(DeleteSvg)(({ theme }) => ({
    ...flex,
    path: {
        fill: theme.palette.text.secondary,
    },
}));
