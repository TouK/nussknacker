import {AxiosResponse} from "axios"
import {useCallback, useEffect, useRef, useState} from "react"

const useIsMounted = () => {
  const isMounted = useRef(false)
  useEffect(() => {
    isMounted.current = true
    return () => {isMounted.current = false}
  }, [])
  return isMounted
}

export function useFetch<T>(fetchAction: () => Promise<AxiosResponse<T>>, initValue?: T): [T, () => Promise<void>, boolean] {
  const [isLoading, setIsLoading] = useState<boolean>(false)
  const [data, setData] = useState<T>(initValue)
  const isMounted = useIsMounted()

  const getData = useCallback(
    async () => {
      setIsLoading(true)
      try {
        const response = await fetchAction()
        if (response && isMounted.current) {
          setData(response.data)
        }
      } catch (error) {
      } finally {
        if (isMounted.current) {
          setIsLoading(false)
        }
      }
    },
    [fetchAction, isMounted],
  )

  return [data, getData, isLoading]
}
