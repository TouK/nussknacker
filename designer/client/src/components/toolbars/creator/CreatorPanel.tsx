import { css } from "@emotion/css";
import { isEmpty } from "lodash";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { SearchIcon } from "../../table/SearchFilter";
import { InputWithIcon } from "../../themed/InputWithIcon";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import ToolBox from "./ToolBox";
import { styled } from "@mui/material";

export function CreatorPanel(): JSX.Element {
    const { t } = useTranslation();

    const SearchInputWithIcon = styled(InputWithIcon)(({ theme }) => ({
        borderRadius: 0,
        height: "36px !important",
        color: "#FFFFFF",
        padding: "6px 12px !important",
        backgroundColor: `${theme.palette.background.paper} !important`,
        border: "none",
        outline: "none !important",
        "&:focus": {
            boxShadow: "none",
        },
    }));

    const [filter, setFilter] = useState("");
    const clearFilter = useCallback(() => setFilter(""), []);

    return (
        <ToolbarWrapper id="creator-panel" title={t("panels.creator.title", "Creator panel")}>
            <SearchInputWithIcon
                onChange={setFilter}
                onClear={clearFilter}
                value={filter}
                placeholder={t("panels.creator.filter.placeholder", "type here to filter...")}
            >
                <SearchIcon isEmpty={isEmpty(filter)} />
            </SearchInputWithIcon>
            <ToolBox filter={filter} />
        </ToolbarWrapper>
    );
}
