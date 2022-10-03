import { useFilterContext } from "../../common";
import { ScenariosFiltersModel } from "./scenariosFiltersModel";
import React, { useMemo } from "react";
import { Chance } from "chance";
import { alpha, Avatar, Box, Chip, emphasize } from "@mui/material";
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

export function ActiveFilters<F extends Record<string, any>>({
    activeKeys,
    getLabel,
}: {
    activeKeys: (keyof F)[];
    getLabel?: (name: keyof F, value?: string | number) => string;
}): JSX.Element {
    const { setFilter, getFilter } = useFilterContext<F>();

    const values = useMemo(
        () =>
            activeKeys.flatMap((k) =>
                []
                    .concat(getFilter(k))
                    .filter((v) => v === 0 || !!v)
                    .map((v) => [k, v]),
            ),
        [getFilter, activeKeys],
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
                alignItems: "center",
            }}
        >
            {values.map(([name, value]) => {
                const color = getColor(name);
                return (
                    <React.Fragment key={name + value}>
                        <Chip
                            color="secondary"
                            avatar={getUserSetting("scenarios:avatars") ? getAvatar(name, value) : null}
                            size="small"
                            label={getLabel?.(name, value)}
                            onDelete={() => {
                                setFilter(name, getFilter(name)?.filter?.((c) => c !== value) || null);
                            }}
                            className={name}
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
                    </React.Fragment>
                );
            })}
            <Box display="flex" flex={1} justifyContent="flex-end">
                <ClearFiltersButton />
            </Box>
        </Box>
    );
}
