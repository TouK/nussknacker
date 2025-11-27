import { Collapse } from "@mui/material";
import { isEmpty } from "lodash";
import type { ReactElement } from "react";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { getEventTrackingProps } from "../../../containers/event-tracking/helpers";
import { EventTrackingSelector } from "../../../containers/event-tracking/use-register-tracking-events";
import { AdvancedOptionsIcon, SearchIcon } from "../../table/SearchFilter";
import type { Focusable } from "../../themed/InputWithIcon";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import { SearchPanelStyled } from "../../tips/Styled";
import type { ToolbarPanelProps } from "../../toolbarComponents/ButtonsToolbar";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { AdvancedSearchFilters } from "./AdvancedSearchFilters";
import type { SearchQuery } from "./SearchResults";
import { SearchResults } from "./SearchResults";

export function SearchPanel(props: ToolbarPanelProps): ReactElement {
    const { t } = useTranslation();
    const [filterFields, setFilterFields] = useState<SearchQuery>({});
    const [filter, setFilter] = useState<string>("");
    const clearFilter = useCallback(() => {
        setFilter("");
        setFilterFields({});
    }, []);
    const [advancedOptionsCollapsed, setAdvancedOptionsCollapsed] = useState(false);

    const searchRef = useRef<Focusable>(null);

    useEffect(() => {
        setAdvancedOptionsCollapsed(false);
    }, [filter]);

    return (
        <ToolbarWrapper {...props} title={t("panels.search.title", "Search")} onExpand={() => searchRef.current?.focus()}>
            <SearchInputWithIcon
                ref={searchRef}
                onChange={setFilter}
                endAdornment={<AdvancedOptionsIcon isActive={advancedOptionsCollapsed} collapseHandler={setAdvancedOptionsCollapsed} />}
                onClear={clearFilter}
                value={filter}
                placeholder={t("panels.search.filter.placeholder", "type here to search nodes...")}
                {...getEventTrackingProps({ selector: EventTrackingSelector.NodesInScenario })}
            >
                <SearchIcon isEmpty={isEmpty(filter)} />
            </SearchInputWithIcon>
            <Collapse in={advancedOptionsCollapsed} unmountOnExit>
                <SearchPanelStyled>
                    <AdvancedSearchFilters
                        filterFields={filterFields}
                        setFilterFields={setFilterFields}
                        filter={filter}
                        setFilter={setFilter}
                        setCollapsedHandler={setAdvancedOptionsCollapsed}
                    />
                </SearchPanelStyled>
            </Collapse>
            <SearchResults filterRawText={filter} />
        </ToolbarWrapper>
    );
}
