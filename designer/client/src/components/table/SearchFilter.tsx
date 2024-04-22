import { css, cx } from "@emotion/css";
import React from "react";
import SearchSvg from "../../assets/img/search.svg";
import DeleteSvg from "../../assets/img/toolbarButtons/delete.svg";
import { useTheme } from "@mui/material";

const flex = css({
    width: 0, // edge 18. why? because! 🙃
    flex: 1,
});

export function SearchIcon(props: { isEmpty?: boolean }): JSX.Element {
    const theme = useTheme();
    return (
        <SearchSvg
            className={cx(
                flex,
                css({
                    ".icon-fill": {
                        fill: props.isEmpty ? theme.palette.text.secondary : theme.palette.primary.main,
                    },
                }),
            )}
        />
    );
}

export function ClearIcon(): JSX.Element {
    const theme = useTheme();

    return (
        <DeleteSvg
            className={cx(
                flex,
                css({
                    path: {
                        fill: theme.palette.text.secondary,
                    },
                }),
            )}
        />
    );
}
