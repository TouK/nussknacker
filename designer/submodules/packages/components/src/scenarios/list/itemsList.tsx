import AssessmentIcon from "@mui/icons-material/Assessment";
import { ListItemAvatar } from "@mui/material";
import IconButton from "@mui/material/IconButton";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import Paper from "@mui/material/Paper";
import type { ListIteratee, Many } from "lodash";
import { orderBy } from "lodash";
import { getEventTrackingProps, EventTrackingSelector } from "nussknackerUi/eventTracking";
import type { CSSProperties } from "react";
import React, { useCallback, useMemo } from "react";
import { List as VList, WindowScroller } from "react-virtualized";
import type { ListRowProps } from "react-virtualized/dist/es/List";

import { ExternalLink, metricsHref, scenarioHref } from "../../common";
import type { FilterRules } from "../../common/filters";
import { useScrollParent } from "../../common/hooks";
import { useScenariosFilterContext } from "../filters/common/useScenariosFilterContext";
import type { ScenariosFiltersModel } from "../filters/scenariosFiltersModel";
import { FirstLine, SecondLine } from "./item";
import type { RowType } from "./listPart";
import { ScenarioAvatar } from "./scenarioAvatar";
import { Stats } from "./stats";

const ListRowContent = React.memo(function ListRowContent({ row }: { row: RowType }): JSX.Element {
    return (
        <ListItemButton divider alignItems={"flex-start"} component={ExternalLink} href={scenarioHref(row.name)}>
            <ListItemAvatar sx={{ minWidth: "46px" }}>
                <ScenarioAvatar scenario={row} />
            </ListItemAvatar>
            <ListItemText
                primary={<FirstLine row={row} />}
                secondary={<SecondLine row={row} />}
                secondaryTypographyProps={{ component: "span" }}
            />
        </ListItemButton>
    );
});

const ListRow = React.memo(function ListRow({ row, style }: { row: RowType; style: CSSProperties }): JSX.Element {
    const opacity = row.isArchived ? 0.5 : 1;

    return (
        <div style={style}>
            <ListItem
                disablePadding
                sx={{ opacity }}
                secondaryAction={
                    !row.isFragment && (
                        <IconButton
                            color={"inherit"}
                            component={ExternalLink}
                            href={metricsHref(row.name)}
                            {...getEventTrackingProps({
                                selector: EventTrackingSelector.ActionMetrics,
                            })}
                        >
                            <AssessmentIcon />
                        </IconButton>
                    )
                }
            >
                <ListRowContent row={row} />
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
    const rowHeight = 84.02;

    const rowRenderer = useCallback(({ index, key, style }: ListRowProps) => <ListRow style={style} key={key} row={rows[index]} />, [rows]);
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
export const DEFAULT_SORT_KEY = "modificationDate";
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
    const { key = DEFAULT_SORT_KEY, order = DEFAULT_SORT_ORDER } = splitSort(sortBy);
    return [(e) => e[key]?.toLowerCase(), order];
}

export function ItemsList(props: {
    data: RowType[];
    isLoading?: boolean;
    filterRules?: FilterRules<RowType, ScenariosFiltersModel>;
}): JSX.Element {
    const { data = [], filterRules, isLoading } = props;
    const { getFilter } = useScenariosFilterContext();

    const rows = useMemo<RowType[]>(() => {
        const filtered = data.filter((row) => filterRules.every(({ key, rule }) => rule(row, getFilter(key))));
        const [sortBy] = getFilter("SORT_BY", true);
        return orderBy(filtered, ...sortRules<RowType>(sortBy));
    }, [data, filterRules, getFilter]);

    const { scrollParent, ref } = useScrollParent();

    return (
        <div ref={ref}>
            <WindowScroller scrollElement={scrollParent}>
                {({ height = 0, width = 0, isScrolling, onChildScroll, scrollTop, registerChild }) => (
                    <>
                        <Paper ref={registerChild} sx={{ flex: 1 }}>
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
