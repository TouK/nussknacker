import { Button, Stack, Typography } from "@mui/material";
import { set } from "lodash";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDocumentEventListener } from "rooks";

import { userSettingSet, userSettingsRotate } from "../../actions/nk/userSettings";
import { getUserSettingsMerged, getUserSettingsValues } from "../../reducers/selectors/userSettings";
import type { Setting } from "../../reducers/userSettings";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import CollapsibleSwitchList from "./collapsibleSwitchList";
import { SearchField } from "./SearchField";

function toNested<T>(entries: [string, T][]) {
    const result = {};
    entries.forEach(([key, value]) => {
        set(result, key, value);
    });
    return result;
}

function SettingsView() {
    const settings = useAppSelector(getUserSettingsMerged);
    const rawValues = useAppSelector(getUserSettingsValues);
    const [filter, setFilter] = useState("");
    const searchStrings = useMemo(() => filter.toLowerCase().split(" "), [filter]);

    const filtered = useMemo(
        () =>
            Object.entries(settings).filter(([key]) => {
                const value = key.toLowerCase();
                return searchStrings.every((string) => {
                    return value.includes(string);
                });
            }),
        [searchStrings, settings],
    );

    const values = useMemo(() => {
        return toNested(filtered);
    }, [filtered]);

    const { t } = useTranslation();

    const dispatch = useAppDispatch();

    const onToggle = useCallback(
        (path: Setting, value?: boolean | "default") => {
            dispatch(value === false || value === true || value === "default" ? userSettingSet(path, value) : userSettingsRotate(path));
        },
        [dispatch],
    );

    useDocumentEventListener("keydown", (event: KeyboardEvent) => {
        if (event.key === "Enter" && filtered.length === 1) {
            onToggle(filtered[0][0] as Setting);
        }
    });

    return (
        <Stack direction="column" sx={{ paddingTop: 3, paddingBottom: 12 }} spacing={1}>
            <Stack
                direction="row"
                sx={{
                    paddingX: 1.5,
                    justifyContent: "space-between",
                    alignItems: "baseline",
                }}
                spacing={3}
            >
                <Typography variant="h4">{t("views.settings.header", "Settings")}</Typography>
                <Button
                    color="warning"
                    size="small"
                    disabled={Object.values(rawValues).length <= 0}
                    onClick={() => {
                        dispatch({ type: "USERSETTINGS_RESET" });
                    }}
                >
                    reset to all defaults
                </Button>
                <SearchField value={filter} onChange={setFilter} />
            </Stack>
            <CollapsibleSwitchList
                data={values}
                onToggle={onToggle}
                flattenSingleChild
                openIfOnly
                searchStrings={searchStrings.flatMap((v) => v.split("."))}
            />
        </Stack>
    );
}

export default SettingsView;
