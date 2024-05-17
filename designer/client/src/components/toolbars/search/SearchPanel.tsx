import { isEmpty } from "lodash";
import React, { ReactElement, useCallback, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { toggleToolbar } from "../../../actions/nk/toolbars";
import { useDocumentListeners } from "../../../containers/useDocumentListeners";
import { getToolbarsConfigId } from "../../../reducers/selectors/toolbars";
import { useSidePanel } from "../../sidePanels/SidePanel";
import { SearchIcon } from "../../table/SearchFilter";
import { Focusable } from "../../themed/InputWithIcon";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { SearchResults } from "./SearchResults";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import { EventTrackingType, getEventTrackingProps, useEventTracking } from "../../../containers/event-tracking";

export function SearchPanel(props: ToolbarPanelProps): ReactElement {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const toolbarsConfigId = useSelector(getToolbarsConfigId);
    const { trackEvent } = useEventTracking();
    const [filter, setFilter] = useState<string>("");
    const clearFilter = useCallback(() => setFilter(""), []);

    const searchRef = useRef<Focusable>();
    const sidePanel = useSidePanel();

    useDocumentListeners({
        keydown: (e) => {
            switch (e.key.toUpperCase()) {
                case "ESCAPE": {
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    clearFilter();
                    const target = e.composedPath().shift();
                    if (target instanceof HTMLElement) {
                        target.blur();
                    }
                    break;
                }
                case "F": {
                    if (!e.ctrlKey && !e.metaKey) return;
                    e.preventDefault();
                    e.stopPropagation();
                    if (!sidePanel.isOpened) {
                        sidePanel.onToggle();
                    }
                    dispatch(toggleToolbar(props.id, toolbarsConfigId, false));
                    searchRef.current.focus();
                    trackEvent({ type: EventTrackingType.KeyboardFocusSearchNodeField });
                    break;
                }
            }
        },
    });

    return (
        <ToolbarWrapper {...props} title={t("panels.search.title", "Search")} onExpand={() => searchRef.current?.focus()}>
            <SearchInputWithIcon
                ref={searchRef}
                onChange={setFilter}
                onClear={clearFilter}
                value={filter}
                placeholder={t("panels.search.filter.placeholder", "type here to search nodes...")}
                {...getEventTrackingProps({ type: EventTrackingType.SearchNodesInScenario })}
            >
                <SearchIcon isEmpty={isEmpty(filter)} />
            </SearchInputWithIcon>
            <SearchResults filterValues={filter.toLowerCase().split(/\s/).filter(Boolean)} />
        </ToolbarWrapper>
    );
}
