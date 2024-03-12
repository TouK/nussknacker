import { useTranslation } from "react-i18next";
import React, { ReactElement, useCallback, useRef, useState } from "react";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { useDocumentListeners } from "../../../containers/useDocumentListeners";
import { toggleToolbar } from "../../../actions/nk/toolbars";
import { useDispatch, useSelector } from "react-redux";
import { getToolbarsConfigId } from "../../../reducers/selectors/toolbars";
import { Focusable, InputWithIcon } from "../../themed/InputWithIcon";
import { SearchIcon } from "../../table/SearchFilter";
import { isEmpty } from "lodash";
import { SearchResults } from "./SearchResults";
import { css } from "@emotion/css";

export function SearchPanel(): ReactElement {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const toolbarsConfigId = useSelector(getToolbarsConfigId);
    const id = "search-panel";

    const styles = css({
        borderRadius: 0,
        height: "36px !important",
        color: "#FFFFFF",
        padding: "6px 12px !important",
        backgroundColor: "#333333",
        border: "none",
        outline: "none !important",
        "&:focus": {
            boxShadow: "none",
        },
    });

    const [filter, setFilter] = useState<string>("");
    const clearFilter = useCallback(() => setFilter(""), []);

    const searchRef = useRef<Focusable>();

    useDocumentListeners({
        keydown: (e) => {
            switch (e.key.toUpperCase()) {
                case "F": {
                    if (!e.ctrlKey && !e.metaKey) return;
                    e.preventDefault();
                    e.stopPropagation();
                    dispatch(toggleToolbar(id, toolbarsConfigId, false));
                    searchRef.current?.focus();
                }
            }
        },
    });

    return (
        <ToolbarWrapper id={id} title={t("panels.search.title", "Search")} onExpand={() => searchRef.current?.focus()}>
            <InputWithIcon
                ref={searchRef}
                className={styles}
                onChange={setFilter}
                onClear={clearFilter}
                value={filter}
                placeholder={t("panels.search.filter.placeholder", "type here to search nodes...")}
            >
                <SearchIcon isEmpty={isEmpty(filter)} />
            </InputWithIcon>
            <SearchResults filterValues={filter.toLowerCase().split(/\s/).filter(Boolean)} />
        </ToolbarWrapper>
    );
}
