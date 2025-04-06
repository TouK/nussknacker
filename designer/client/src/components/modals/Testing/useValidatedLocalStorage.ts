import { useEffect, useState } from "react";

export function useValidatedLocalStorage<T>(key: string, initialValue: T, validate: (value: any) => boolean) {
    const [state, setState] = useState<T>(() => {
        try {
            const storedValue = localStorage.getItem(key);
            if (storedValue !== null) {
                const parsedValue = JSON.parse(storedValue);
                return validate(parsedValue) ? parsedValue : initialValue;
            }
        } catch (error) {
            console.error("Error reading localStorage:", error);
        }
        return initialValue;
    });

    useEffect(() => {
        localStorage.setItem(key, JSON.stringify(state));
    }, [key, state]);

    return [state, setState] as const;
}
