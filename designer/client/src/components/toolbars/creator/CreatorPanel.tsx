import { css } from "@emotion/css";
import { isEmpty } from "lodash";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { SearchIcon } from "../../table/SearchFilter";
import { InputWithIcon } from "../../themed/InputWithIcon";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import ToolBox from "./ToolBox";

export function CreatorPanel(props: ToolbarPanelProps): JSX.Element {
    const { t } = useTranslation();

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

    const [filter, setFilter] = useState("");
    const clearFilter = useCallback(() => setFilter(""), []);

    return (
        <ToolbarWrapper {...props} title={t("panels.creator.title", "Creator panel")}>
            <InputWithIcon
                className={styles}
                onChange={setFilter}
                onClear={clearFilter}
                value={filter}
                placeholder={t("panels.creator.filter.placeholder", "type here to filter...")}
            >
                <SearchIcon isEmpty={isEmpty(filter)} />
            </InputWithIcon>
            <ToolBox filter={filter} />
        </ToolbarWrapper>
    );
}
