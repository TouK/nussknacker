import { FirstLine, SecondLine } from "./item";
import React, { CSSProperties, useCallback, useMemo } from "react";
import { FilterRules, useFilterContext } from "../../common/filters";
import { ExternalLink, metricsHref, NuIcon, scenarioHref } from "../../common";
import ListItem from "@mui/material/ListItem";
import Paper from "@mui/material/Paper";
import ListItemText from "@mui/material/ListItemText";
import ListItemButton from "@mui/material/ListItemButton";
import { ListIteratee, Many, orderBy } from "lodash";
import { List as VList, WindowScroller } from "react-virtualized";
import { useScrollParent } from "../../common/hooks";
import IconButton from "@mui/material/IconButton";
import AssessmentIcon from "@mui/icons-material/Assessment";
import AccountTreeIcon from "@mui/icons-material/AccountTreeRounded";
import { Avatar, ListItemAvatar } from "@mui/material";
import { ListRowProps } from "react-virtualized/dist/es/List";
import { ScenariosFiltersModel } from "../filters/scenariosFiltersModel";
import { RowType } from "./listPart";
import { Stats } from "./stats";
import { FiltersContextType } from "../../common/filters/filtersContext";
import { useTranslation } from "react-i18next";

const ListRowContent = React.memo(function ListRowContent({
    row,
    filtersContext,
}: {
    row: RowType;
    filtersContext: FiltersContextType<ScenariosFiltersModel>;
}): JSX.Element {
    const sx = useMemo(
        () => ({
            bgcolor: "transparent",
            color: "inherit",
            transition: (theme) =>
                theme.transitions.create("font-size", {
                    easing: theme.transitions.easing.easeOut,
                    duration: theme.transitions.duration.shorter,
                }),
            fontSize: "1.5em",
            "[role=button]:hover &": {
                fontSize: "2em",
            },
            ".MuiSvgIcon-root": {
                fontSize: "inherit",
            },
        }),
        [],
    );

    const { t } = useTranslation();

    return (
        <ListItemButton component={ExternalLink} href={scenarioHref(row.id)}>
            <ListItemAvatar>
                <Avatar variant="rounded" sx={sx}>
                    {row.isSubprocess ? (
                        <AccountTreeIcon
                            titleAccess={t("scenario.iconTitle", "Fragment is stateless.", {
                                context: "FRAGMENT",
                            })}
                        />
                    ) : (
                        <NuIcon
                            titleAccess={t("scenario.iconTitle", "{{tooltip}}", {
                                context: row.state.status.name,
                                tooltip: row.state.tooltip,
                            })}
                            sx={{ color: "primary.main" }}
                            src={row.state.icon}
                        />
                    )}
                </Avatar>
            </ListItemAvatar>
            <ListItemText
                primary={<FirstLine row={row} filtersContext={filtersContext} />}
                secondary={<SecondLine row={row} filtersContext={filtersContext} />}
            />
        </ListItemButton>
    );
});

const ListRow = React.memo(function ListRow({
    row,
    style,
    filtersContext,
}: {
    row: RowType;
    style: CSSProperties;
    filtersContext: FiltersContextType<ScenariosFiltersModel>;
}): JSX.Element {
    const opacity = row.isArchived ? 0.5 : 1;

    return (
        <div style={style}>
            <ListItem
                disablePadding
                sx={{ opacity }}
                secondaryAction={
                    !row.isSubprocess && (
                        <IconButton component={ExternalLink} href={metricsHref(row.id)}>
                            <AssessmentIcon />
                        </IconButton>
                    )
                }
            >
                <ListRowContent row={row} filtersContext={filtersContext} />
            </ListItem>
        </div>
    );
});

function ScenarioAndFragmentsList({
    width,
    isScrolling,
    scrollTop,
    height,
    onChildScroll,
    rows,
}: {
    width: number;
    isScrolling: boolean;
    scrollTop: number;
    height: number;
    onChildScroll: (params: { scrollTop: number }) => void;
    rows: RowType[];
}) {
    const rowHeight = 72.02;
    const filtersContext = useFilterContext<ScenariosFiltersModel>();

    const rowRenderer = useCallback(
        ({ index, key, style }: ListRowProps) => <ListRow style={style} key={key} row={rows[index]} filtersContext={filtersContext} />,
        [filtersContext, rows],
    );
    return (
        <VList
            autoWidth
            autoHeight
            width={width}
            height={height}
            isScrolling={isScrolling}
            onScroll={onChildScroll}
            scrollTop={scrollTop}
            rowCount={rows?.length}
            rowHeight={rowHeight}
            rowRenderer={rowRenderer}
            overscanRowCount={0}
        />
    );
}

const SORT_SEPARATOR = ".";
export type SortKey = `${string}${typeof SORT_SEPARATOR}${"asc" | "desc"}`;
export const DEFAULT_SORT_KEY = "createdAt";
export const DEFAULT_SORT_ORDER = "desc";

export function splitSort(value: SortKey): { key: string; order: "asc" | "desc" } {
    const [key = DEFAULT_SORT_KEY, order = DEFAULT_SORT_ORDER] = value?.split?.(SORT_SEPARATOR) || [];
    return { key, order: order as any };
}

export function joinSort(key: string, order: "asc" | "desc"): SortKey {
    return `${key}${SORT_SEPARATOR}${order}`;
}

export function isDefaultSort(key: string, order: "asc" | "desc"): boolean {
    return key === DEFAULT_SORT_KEY && order === DEFAULT_SORT_ORDER;
}

function sortRules<T>(sortBy: SortKey): [Many<ListIteratee<T>>, Many<boolean | "asc" | "desc">] {
    const { key, order } = splitSort(sortBy);
    return [(e) => e[key]?.toLowerCase(), order];
}

export function ItemsList(props: {
    data: RowType[];
    isLoading?: boolean;
    filterRules?: FilterRules<RowType, ScenariosFiltersModel>;
}): JSX.Element {
    const { data = [], filterRules, isLoading } = props;
    const { getFilter } = useFilterContext<ScenariosFiltersModel>();

    const rows = useMemo<RowType[]>(() => {
        const filtered = data.filter((row) => filterRules.every(({ key, rule }) => rule(row, getFilter(key))));
        const [sortBy] = getFilter("SORT_BY", true);
        return orderBy(filtered, ...sortRules<RowType>(sortBy));
    }, [data, filterRules, getFilter]);

    const { scrollParent, ref } = useScrollParent();

    return (
        <div ref={ref}>
            <WindowScroller scrollElement={scrollParent}>
                {({ height = 0, width = 0, isScrolling, onChildScroll, scrollTop }) => (
                    <>
                        <Paper sx={{ flex: 1 }}>
                            <ScenarioAndFragmentsList
                                height={height}
                                width={width}
                                isScrolling={isScrolling}
                                onChildScroll={onChildScroll}
                                rows={rows}
                                scrollTop={scrollTop}
                            />
                        </Paper>
                    </>
                )}
            </WindowScroller>
            <Stats current={rows?.length} all={data?.length} isLoading={isLoading} />
        </div>
    );
}
