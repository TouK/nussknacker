import { renderHook } from "@testing-library/react-hooks";
import { useProcessFormDataOptions } from "./useProcessFormDataOptions";
import { ProcessingMode, ScenarioParametersCombination } from "../http/HttpService";

describe("useProcessFormDataOptions", () => {
    const allCombinations: ScenarioParametersCombination[] = [
        {
            processingMode: ProcessingMode.requestResponse,
            category: "RequestResponse",
            engineSetupName: "Lite Embedded",
        },
        {
            processingMode: ProcessingMode.requestResponse,
            category: "RequestResponseK8s",
            engineSetupName: "Lite K8s",
        },
        {
            processingMode: ProcessingMode.streaming,
            category: "Category1",
            engineSetupName: "Flink",
        },
        {
            processingMode: ProcessingMode.streaming,
            category: "Category2",
            engineSetupName: "Flink",
        },
        {
            processingMode: ProcessingMode.streaming,
            category: "Default",
            engineSetupName: "Flink",
        },
        {
            processingMode: ProcessingMode.streaming,
            category: "DevelopmentTests",
            engineSetupName: "Development Tests",
        },
        {
            processingMode: ProcessingMode.streaming,
            category: "Periodic",
            engineSetupName: "Dev Periodic",
        },
        {
            processingMode: ProcessingMode.streaming,
            category: "StreamingLite",
            engineSetupName: "Lite Embedded",
        },
        {
            processingMode: ProcessingMode.streaming,
            category: "StreamingLiteK8s",
            engineSetupName: "Lite K8s",
        },
    ];

    it("should return unique formOptions based on the form values", () => {
        let value: Record<string, string> = {
            processingMode: "Unbounded-Stream",
            processCategory: "Category1",
            processEngine: "Flink",
        };

        const { result, rerender } = renderHook(() =>
            useProcessFormDataOptions({
                allCombinations,
                value,
            }),
        );

        expect(result.current).toEqual({
            categories: [
                {
                    disabled: true,
                    value: "RequestResponse",
                },
                {
                    disabled: true,
                    value: "RequestResponseK8s",
                },
                {
                    disabled: false,
                    value: "Category1",
                },
                {
                    disabled: false,
                    value: "Category2",
                },
                {
                    disabled: false,
                    value: "Default",
                },
                {
                    disabled: true,
                    value: "DevelopmentTests",
                },
                {
                    disabled: true,
                    value: "Periodic",
                },
                {
                    disabled: true,
                    value: "StreamingLite",
                },
                {
                    disabled: true,
                    value: "StreamingLiteK8s",
                },
            ],
            engines: ["Flink"],
            isCategoryFieldVisible: true,
            isEngineFieldVisible: false,
            isProcessingModeBatchAvailable: false,
            processingModes: ["Unbounded-Stream"],
        });

        value = {
            processingMode: "Unbounded-Stream",
            processCategory: "Category1",
        };

        rerender();

        expect(result.current).toEqual({
            categories: [
                {
                    disabled: true,
                    value: "RequestResponse",
                },
                {
                    disabled: true,
                    value: "RequestResponseK8s",
                },
                {
                    disabled: false,
                    value: "Category1",
                },
                {
                    disabled: false,
                    value: "Category2",
                },
                {
                    disabled: false,
                    value: "Default",
                },
                {
                    disabled: false,
                    value: "DevelopmentTests",
                },
                {
                    disabled: false,
                    value: "Periodic",
                },
                {
                    disabled: false,
                    value: "StreamingLite",
                },
                {
                    disabled: false,
                    value: "StreamingLiteK8s",
                },
            ],
            engines: ["Flink"],
            isCategoryFieldVisible: true,
            isEngineFieldVisible: false,
            isProcessingModeBatchAvailable: false,
            processingModes: ["Unbounded-Stream"],
        });

        value = {
            processCategory: "Category1",
        };

        rerender();

        expect(result.current).toEqual({
            categories: [
                {
                    disabled: false,
                    value: "RequestResponse",
                },
                {
                    disabled: false,
                    value: "RequestResponseK8s",
                },
                {
                    disabled: false,
                    value: "Category1",
                },
                {
                    disabled: false,
                    value: "Category2",
                },
                {
                    disabled: false,
                    value: "Default",
                },
                {
                    disabled: false,
                    value: "DevelopmentTests",
                },
                {
                    disabled: false,
                    value: "Periodic",
                },
                {
                    disabled: false,
                    value: "StreamingLite",
                },
                {
                    disabled: false,
                    value: "StreamingLiteK8s",
                },
            ],
            engines: ["Flink"],
            isCategoryFieldVisible: true,
            isEngineFieldVisible: false,
            isProcessingModeBatchAvailable: false,
            processingModes: ["Unbounded-Stream"],
        });

        value = {};

        rerender();

        expect(result.current).toEqual({
            categories: [
                {
                    disabled: false,
                    value: "RequestResponse",
                },
                {
                    disabled: false,
                    value: "RequestResponseK8s",
                },
                {
                    disabled: false,
                    value: "Category1",
                },
                {
                    disabled: false,
                    value: "Category2",
                },
                {
                    disabled: false,
                    value: "Default",
                },
                {
                    disabled: false,
                    value: "DevelopmentTests",
                },
                {
                    disabled: false,
                    value: "Periodic",
                },
                {
                    disabled: false,
                    value: "StreamingLite",
                },
                {
                    disabled: false,
                    value: "StreamingLiteK8s",
                },
            ],
            engines: ["Lite Embedded", "Lite K8s", "Flink", "Development Tests", "Dev Periodic"],
            isCategoryFieldVisible: true,
            isEngineFieldVisible: false,
            isProcessingModeBatchAvailable: false,
            processingModes: ["Request-Response", "Unbounded-Stream"],
        });

        value = {
            processingMode: "Request-Response",
        };

        rerender();

        expect(result.current).toEqual({
            categories: [
                {
                    disabled: false,
                    value: "RequestResponse",
                },
                {
                    disabled: false,
                    value: "RequestResponseK8s",
                },
                {
                    disabled: true,
                    value: "Category1",
                },
                {
                    disabled: true,
                    value: "Category2",
                },
                {
                    disabled: true,
                    value: "Default",
                },
                {
                    disabled: true,
                    value: "DevelopmentTests",
                },
                {
                    disabled: true,
                    value: "Periodic",
                },
                {
                    disabled: true,
                    value: "StreamingLite",
                },
                {
                    disabled: true,
                    value: "StreamingLiteK8s",
                },
            ],
            engines: ["Lite Embedded", "Lite K8s"],
            isCategoryFieldVisible: true,
            isEngineFieldVisible: false,
            isProcessingModeBatchAvailable: false,
            processingModes: ["Request-Response", "Unbounded-Stream"],
        });
    });

    it("should return isEngineFieldVisible true when for each category and processingMode combination, there is more than one engine", () => {
        const jsonDataOneUnique: ScenarioParametersCombination[] = [
            { processingMode: ProcessingMode.streaming, category: "Category1", engineSetupName: "Engine1" },
            { processingMode: ProcessingMode.streaming, category: "Category2", engineSetupName: "Engine1" },
            { processingMode: ProcessingMode.batch, category: "Category1", engineSetupName: "Engine1" },
            { processingMode: ProcessingMode.batch, category: "Category1", engineSetupName: "Engine2" },
        ];

        const value: Record<string, string> = {
            processingMode: "",
            processCategory: "",
            processEngine: "",
        };

        const { result } = renderHook(() =>
            useProcessFormDataOptions({
                allCombinations: jsonDataOneUnique,
                value,
            }),
        );

        expect(result.current).toEqual({
            categories: [
                {
                    disabled: false,
                    value: "Category1",
                },
                {
                    disabled: false,
                    value: "Category2",
                },
            ],
            engines: ["Engine1", "Engine2"],
            isCategoryFieldVisible: true,
            isEngineFieldVisible: true,
            isProcessingModeBatchAvailable: true,
            processingModes: ["Unbounded-Stream", "Bounded-Stream"],
        });
    });

    it("should return isEngineFieldVisible false when for each category and processingMode combination, there is only one engine", () => {
        const jsonDataOneUnique: ScenarioParametersCombination[] = [
            { processingMode: ProcessingMode.streaming, category: "Category1", engineSetupName: "Engine1" },
            { processingMode: ProcessingMode.streaming, category: "Category2", engineSetupName: "Engine1" },
            { processingMode: ProcessingMode.batch, category: "Category1", engineSetupName: "Engine2" },
        ];

        const value: Record<string, string> = {
            processingMode: "",
            processCategory: "",
            processEngine: "",
        };

        const { result } = renderHook(() =>
            useProcessFormDataOptions({
                allCombinations: jsonDataOneUnique,
                value,
            }),
        );

        expect(result.current).toEqual({
            categories: [
                {
                    disabled: false,
                    value: "Category1",
                },
                {
                    disabled: false,
                    value: "Category2",
                },
            ],
            engines: ["Engine1", "Engine2"],
            isCategoryFieldVisible: true,
            isEngineFieldVisible: false,
            isProcessingModeBatchAvailable: true,
            processingModes: ["Unbounded-Stream", "Bounded-Stream"],
        });
    });

    it("should return isCategoryFieldVisible false when there is only one category available", () => {
        const jsonDataOneUnique: ScenarioParametersCombination[] = [
            { processingMode: ProcessingMode.streaming, category: "Category1", engineSetupName: "Engine1" },
            { processingMode: ProcessingMode.streaming, category: "Category1", engineSetupName: "Engine1" },
            { processingMode: ProcessingMode.batch, category: "Category1", engineSetupName: "Engine2" },
        ];

        const value: Record<string, string> = {
            processingMode: "",
            processCategory: "",
            processEngine: "",
        };

        const { result } = renderHook(() =>
            useProcessFormDataOptions({
                allCombinations: jsonDataOneUnique,
                value,
            }),
        );

        expect(result.current).toEqual({
            categories: [
                {
                    disabled: false,
                    value: "Category1",
                },
            ],
            engines: ["Engine1", "Engine2"],
            isCategoryFieldVisible: false,
            isProcessingModeBatchAvailable: true,
            isEngineFieldVisible: false,

            processingModes: ["Unbounded-Stream", "Bounded-Stream"],
        });
    });
});
