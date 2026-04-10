import type { SelectProps } from "@mui/material";
import type { Theme } from "@mui/material";

import { getBorderColor } from "../../../../../../../containers/theme/helpers";

export const nuSelectSx = (theme: Theme) => ({
    fontSize: "0.85rem",
    borderRadius: 0,
    backgroundColor: "background.paper",
    height: 35,
    "& .MuiOutlinedInput-notchedOutline": { border: "none" },
    "&:hover .MuiOutlinedInput-notchedOutline": { border: "none" },
    "&.Mui-focused .MuiOutlinedInput-notchedOutline": { border: "none" },
    outline: `1px solid ${getBorderColor(theme)}`,
    "&.Mui-focused": { outline: `1px solid ${theme.palette.primary.main}` },
    "& .MuiSelect-select": { py: "6px !important" },
});

export const nuMenuProps: SelectProps["MenuProps"] = {
    PaperProps: {
        elevation: 0,
        sx: (theme) => ({
            backgroundColor: "background.paper",
            backgroundImage: "none",
            borderRadius: 0,
            boxShadow: "none",
            outline: `1px solid ${getBorderColor(theme as Theme)}`,
            "& .MuiMenuItem-root": {
                fontSize: "0.85rem",
                "&:hover": { backgroundColor: "action.hover" },
                "&.Mui-selected": { backgroundColor: "action.selected" },
            },
        }),
    },
};
