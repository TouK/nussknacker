import { ExpandLess, ExpandMore } from "@mui/icons-material";
import { Badge, Button, Menu } from "@mui/material";
import { bindMenu, bindTrigger, usePopupState } from "material-ui-popup-state/hooks";
import type { PropsWithChildren } from "react";
import React from "react";

export function FilterMenu({ children, label, count }: PropsWithChildren<{ label: string; count?: number }>): JSX.Element {
    const popupState = usePopupState({ variant: "popper", popupId: label });
    return (
        <>
            <Badge
                badgeContent={count}
                color="primary"
                sx={{
                    ".MuiBadge-anchorOriginTopRight": {
                        marginRight: 0.6,
                        marginTop: 0.4,
                    },
                }}
            >
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
            <Menu
                {...bindMenu(popupState)}
                anchorOrigin={{
                    vertical: "bottom",
                    horizontal: "center",
                }}
                transformOrigin={{
                    vertical: "top",
                    horizontal: "center",
                }}
                onKeyDown={(event) => {
                    // prevent setting focus on search input
                    event.stopPropagation();
                }}
            >
                {children}
            </Menu>
        </>
    );
}
