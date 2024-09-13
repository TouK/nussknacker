import { styled } from "@mui/material";
import { InputWithIcon } from "./InputWithIcon";

export const SearchInputWithIcon = styled(InputWithIcon)(({ theme }) => ({
    ...theme.typography.body2,
    width: "75%",
    borderRadius: 0,
    height: "36px !important",
    color: theme.palette.text.secondary,
    padding: "6px 12px !important",
    backgroundColor: `${theme.palette.background.paper} !important`,
    border: "none",
    outline: "none !important",
    "&:focus": {
        boxShadow: "none",
    },
}));
