import { isEmpty } from "lodash";
import React, { ReactElement, useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AdvancedOptionsIcon, SearchIcon } from "../../table/SearchFilter";
import { Focusable } from "../../themed/InputWithIcon";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { SearchResults } from "./SearchResults";
import { SearchInputWithAdvancedOptions, SearchInputWithIcon } from "../../themed/SearchInput";
import { EventTrackingSelector, getEventTrackingProps } from "../../../containers/event-tracking";
import { Collapse } from "@mui/material";
import { AdvancedSearchOptions } from "./AdvancedSearchOptions";

export function SearchPanel(props: ToolbarPanelProps): ReactElement {
    const { t } = useTranslation();
    const [filter, setFilter] = useState<string>("");
    const clearFilter = useCallback(() => setFilter(""), []);
    const [advancedOptionsCollapsed, setAdvancedOptionsCollapsed] = useState(true);

    const searchRef = useRef<Focusable>();

    useEffect(() => {
        setAdvancedOptionsCollapsed(false);
    }, [filter]);

    return (
        <ToolbarWrapper {...props} title={t("panels.search.title", "Search")} onExpand={() => searchRef.current?.focus()}>
            <SearchInputWithAdvancedOptions
                ref={searchRef}
                onChange={setFilter}
                onClear={clearFilter}
                value={filter}
                placeholder={t("panels.search.filter.placeholder", "type here to search nodes...")}
                {...getEventTrackingProps({ selector: EventTrackingSelector.NodesInScenario })}
            >
                <AdvancedOptionsIcon isActive={advancedOptionsCollapsed} collapseHandler={setAdvancedOptionsCollapsed} />
            </SearchInputWithAdvancedOptions>
            <Collapse in={advancedOptionsCollapsed} timeout="auto" unmountOnExit={false}>
                <AdvancedSearchOptions setFilter={setFilter} setCollapsedHandler={setAdvancedOptionsCollapsed} />
            </Collapse>
            <SearchResults filterRawText={filter} />
        </ToolbarWrapper>
    );
}
