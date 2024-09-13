import { isEmpty } from "lodash";
import React, { ReactElement, useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AdvancedOptionsIcon, SearchIcon } from "../../table/SearchFilter";
import { Focusable } from "../../themed/InputWithIcon";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { SearchResults } from "./SearchResults";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import { EventTrackingSelector, getEventTrackingProps } from "../../../containers/event-tracking";
import { Collapse } from "@mui/material";
import { AdvancedSearchFilters } from "./AdvancedSearchFilters";
import { SearchPanelStyled, TipPanelStyled } from "../../tips/Styled";

export function SearchPanel(props: ToolbarPanelProps): ReactElement {
    const { t } = useTranslation();
    const [filter, setFilter] = useState<string>("");
    const refForm = useRef<HTMLFormElement>(null);
    const clearFilter = useCallback(() => {
        setFilter("");
        refForm.current.reset();
    }, []);
    const [advancedOptionsCollapsed, setAdvancedOptionsCollapsed] = useState(false);

    const searchRef = useRef<Focusable>();

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
            <Collapse in={advancedOptionsCollapsed} timeout="auto" unmountOnExit={false}>
                <SearchPanelStyled>
                    <AdvancedSearchFilters
                        refForm={refForm}
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
