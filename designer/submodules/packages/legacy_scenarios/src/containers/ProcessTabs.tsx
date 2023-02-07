import { defaultsDeep } from "lodash";
import React, { createContext } from "react";
import HealthCheck from "../components/HealthCheck";
import { ArchiveTabData } from "./Archive";
import { darkTheme } from "./darkTheme";
import { ProcessesTabData } from "./Processes";
import { SubProcessesTabData } from "./SubProcesses";
import { Tabs } from "../components/tabs/Tabs";
import { NkThemeProvider } from "./theme";
import { BrowserRouter } from "react-router-dom";
import { BASE_PATH } from "../config";

interface ProcessTabsProps {
    onScenarioAdd: () => void;
    onFragmentAdd: () => void;
    scenarioLinkGetter: (scenarioId: string) => string;
    metricsLinkGetter: (scenarioId: string) => string;
    basepath?: string;
}

export const ScenariosContext = createContext<ProcessTabsProps>(null);

function ProcessTabs({ basepath = BASE_PATH, ...props }: ProcessTabsProps) {
    return (
        <BrowserRouter basename={basepath}>
            <NkThemeProvider theme={(outerTheme) => defaultsDeep(darkTheme, outerTheme)}>
                <ScenariosContext.Provider value={props}>
                    <Tabs tabs={[ProcessesTabData, SubProcessesTabData, ArchiveTabData]}>
                        <HealthCheck />
                    </Tabs>
                </ScenariosContext.Provider>
            </NkThemeProvider>
        </BrowserRouter>
    );
}

export default ProcessTabs;
