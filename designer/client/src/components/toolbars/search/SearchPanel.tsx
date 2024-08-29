import { isEmpty } from "lodash";
import React, { ReactElement, useCallback, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { SearchIcon } from "../../table/SearchFilter";
import { Focusable } from "../../themed/InputWithIcon";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { SearchResults } from "./SearchResults";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import { EventTrackingSelector, getEventTrackingProps } from "../../../containers/event-tracking";

export function SearchPanel(props: ToolbarPanelProps): ReactElement {
    const { t } = useTranslation();
    const [filter, setFilter] = useState<string>("");
    const clearFilter = useCallback(() => setFilter(""), []);

    const searchRef = useRef<Focusable>();

    return (
        <ToolbarWrapper {...props} title={t("panels.search.title", "Search")} onExpand={() => searchRef.current?.focus()}>
            <SearchInputWithIcon
                ref={searchRef}
                onChange={setFilter}
                onClear={clearFilter}
                value={filter}
                placeholder={t("panels.search.filter.placeholder", "type here to search nodes...")}
                {...getEventTrackingProps({ selector: EventTrackingSelector.NodesInScenario })}
            >
                <SearchIcon isEmpty={isEmpty(filter)} />
            </SearchInputWithIcon>
            <SearchResults filterRawText={filter} />
        </ToolbarWrapper>
    );
}
