import { Avatar } from "@mui/material";
import React, { PropsWithChildren } from "react";

export function TableCellAvatar({ children }: PropsWithChildren<unknown>) {
    return (
        <Avatar
            variant="rounded"
            sx={{
                bgcolor: "transparent",
                color: "inherit",
                transition: (theme) =>
                    theme.transitions.create("font-size", {
                        easing: theme.transitions.easing.easeOut,
                        duration: theme.transitions.duration.shorter,
                    }),
                fontSize: "1.5em",
                ".MuiDataGrid-row:hover &": {
                    fontSize: "2em",
                },
                "a:hover &": {
                    fontSize: "2em",
                },
                ".MuiSvgIcon-root": {
                    fontSize: "inherit",
                },
            }}
        >
            {children}
        </Avatar>
    );
}
