import React, { PropsWithChildren, useCallback, useMemo } from "react";
import { FiltersContextProvider, useFilterContext } from "../common";
import { useScenariosWithStatus, useUserQuery } from "./useScenariosQuery";
import { ProcessType } from "nussknackerUi/components/Process/types";
import { ItemsList } from "./itemsList";
import { Avatar, Badge, Box, Button, Chip, Divider, Popover, Stack } from "@mui/material";
import { flatten, uniq, uniqBy } from "lodash";
import { bindPopover, bindTrigger, usePopupState } from "material-ui-popup-state/hooks";
import { useTranslation } from "react-i18next";
import { Chance } from "chance";
import { ExpandLess, ExpandMore } from "@mui/icons-material";
import { SimpleOptionsStack } from "./selectFilter2";
import { OtherOptionsStack } from "./otherOptionsStack";
import { ScenariosFiltersModel } from "./scenariosFiltersModel";
import { QuickFilter } from "./quickFilter";
import { filterRules } from "./filterRules";
import { SortOptionsStack } from "./sortOptionsStack";

export function Prototype(): JSX.Element {
    const { data = [], isLoading } = useScenariosWithStatus();

    return (
        <FiltersContextProvider<ScenariosFiltersModel>
            getValueLinker={(setNewValue) => (id, value) => {
                switch (id) {
                    case "HIDE_SCENARIOS":
                        return value && setNewValue("HIDE_FRAGMENTS", false);
                    case "HIDE_FRAGMENTS":
                        return value && setNewValue("HIDE_SCENARIOS", false);
                    case "HIDE_ACTIVE":
                        return value && setNewValue("SHOW_ARCHIVED", true);
                    case "SHOW_ARCHIVED":
                        return !value && setNewValue("HIDE_ACTIVE", false);
                }
            }}
        >
            <TableView data={data} isLoading={isLoading} />
        </FiltersContextProvider>
    );
}

export type RowType = ProcessType;

interface TableViewData<T> {
    data: T[];
    isLoading?: boolean;
}

function TableView(props: TableViewData<RowType>): JSX.Element {
    const { data = [], isLoading } = props;

    const filterableKeys = useMemo(() => ["isArchived", "isSubprocess", "processCategory", "createdBy"], [data]);
    const filterableValues = useMemo(() => {
        const entries = filterableKeys.map((k) => [k, uniq(flatten(data.map((v) => v[k]))).sort()]);
        return Object.fromEntries([
            ...entries,
            [
                "status",
                uniqBy(
                    data.map((v) => ({ name: v.state?.status.name, icon: v.state?.icon })),
                    "name",
                ).sort(),
            ],
        ]);
    }, [data, filterableKeys]);

    const { t } = useTranslation();

    const { getFilter, setFilter } = useFilterContext<ScenariosFiltersModel>();

    const otherFilters = ["HIDE_SCENARIOS", "HIDE_FRAGMENTS", "HIDE_ACTIVE", "SHOW_ARCHIVED"];

    const { data: userData } = useUserQuery();
    const categories = (userData?.categories || filterableValues["processCategory"]).map((name) => ({ name }));

    return (
        <>
            <QuickFilter>
                <Stack direction="row" spacing={1} p={1} alignItems="center" divider={<Divider orientation="vertical" flexItem />}>
                    <FilterExpand label={t("table.filter.STATUS", "Status")} count={getFilter("STATUS", true).length}>
                        <SimpleOptionsStack
                            label={t("table.filter.STATUS", "Status")}
                            options={filterableValues["status"]}
                            value={getFilter("STATUS", true)}
                            onChange={setFilter("STATUS")}
                        />
                    </FilterExpand>
                    <FilterExpand label={t("table.filter.CATEGORY", "Category")} count={getFilter("CATEGORY", true).length}>
                        <SimpleOptionsStack
                            label={t("table.filter.CATEGORY", "Category")}
                            options={categories}
                            value={getFilter("CATEGORY", true)}
                            onChange={setFilter("CATEGORY")}
                        />
                    </FilterExpand>
                    <FilterExpand label={t("table.filter.CREATED_BY", "Author")} count={getFilter("CREATED_BY", true).length}>
                        <SimpleOptionsStack
                            label={t("table.filter.CREATED_BY", "Author")}
                            options={filterableValues["createdBy"].map((name) => ({ name }))}
                            value={getFilter("CREATED_BY", true)}
                            onChange={setFilter("CREATED_BY")}
                        />
                    </FilterExpand>
                    <FilterExpand
                        label={t("table.filter.other", "Other")}
                        count={otherFilters.filter((k: keyof ScenariosFiltersModel) => getFilter(k)).length}
                    >
                        <OtherOptionsStack />
                    </FilterExpand>
                    <FilterExpand label={t("table.filter.SORT_BY", "Sort")}>
                        <SortOptionsStack
                            label={t("table.filter.SORT_BY", "Sort")}
                            options={["createdAt", "modificationDate", "name"].map((name) => ({ name }))}
                            value={getFilter("SORT_BY", true)}
                            onChange={setFilter("SORT_BY")}
                        />
                    </FilterExpand>
                </Stack>
            </QuickFilter>

            <ActiveFilters />

            <Box sx={{ display: "flex", alignItems: "flex-start", flexDirection: "row-reverse" }}>
                <Box
                    sx={{
                        flex: 1,
                        width: 0,
                        overflow: "hidden",
                    }}
                >
                    <ItemsList data={data} filterRules={filterRules} isLoading={isLoading} />
                </Box>
            </Box>
        </>
    );
}

export function FilterExpand({ children, label, count }: PropsWithChildren<{ label: string; count?: number }>): JSX.Element {
    const popupState = usePopupState({ variant: "popper", popupId: label });
    return (
        <>
            <Badge badgeContent={count} color="primary">
                <Button
                    type="button"
                    size="small"
                    variant="text"
                    disabled={popupState.isOpen}
                    color="inherit"
                    startIcon={popupState.isOpen ? <ExpandLess /> : <ExpandMore />}
                    {...bindTrigger(popupState)}
                >
                    {label}
                </Button>
            </Badge>
            <Popover
                {...bindPopover(popupState)}
                anchorOrigin={{
                    vertical: "center",
                    horizontal: "center",
                }}
                transformOrigin={{
                    vertical: "top",
                    horizontal: "center",
                }}
            >
                {children}
            </Popover>
        </>
    );
}

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
        () => activeKeys.filter((k) => k !== "NAME" && k !== "SORT_BY").flatMap((k) => [].concat(getFilter(k)).map((v) => [k, v])),
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
