import { OpenInBrowser as LinkIcon, Visibility } from "@mui/icons-material";
import { Box, Chip, Link, Popover, Stack } from "@mui/material";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { bindPopover, bindTrigger, usePopupState } from "material-ui-popup-state/hooks";
import { BASE_ORIGIN, BASE_PATH } from "nussknackerUi/config";
import React, { PropsWithChildren, useCallback, useRef } from "react";
import "react-truncate-list/dist/styles.css";
import { useFilterContext } from "../filters/filtersContext";
import { Truncate } from "./truncate";
import { useCellArrowKeys } from "./useCellArrowKeys";

function CategoryChip({ filterValue, name }: { filterValue: string[]; name: string }): JSX.Element {
    const isSelected = filterValue.includes(name);
    const { getFilter, setFilter } = useFilterContext();

    const onClick = useCallback(() => {
        const category = getFilter("CATEGORY", true);
        setFilter("CATEGORY", isSelected ? category.filter((value) => value !== name) : [...category, name]);
    }, [getFilter, isSelected, name, setFilter]);

    return <Chip tabIndex={0} label={name} size="small" color={isSelected ? "primary" : "default"} onClick={onClick} />;
}

export function WrapperB({ children, ...props }: PropsWithChildren<GridRenderCellParams>): JSX.Element {
    const handleCellKeyDown = useCellArrowKeys(props);
    const popupState = usePopupState({ variant: "popover", popupId: "pop" });
    const { anchorEl, ...popoverProps } = bindPopover(popupState);
    const ref = useRef();
    return (
        <Box
            ref={ref}
            onKeyDown={handleCellKeyDown}
            sx={{
                overflow: "hidden",
                flex: 1,
            }}
        >
            <Stack
                sx={{ flex: 1 }}
                direction="row"
                spacing={0.5}
                component={Truncate}
                renderTruncator={({ hiddenItemsCount }) => (
                    <Chip
                        sx={{ border: "none" }}
                        tabIndex={0}
                        icon={<Visibility />}
                        label={`${hiddenItemsCount} more...`}
                        size="small"
                        variant="outlined"
                        {...bindTrigger(popupState)}
                    />
                )}
            >
                {children}
            </Stack>
            <Popover
                elevation={3}
                PaperProps={{
                    sx: {
                        border: 1,
                        borderColor: "primary.dark",
                        maxWidth: "60vw",
                        a: {
                            maxWidth: "50vw",
                        },
                    },
                }}
                anchorOrigin={{
                    vertical: "center",
                    horizontal: "center",
                }}
                transformOrigin={{
                    vertical: "center",
                    horizontal: "center",
                }}
                {...popoverProps}
                open={popoverProps.open}
                anchorEl={ref.current || anchorEl}
            >
                <Box display="flex" flexDirection="row" flexWrap="wrap" p={2} gap={0.5} justifyContent="flex-end">
                    {children}
                </Box>
            </Popover>
        </Box>
    );
}

export function CategoriesCell(props: GridRenderCellParams & { filterValue: string[] }): JSX.Element {
    const { value, filterValue } = props;
    const elements = value.map((name) => {
        return <CategoryChip key={name} filterValue={filterValue} name={name} />;
    });
    return <WrapperB {...props}>{elements}</WrapperB>;
}

const urljoin = (...parts: string[]) =>
    parts
        .map((p) => p.trim())
        .join("/")
        .replace(/(?<!:)(\/)+/g, "/");

export function scenarioHref(id: string): string {
    return urljoin(BASE_ORIGIN, BASE_PATH, "/visualization", id);
}

export const CustomCell = (props: GridRenderCellParams): JSX.Element => {
    const { value, row } = props;
    const { getFilter } = useFilterContext();
    const filter = getFilter("TEXT");

    const elements = value.map((node) => {
        return (
            <Chip
                size="small"
                component={Link}
                href={urljoin(scenarioHref(row.id), `?nodeId=${node}`)}
                target="_blank"
                rel="noopener"
                tabIndex={0}
                key={node}
                label={node}
                color={!filter || node.toString().includes(filter) ? "primary" : "default"}
                icon={<LinkIcon />}
                onClick={() => {
                    return;
                }}
            />
        );
    });
    return <WrapperB {...props}>{elements}</WrapperB>;
};
