import { useEffect, useState } from "react";

import HttpService from "../http/instance";
import type { ProcessingMode, ScenarioParametersCombination } from "../http/types";
import { useProcessFormDataOptions } from "./useProcessFormDataOptions";

interface Props {
    processCategory: string;
    processingMode: ProcessingMode;
    processEngine: string;
}
export const useGetAllCombinations = ({ processCategory, processingMode, processEngine }: Props) => {
    const [allCombinations, setAllCombinations] = useState<ScenarioParametersCombination[]>([]);
    const [engineSetupErrors, setEngineSetupErrors] = useState<Record<string, string[]>>({});
    const [isAllCombinationsLoading, setIsAllCombinationsLoading] = useState<boolean>(false);

    const { isCategoryFieldVisible } = useProcessFormDataOptions({
        allCombinations,
        value: {
            processCategory,
            processingMode,
            processEngine,
        },
    });

    useEffect(() => {
        setIsAllCombinationsLoading(true);
        HttpService.fetchScenarioParametersCombinations()
            .then((response) => {
                setAllCombinations(response.data.combinations);
                setEngineSetupErrors(response.data.engineSetupErrors);
            })
            .finally(() => {
                setIsAllCombinationsLoading(false);
            });
    }, []);

    return { allCombinations, isAllCombinationsLoading, isCategoryFieldVisible, engineSetupErrors };
};
