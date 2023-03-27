import "./styles";
import React, { Suspense } from "react";
import ProcessTabs from "./containers/ProcessTabs";
import { QueryClient, QueryClientProvider } from "react-query";
import LoaderSpinner from "./components/Spinner";
import { NkApiProvider } from "./settings/nkApiProvider";
import urljoin from "url-join";
import { BASE_ORIGIN, BASE_PATH } from "nussknackerUi/config";
import i18n from "./i18n";
import { I18nextProvider } from "react-i18next";
import { NkThemeProvider } from "./containers/theme";
import { defaultsDeep } from "lodash";
import { darkTheme } from "./containers/darkTheme";

const queryClient = new QueryClient();

export interface Props {
    addScenario?: () => void;
    addFragment?: () => void;
    onNavigate?: (path: string) => void;
}

export const BASE_HREF = urljoin(BASE_ORIGIN, BASE_PATH);

export default React.memo(function LegacyScenariosNkView({
    addFragment = () => alert("fragment"),
    addScenario = () => alert("scenario"),
}: Props): JSX.Element {
    return (
        <Suspense fallback={<LoaderSpinner show />}>
            <I18nextProvider i18n={i18n as any}>
                <NkApiProvider>
                    <QueryClientProvider client={queryClient}>
                        <NkThemeProvider theme={(outerTheme) => defaultsDeep(darkTheme, outerTheme)}>
                            <ProcessTabs
                                onFragmentAdd={addFragment}
                                onScenarioAdd={addScenario}
                                metricsLinkGetter={(id) => urljoin(BASE_HREF, `/metrics/${id}`)}
                                scenarioLinkGetter={(id) => urljoin(BASE_HREF, `/visualization/${id}`)}
                            />
                        </NkThemeProvider>
                    </QueryClientProvider>
                </NkApiProvider>
            </I18nextProvider>
        </Suspense>
    );
});
