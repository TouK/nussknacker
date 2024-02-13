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
            categories: ["Category1", "Category2", "Default"],
            engines: ["Flink"],
            processingModes: ["Unbounded-Stream"],
        });

        value = {
            processingMode: "Unbounded-Stream",
            processCategory: "Category1",
        };

        rerender();

        expect(result.current).toEqual({
            categories: ["Category1", "Category2", "Default", "DevelopmentTests", "Periodic", "StreamingLite", "StreamingLiteK8s"],
            engines: ["Flink"],
            processingModes: ["Unbounded-Stream"],
        });

        value = {
            processCategory: "Category1",
        };

        rerender();

        expect(result.current).toEqual({
            categories: [
                "RequestResponse",
                "RequestResponseK8s",
                "Category1",
                "Category2",
                "Default",
                "DevelopmentTests",
                "Periodic",
                "StreamingLite",
                "StreamingLiteK8s",
            ],
            engines: ["Flink"],
            processingModes: ["Unbounded-Stream"],
        });

        value = {};

        rerender();

        expect(result.current).toEqual({
            categories: [
                "RequestResponse",
                "RequestResponseK8s",
                "Category1",
                "Category2",
                "Default",
                "DevelopmentTests",
                "Periodic",
                "StreamingLite",
                "StreamingLiteK8s",
            ],
            engines: ["Lite Embedded", "Lite K8s", "Flink", "Development Tests", "Dev Periodic"],
            processingModes: ["Request-Response", "Unbounded-Stream"],
        });

        value = {
            processingMode: "Request-Response",
        };

        rerender();

        expect(result.current).toEqual({
            categories: ["RequestResponse", "RequestResponseK8s"],
            engines: ["Lite Embedded", "Lite K8s"],
            processingModes: ["Request-Response", "Unbounded-Stream"],
        });
    });
});
