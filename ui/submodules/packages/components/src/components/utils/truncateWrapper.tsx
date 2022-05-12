import React, { PropsWithChildren, useCallback, useRef } from "react";
import { useCellArrowKeys } from "./useCellArrowKeys";
import { Truncate } from "./truncate";
import { Visibility } from "@mui/icons-material";
import { Box, Chip, Popover, PopoverOrigin, Stack } from "@mui/material";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { bindPopover, bindTrigger, PopupState, usePopupState } from "material-ui-popup-state/hooks";

const paperProps = {
    sx: {
        border: 1,
        borderColor: "primary.dark",
        maxWidth: "60vw",
        a: {
            maxWidth: "50vw",
        },
    },
};

const anchorOrigin: PopoverOrigin = {
    vertical: "center",
    horizontal: "center",
};
const transformOrigin: PopoverOrigin = {
    vertical: "center",
    horizontal: "center",
};

export function TruncateWrapper({ children, ...props }: PropsWithChildren<GridRenderCellParams>): JSX.Element {
    const handleCellKeyDown = useCellArrowKeys(props);
    const popupState = usePopupState({ variant: "popover", popupId: "pop" });
    const { anchorEl, ...popoverProps } = bindPopover(popupState);
    const ref = useRef();

    const childrenNumber = React.Children.count(children);
    const renderTruncator = useCallback(
        ({ hiddenItemsCount }) => <Truncator itemsCount={childrenNumber} hiddenItemsCount={hiddenItemsCount} popupState={popupState} />,
        [popupState, childrenNumber],
    );

    return (
        <Box ref={ref} onKeyDown={handleCellKeyDown} overflow="hidden" flex={1}>
            <Stack
                flex={1}
                direction="row"
                spacing={0.5}
                component={Truncate}
                renderTruncator={renderTruncator}
                itemClassName="item"
                sx={{
                    ".item:nth-child(2)": {
                        marginLeft: 0,
                    },
                }}
            >
                {children}
            </Stack>
            <Popover
                elevation={3}
                PaperProps={paperProps}
                anchorOrigin={anchorOrigin}
                transformOrigin={transformOrigin}
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

const Truncator = ({
    itemsCount,
    hiddenItemsCount,
    popupState,
}: {
    itemsCount: number;
    hiddenItemsCount: number;
    popupState: PopupState;
}) => (
    <Chip
        sx={{ border: "none" }}
        tabIndex={0}
        icon={<Visibility />}
        label={itemsCount === hiddenItemsCount ? `${hiddenItemsCount} items...` : `${hiddenItemsCount} more...`}
        size="small"
        variant="outlined"
        {...bindTrigger(popupState)}
    />
);
