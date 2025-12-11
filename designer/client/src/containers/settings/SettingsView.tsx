import { Button, Stack, Typography } from "@mui/material";
import { set } from "lodash";
import React, { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

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

    const values = useMemo(() => {
        const filtered = Object.entries(settings).filter(([key]) => key.toLowerCase().includes(filter.toLowerCase()));
        return toNested(filtered);
    }, [filter, settings]);

    const { t } = useTranslation();

    const dispatch = useAppDispatch();

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
                onToggle={(path: Setting, value) => {
                    dispatch(
                        value === false || value === true || value === "default" ? userSettingSet(path, value) : userSettingsRotate(path),
                    );
                }}
                flattenSingleChild
            />
        </Stack>
    );
}

export default SettingsView;
