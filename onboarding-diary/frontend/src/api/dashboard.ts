import api from './axios';
import type { DashboardSummary, WeeklyStat } from '../types';

export const getSummary = async (): Promise<DashboardSummary> => {
  const { data } = await api.get<DashboardSummary>('/dashboard/summary');
  return data;
};

export const getWeeklyStats = async (): Promise<WeeklyStat[]> => {
  const { data } = await api.get<WeeklyStat[]>('/dashboard/weekly-stats');
  return data;
};
