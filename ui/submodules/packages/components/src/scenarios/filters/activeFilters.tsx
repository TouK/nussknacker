import { useFilterContext } from "../../common";
import { ScenariosFiltersModel } from "./scenariosFiltersModel";
import React, { useCallback, useMemo } from "react";
import { Chance } from "chance";
import { Avatar, Box, Chip } from "@mui/material";
import { useTranslation } from "react-i18next";

function stringAvatar(name: string) {
    const [first, second = ""] = [...name].filter((l) => l.match(/[b-df-hj-np-tv-z0-9]/i));

    const letters = `${first}${second}`;
    const color = new Chance(name).color({ format: "shorthex" });
    return {
        sx: {
            "&, .MuiChip-root &": {
                bgcolor: color,
                color: (theme) => theme.palette.getContrastText(color),
            },
        },
        children: letters,
    };
}

export function ActiveFilters(): JSX.Element {
    const { t } = useTranslation();
    const { activeKeys, setFilter, getFilter } = useFilterContext<ScenariosFiltersModel>();

    const values = useMemo(
        () =>
            activeKeys
                .filter((k) => k !== "NAME" && k !== "SORT_BY")
                .flatMap((k) =>
                    []
                        .concat(getFilter(k))
                        .filter(Boolean)
                        .map((v) => [k, v]),
                ),
        [getFilter, activeKeys],
    );

    const getLabel = useCallback(
        (name: keyof ScenariosFiltersModel, value?: string) => {
            if (value?.length) {
                return value;
            }

            switch (name) {
                case "HIDE_ACTIVE":
                    return t("table.filter.desc.HIDE_ACTIVE", "Hide active");
                case "HIDE_FRAGMENTS":
                    return t("table.filter.desc.HIDE_FRAGMENTS", "Hide fragments");
                case "HIDE_SCENARIOS":
                    return t("table.filter.desc.HIDE_SCENARIOS", "Hide scenarios");
                case "SHOW_ARCHIVED":
                    return t("table.filter.desc.SHOW_ARCHIVED", "Show archived");
                case "HIDE_DEPLOYED":
                    return t("table.filter.desc.HIDE_DEPLOYED", "Not deployed only");
                case "HIDE_NOT_DEPLOYED":
                    return t("table.filter.desc.HIDE_NOT_DEPLOYED", "Deployed only");
            }

            return name;
        },
        [t],
    );

    if (!values.length) {
        return null;
    }

    return (
        <Box
            sx={{
                display: "flex",
                px: 0.5,
                columnGap: 0.5,
                rowGap: 1,
                flexWrap: "wrap",
            }}
        >
            {values.map(([name, value]) => (
                <Chip
                    color="secondary"
                    avatar={<Avatar {...stringAvatar(name)} />}
                    size="small"
                    key={name + value}
                    label={getLabel(name, value)}
                    onDelete={() => {
                        setFilter(name, getFilter(name)?.filter?.((c) => c !== value) || null);
                    }}
                />
            ))}
        </Box>
    );
}
