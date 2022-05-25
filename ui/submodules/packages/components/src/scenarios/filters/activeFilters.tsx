import { useFilterContext } from "../../common";
import { ScenariosFiltersModel } from "./scenariosFiltersModel";
import React, { useCallback, useMemo } from "react";
import { Chance } from "chance";
import { alpha, Avatar, Box, Chip, emphasize } from "@mui/material";
import { useTranslation } from "react-i18next";
import { ClearFiltersButton } from "./clearFiltersButton";
import { getUserSetting } from "./getUserSetting";

function getInitials(value: string): [string, string] {
    const [first, ...restChars] = value;
    const [, wordStart] = value.split(/\s/).map(([letter]) => letter);
    const [capital] = restChars.filter((l) => l.match(/[A-Z0-9]/));
    const [consonant] = restChars.filter((l) => l.match(/[b-df-hj-np-tv-z0-9]/i));
    return [first, wordStart || capital || consonant || ""];
}

function stringAvatar(name: string, value: string) {
    const [first, second] = getInitials(value);
    const letters = `${first}${second}`.toUpperCase();
    const color = new Chance(value).color({ format: "shorthex" });
    return {
        sx: {
            "&, .MuiChip-root &": {
                bgcolor: color,
                color: (theme) => theme.palette.getContrastText(color),
                fontWeight: "bold",
            },
        },
        children: letters,
    };
}

function getAvatar(name: keyof ScenariosFiltersModel, value?: string) {
    switch (name) {
        case "CATEGORY":
        case "CREATED_BY":
            return <Avatar {...stringAvatar(name, value)} />;
        default:
            return null;
    }
}

function getColor(name: keyof ScenariosFiltersModel) {
    return emphasize(new Chance(name).color({ format: "hex" }), 0.6);
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
                    return t("table.filter.desc.HIDE_ACTIVE", "Active hidden");
                case "HIDE_FRAGMENTS":
                    return t("table.filter.desc.HIDE_FRAGMENTS", "Fragments hidden");
                case "HIDE_SCENARIOS":
                    return t("table.filter.desc.HIDE_SCENARIOS", "Scenarios hidden");
                case "SHOW_ARCHIVED":
                    return t("table.filter.desc.SHOW_ARCHIVED", "Archived visible");
                case "HIDE_DEPLOYED":
                    return t("table.filter.desc.HIDE_DEPLOYED", "Deployed hidden");
                case "HIDE_NOT_DEPLOYED":
                    return t("table.filter.desc.HIDE_NOT_DEPLOYED", "Not deployed hidden");
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
            {values.map(([name, value]) => {
                const color = getColor(name);
                return (
                    <Chip
                        color="secondary"
                        avatar={getUserSetting("scenarios:avatars") ? getAvatar(name, value) : null}
                        size="small"
                        key={name + value}
                        label={getLabel(name, value)}
                        onDelete={() => {
                            setFilter(name, getFilter(name)?.filter?.((c) => c !== value) || null);
                        }}
                        sx={{
                            bgcolor: color,
                            color: (theme) => theme.palette.getContrastText(color),
                            ".MuiChip-deleteIcon": {
                                color: (theme) => alpha(theme.palette.getContrastText(color), 0.26),
                            },
                            ".MuiChip-deleteIcon:hover": {
                                color: (theme) => alpha(theme.palette.getContrastText(color), 0.4),
                            },
                            fontWeight: "bold",
                            boxShadow: (theme) => theme.shadows[1],
                        }}
                    />
                );
            })}

            <Box display="flex" flex={1} justifyContent="flex-end">
                <ClearFiltersButton />
            </Box>
        </Box>
    );
}
