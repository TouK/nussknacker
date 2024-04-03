import { FormValue } from "./AddProcessForm";
import { useMemo } from "react";
import { every, groupBy, map, some, uniq, uniqBy } from "lodash";
import { ProcessingMode, ScenarioParametersCombination } from "../http/HttpService";

const getFilteredValues = (allCombinations: ScenarioParametersCombination[], value: Partial<FormValue>) => {
    let availableCombinations = allCombinations;
    if (value.processingMode) {
        availableCombinations = groupBy(availableCombinations, "processingMode")[value.processingMode];
    }

    if (value.processCategory) {
        availableCombinations = groupBy(availableCombinations, "category")[value.processCategory];
    }

    if (value.processEngine) {
        availableCombinations = groupBy(availableCombinations, "engineSetupName")[value.processEngine];
    }
    return availableCombinations;
};

interface Props {
    allCombinations: ScenarioParametersCombination[];
    value: Partial<FormValue>;
}

export const useProcessFormDataOptions = ({ allCombinations, value }: Props) => {
    const categories = useMemo(() => {
        const allCategories = uniq(map(getFilteredValues(allCombinations, {}), "category"));
        const availableCategories = uniq(
            map(
                getFilteredValues(allCombinations, {
                    processingMode: value.processingMode,
                    processEngine: value.processEngine,
                }),
                "category",
            ),
        );

        return map(allCategories, (category) => ({
            value: category,
            disabled: every(availableCategories, (availableCategory) => availableCategory !== category),
        }));
    }, [allCombinations, value.processEngine, value.processingMode]);

    const engines = useMemo(() => {
        const filteredValues = getFilteredValues(allCombinations, {
            processingMode: value.processingMode,
            processCategory: value.processCategory,
        });

        return uniq(map(filteredValues, "engineSetupName"));
    }, [allCombinations, value.processCategory, value.processingMode]);

    const processingModes = useMemo(() => {
        const filteredValues = getFilteredValues(allCombinations, {
            processEngine: value.processEngine,
            processCategory: value.processCategory,
        });

        return uniq(map(filteredValues, "processingMode"));
    }, [allCombinations, value.processCategory, value.processEngine]);

    const isEngineFieldVisible = useMemo(() => {
        const groupedCombinations = groupBy(allCombinations, (combination) => `${combination.processingMode}-${combination.category}`);
        const multipleEnginesSelectable = some(groupedCombinations, (group) => uniqBy(group, "engineSetupName").length > 1);

        return multipleEnginesSelectable;
    }, [allCombinations]);

    const isCategoryFieldVisible = true; // QUICK-BUG-FIX categories.length > 1;

    const isProcessingModeBatchAvailable = allCombinations.some((allCombination) => allCombination.processingMode === ProcessingMode.batch);

    return { processingModes, categories, engines, isEngineFieldVisible, isCategoryFieldVisible, isProcessingModeBatchAvailable };
};
