import { FirstLine, SecondLine } from "./item";
import React, { CSSProperties, useCallback, useMemo } from "react";
import { FilterRules, useFilterContext } from "../../common/filters";
import { ExternalLink, metricsHref, NuIcon, scenarioHref } from "../../common";
import ListItem from "@mui/material/ListItem";
import Paper from "@mui/material/Paper";
import ListItemText from "@mui/material/ListItemText";
import ListItemButton from "@mui/material/ListItemButton";
import { orderBy } from "lodash";
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
import { BoxWithArchivedStyle } from "../../common/boxWithArchivedStyle";

const ListRowContent = React.memo(function ListRowContent({ row }: { row: RowType }): JSX.Element {
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
    return (
        <ListItemButton component={ExternalLink} href={scenarioHref(row.id)}>
            <ListItemAvatar>
                <Avatar variant="rounded" sx={sx}>
                    {row.isSubprocess ? (
                        <AccountTreeIcon titleAccess="fragment" />
                    ) : (
                        <NuIcon sx={{ color: "primary.main" }} src={row.state.icon} />
                    )}
                </Avatar>
            </ListItemAvatar>
            <ListItemText primary={<FirstLine row={row} />} secondary={<SecondLine row={row} />} />
        </ListItemButton>
    );
});

const ListRow = React.memo(function ListRow({ row, style }: { row: RowType; style: CSSProperties }): JSX.Element {
    return (
        <div style={style}>
            <BoxWithArchivedStyle
                disablePadding
                component={ListItem}
                isArchived={row.isArchived}
                secondaryAction={
                    !row.isSubprocess && (
                        <IconButton component={ExternalLink} href={metricsHref(row.id)}>
                            <AssessmentIcon />
                        </IconButton>
                    )
                }
            >
                <ListRowContent row={row} />
            </BoxWithArchivedStyle>
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
export const DEFAULT_SORT_KEY = "createdAt";
export const DEFAULT_SORT_ORDER = "desc";

export function splitSort(value: string): { key: string; order: "asc" | "desc" } {
    const [key = DEFAULT_SORT_KEY, order = DEFAULT_SORT_ORDER] = value?.split?.(SORT_SEPARATOR) || [];
    return { key, order: order as any };
}

export function joinSort(key: string, order: "asc" | "desc"): string {
    return `${key}${SORT_SEPARATOR}${order}`;
}

export function isDefaultSort(key: string, order: "asc" | "desc"): boolean {
    return key === DEFAULT_SORT_KEY && order === DEFAULT_SORT_ORDER;
}

export function ItemsList(props: {
    data: RowType[];
    isLoading?: boolean;
    filterRules?: FilterRules<RowType, ScenariosFiltersModel>;
}): JSX.Element {
    const { data = [], filterRules, isLoading } = props;
    const { getFilter } = useFilterContext<ScenariosFiltersModel>();

    const filtered = useMemo(() => {
        return data.filter((row) => filterRules.every(({ key, rule }) => rule(row, getFilter(key))));
    }, [data, filterRules, getFilter]);

    const sorted = useMemo(() => {
        const { key, order } = splitSort(getFilter("SORT_BY"));
        return orderBy(filtered, (e) => e[key]?.toLowerCase(), order);
    }, [filtered, getFilter]);

    const rows = sorted;

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
