import React from "react";
import { useTranslation } from "react-i18next";
import { StyledButton } from "../../../focusableStyled";

export function RemoveButton({ onClick }: { onClick: () => void }): JSX.Element {
    const { t } = useTranslation();
    return (
        <StyledButton title={t("node.row.remove.title", "Remove field")} onClick={onClick}>
            {t("node.row.remove.text", "-")}
        </StyledButton>
    );
}
