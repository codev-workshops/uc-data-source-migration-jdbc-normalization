import api from './axios';
import type { SearchRequest, SearchResult, WeeklySummaryResponse } from '../types';

export const getWeeklySummary = async (
  recruitId: string,
  week: number,
): Promise<WeeklySummaryResponse> => {
  const { data } = await api.get<WeeklySummaryResponse>('/ai/weekly-summary', {
    params: { recruitId, week },
  });
  return data;
};

export const semanticSearch = async (payload: SearchRequest): Promise<SearchResult[]> => {
  const { data } = await api.post<SearchResult[]>('/ai/semantic-search', payload);
  return data;
};
