import { css, cx } from "@emotion/css";
import React from "react";
import SearchSvg from "../../assets/img/search.svg";
import AdvancedSearchSvg from "../../assets/img/advanced-search.svg";
import DeleteSvg from "../../assets/img/toolbarButtons/delete.svg";
import { useTheme } from "@mui/material";

const flex = css({
    width: 0, // edge 18. why? because! 🙃
    flex: 1,
});

export function AdvancedOptionsIcon(props: {
    isActive?: boolean;
    collapseHandler: React.Dispatch<React.SetStateAction<boolean>>;
}): JSX.Element {
    const theme = useTheme();

    const toggleCollapseHandler = () => {
        props.collapseHandler((p) => !p);
    };

    return (
        <AdvancedSearchSvg
            onClick={toggleCollapseHandler}
            className={cx(
                flex,
                css({
                    transform: "scale(1.5)",
                    ".icon-fill": {
                        fill: "none",
                        stroke: !props.isActive ? theme.palette.text.secondary : theme.palette.primary.main,
                    },
                }),
            )}
        />
    );
}

export function SearchIcon(props: { isEmpty?: boolean }): JSX.Element {
    const theme = useTheme();
    return (
        <SearchSvg
            className={cx(
                flex,
                css({
                    transform: "scale(0.8)",
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
