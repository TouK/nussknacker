import React, { useCallback, useMemo } from "react";
import { Chip, styled } from "@mui/material";
import { useTranslation } from "react-i18next";

interface Props {
    id: string;
    value: string;
    filterValue: string[];
    setFilter: (value: string[]) => void;
}

const StyledLabelChip = styled(Chip)({
    borderRadius: "16px",
});

export function LabelChip({ id, value, filterValue, setFilter }: Props): JSX.Element {
    const { t } = useTranslation();
    const isSelected = useMemo(() => filterValue.includes(value), [filterValue, value]);

    const onClick = useCallback(
        (e) => {
            setFilter(isSelected ? filterValue.filter((v) => v !== value) : [...filterValue, value]);
            e.preventDefault();
            e.stopPropagation();
        },
        [setFilter, isSelected, filterValue, value],
    );

    return (
        <StyledLabelChip
            title={t("scenariosList.tooltip.label", "Label")}
            key={id}
            color={isSelected ? "primary" : "default"}
            size="small"
            variant={"outlined"}
            label={value}
            tabIndex={0}
            onClick={onClick}
        />
    );
}
