import { isEmpty } from "lodash";
import React, { ReactElement, useCallback, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { toggleToolbar } from "../../../actions/nk/toolbars";
import { useDocumentListeners } from "../../../containers/useDocumentListeners";
import { getToolbarsConfigId } from "../../../reducers/selectors/toolbars";
import { SearchIcon } from "../../table/SearchFilter";
import { Focusable } from "../../themed/InputWithIcon";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { SearchResults } from "./SearchResults";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import { EventTrackingSelector, EventTrackingType, getEventTrackingProps, useEventTracking } from "../../../containers/event-tracking";
import { useTheme } from "@mui/material";
import { useSidePanel } from "../../sidePanels/SidePanelsContext";

export function SearchPanel(props: ToolbarPanelProps): ReactElement {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const toolbarsConfigId = useSelector(getToolbarsConfigId);
    const { trackEvent } = useEventTracking();
    const [filter, setFilter] = useState<string>("");
    const clearFilter = useCallback(() => setFilter(""), []);

    const searchRef = useRef<Focusable>();
    const { isOpened, toggleCollapse } = useSidePanel();

    const theme = useTheme();

    const handleEscape = useCallback(
        (e: KeyboardEvent) => {
            e.preventDefault();
            e.stopImmediatePropagation();
            clearFilter();
            const target = e.composedPath().shift();
            if (target instanceof HTMLElement) {
                target.blur();
            }
        },
        [clearFilter],
    );

    const handleSearch = useCallback(
        (e: KeyboardEvent) => {
            if (!e.ctrlKey && !e.metaKey) return;
            e.preventDefault();
            e.stopPropagation();
            if (!isOpened) {
                toggleCollapse();
            }
            dispatch(toggleToolbar(props.id, toolbarsConfigId, false));
            setTimeout(() => {
                searchRef.current.focus();
            }, theme.transitions.duration.enteringScreen);
            trackEvent({
                selector: EventTrackingSelector.FocusSearchNodeField,
                event: EventTrackingType.Keyboard,
            });
        },
        [dispatch, isOpened, toggleCollapse, props.id, theme.transitions.duration.enteringScreen, toolbarsConfigId, trackEvent],
    );

    useDocumentListeners({
        keydown: (e) => {
            switch (e.key.toUpperCase()) {
                case "ESCAPE": {
                    handleEscape(e);
                    break;
                }
                case "F": {
                    handleSearch(e);
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
                {...getEventTrackingProps({ selector: EventTrackingSelector.NodesInScenario })}
            >
                <SearchIcon isEmpty={isEmpty(filter)} />
            </SearchInputWithIcon>
            <SearchResults filterValues={filter.toLowerCase().split(/\s/).filter(Boolean)} />
        </ToolbarWrapper>
    );
}
