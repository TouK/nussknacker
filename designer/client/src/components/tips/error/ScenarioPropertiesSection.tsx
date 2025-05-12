import i18next from "i18next";
import React from "react";
import { useTranslation } from "react-i18next";
import { NavLink } from "react-router-dom";

import { useOpenProperties } from "../../toolbars/scenarioActions/buttons/PropertiesButton";
import { ErrorHeader } from "./ErrorHeader";
import { ErrorLinkStyle } from "./styled";

export const ScenarioPropertiesSection = () => {
    const { t } = useTranslation();
    const openProperties = useOpenProperties();
    return (
        <>
            <ErrorHeader message={i18next.t("errors.errorsIn", "Errors in: ")} />
            <ErrorLinkStyle variant={"body2"} component={NavLink} onClick={openProperties}>
                {t("errors.propertiesText", "properties")}
            </ErrorLinkStyle>
        </>
    );
};
