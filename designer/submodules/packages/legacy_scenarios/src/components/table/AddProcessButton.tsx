import { css } from "@emotion/css";
import React, { useContext } from "react";
import { useTranslation } from "react-i18next";
import { useLoggedUser } from "../../reducers/selectors/settings";
import { ThemedButton } from "../themed/ThemedButton";
import { ScenariosContext } from "../../containers/ProcessTabs";

interface AddButtonProps {
    onClick: () => void;
    className?: string;
    title: string;
}

function AddButton({ className, title, onClick }: AddButtonProps): JSX.Element {
    const loggedUser = useLoggedUser();
    return loggedUser?.isWriter() ? (
        <ThemedButton className={className} onClick={onClick} title={title}>
            <span className={css({ textTransform: "uppercase" })}>{title}</span>
        </ThemedButton>
    ) : null;
}

interface AddProcessButtonProps {
    isFragment?: boolean;
    className?: string;
}

export function AddProcessButton({ isFragment, className }: AddProcessButtonProps): JSX.Element {
    const { t } = useTranslation();
    const { onScenarioAdd, onFragmentAdd } = useContext(ScenariosContext);
    const action = isFragment ? onFragmentAdd : onScenarioAdd;

    return (
        <AddButton
            className={className}
            title={
                isFragment ? t("addProcessButton.fragment", "Create new fragment") : t("addProcessButton.process", "Create new scenario")
            }
            onClick={action}
        />
    );
}
