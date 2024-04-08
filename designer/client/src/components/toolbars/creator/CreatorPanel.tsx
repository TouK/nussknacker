import { isEmpty } from "lodash";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { SearchIcon } from "../../table/SearchFilter";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import ToolBox from "./ToolBox";
import { SearchInputWithIcon } from "../../themed/SearchInput";

export function CreatorPanel(): JSX.Element {
    const { t } = useTranslation();

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
