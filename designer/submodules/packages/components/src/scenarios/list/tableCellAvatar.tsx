import { Avatar } from "@mui/material";
import React, { PropsWithChildren } from "react";

export function TableCellAvatar({ children, title }: PropsWithChildren<{ title?: string }>) {
    return (
        <Avatar
            variant="rounded"
            sx={{
                color: "inherit",
                bgcolor: "transparent",
                transition: (theme) =>
                    theme.transitions.create("font-size", {
                        easing: theme.transitions.easing.easeOut,
                        duration: theme.transitions.duration.shorter,
                    }),
                fontSize: "1em",
                ".MuiDataGrid-row:hover &": {
                    fontSize: "1.25em",
                },
                "a:hover &": {
                    fontSize: "1.25em",
                },
                ".MuiSvgIcon-root": {
                    fontSize: "1em",
                },
            }}
            title={title}
        >
            {children}
        </Avatar>
    );
}
