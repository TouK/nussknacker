import { Scenario } from "nussknackerUi/components/Process/types";
import React from "react";
import { TableCellAvatar } from "./tableCellAvatar";
import ScanarioIcon from "../../assets/icons/scenario.svg";
import FragmentIcon from "../../assets/icons/fragment.svg";
import { useTranslation } from "react-i18next";

export function ScenarioAvatar({ scenario }: { scenario: Pick<Scenario, "isFragment" | "state"> }) {
    const { t } = useTranslation();
    const { isFragment } = scenario;

    return (
        <TableCellAvatar
            title={isFragment ? t("scenarioAvatar.title.fragment", "Fragment") : t("scenarioAvatar.title.scenario", "Scenario")}
        >
            {isFragment ? <FragmentIcon width={"1em"} height={"1em"} /> : <ScanarioIcon width={"1em"} height={"1em"} />}
        </TableCellAvatar>
    );
}
