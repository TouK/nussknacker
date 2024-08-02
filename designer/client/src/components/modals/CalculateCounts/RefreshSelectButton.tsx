import React, { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, ButtonGroup, ClickAwayListener, Grow, MenuItem, MenuList, Paper, Popper } from "@mui/material";
import { ArrowDropDown, Update, UpdateDisabled } from "@mui/icons-material";
import { duration } from "moment";

type RefreshSelectButtonProps = {
    onChange: (value: number | null) => void;
    value: number;
    disabled: boolean;
    options: number[];
};

export function RefreshSelectButton({ onChange, value, disabled, options }: RefreshSelectButtonProps) {
    const { t } = useTranslation();
    const [open, setOpen] = useState(false);
    const anchorRef = useRef<HTMLDivElement>(null);
    const [selectedIndex, setSelectedIndex] = useState(0);

    const handleClick = useCallback(() => {
        onChange(value ? null : options[selectedIndex]);
    }, [onChange, options, selectedIndex, value]);

    const handleMenuItemClick = useCallback(
        (event: React.MouseEvent<HTMLLIElement, MouseEvent>, index: number) => {
            setSelectedIndex(index);
            onChange(options[index]);
            setOpen(false);
        },
        [onChange, options],
    );

    const handleToggle = useCallback(() => {
        setOpen((prevOpen) => !prevOpen);
    }, []);

    const handleClose = useCallback((event: Event) => {
        if (anchorRef.current && anchorRef.current.contains(event.target as HTMLElement)) {
            return;
        }

        setOpen(false);
    }, []);

    useEffect(() => {
        const number = options.indexOf(value);
        if (number >= 0) {
            setSelectedIndex(number);
        }
    }, [options, value]);

    const humanizeThresholds = {
        ss: 5,
        s: 55,
    };

    return (
        <>
            <ButtonGroup
                variant="outlined"
                color={value ? "primary" : "inherit"}
                size="small"
                ref={anchorRef}
                aria-label="Button group with a nested menu"
                disabled={disabled}
            >
                <Button onClick={handleClick} startIcon={value ? <Update /> : <UpdateDisabled />}>
                    {value
                        ? t("calculateCounts.autoRefresh", "auto-refresh {{s}}", {
                              s: duration(value, "seconds").humanize(true, humanizeThresholds),
                          })
                        : t("calculateCounts.autoRefreshDisabled", "no refresh")}
                </Button>
                <Button
                    size="small"
                    aria-controls={open ? "split-button-menu" : undefined}
                    aria-expanded={open ? "true" : undefined}
                    aria-label="select merge strategy"
                    aria-haspopup="menu"
                    onClick={handleToggle}
                >
                    <ArrowDropDown />
                </Button>
            </ButtonGroup>
            <Popper
                sx={{
                    zIndex: 1,
                }}
                open={open}
                anchorEl={anchorRef.current}
                role={undefined}
                transition
                disablePortal
            >
                {({ TransitionProps, placement }) => (
                    <Grow
                        {...TransitionProps}
                        style={{
                            transformOrigin: placement === "bottom" ? "center top" : "center bottom",
                        }}
                    >
                        <Paper>
                            <ClickAwayListener onClickAway={handleClose}>
                                <MenuList id="split-button-menu" autoFocusItem>
                                    {options.map((option, index) => (
                                        <MenuItem
                                            key={option}
                                            selected={index === selectedIndex}
                                            onClick={(event) => handleMenuItemClick(event, index)}
                                        >
                                            {duration(option, "seconds").humanize(humanizeThresholds)}
                                        </MenuItem>
                                    ))}
                                </MenuList>
                            </ClickAwayListener>
                        </Paper>
                    </Grow>
                )}
            </Popper>
        </>
    );
}
