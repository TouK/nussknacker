import { Stack, Typography } from "@mui/material";
import { set } from "lodash";
import React, { useMemo, useState } from "react";

import { useUserSettings } from "../../common/userSettings";
import type { UserSettings } from "../../reducers/userSettings";
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
    const [settings, toggle] = useUserSettings();
    const [filter, setFilter] = useState("");

    const values = useMemo(() => {
        const filtered = Object.entries(settings).filter(([key]) => key.toLowerCase().includes(filter.toLowerCase()));
        return toNested(filtered);
    }, [filter, settings]);

    return (
        <>
            <Stack direction="row" sx={{ marginX: 1.5, justifyContent: "space-between", alignItems: "baseline" }}>
                <Typography variant="h4">Settings</Typography>
                <SearchField value={filter} onChange={setFilter} />
            </Stack>
            <CollapsibleSwitchList data={values} onToggle={(path: keyof UserSettings) => toggle([path])} flattenSingleChild />
        </>
    );
}

export default SettingsView;
