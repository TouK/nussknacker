import React from "react";
import { useTranslation } from "react-i18next";
import { ProcessStateType } from "./types";

export function Errors({ state }: { state: ProcessStateType }) {
    const { t } = useTranslation();

    if (state.errors?.length < 1) {
        return null;
    }

    return (
        <div>
            <span>{t("stateIcon.errors", "Errors:")}</span>
            <ul>
                {state.errors.map((error, key) => (
                    <li key={key}>{error}</li>
                ))}
            </ul>
        </div>
    );
}
