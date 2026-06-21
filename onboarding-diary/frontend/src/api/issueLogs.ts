import api from './axios';
import type { IssueLog, IssueLogRequest } from '../types';

export const getIssues = async (): Promise<IssueLog[]> => {
  const { data } = await api.get<IssueLog[]>('/issue-logs');
  return data;
};

export const getIssue = async (id: string): Promise<IssueLog> => {
  const { data } = await api.get<IssueLog>(`/issue-logs/${id}`);
  return data;
};

export const createIssue = async (payload: IssueLogRequest): Promise<IssueLog> => {
  const { data } = await api.post<IssueLog>('/issue-logs', payload);
  return data;
};

export const updateIssue = async (id: string, payload: IssueLogRequest): Promise<IssueLog> => {
  const { data } = await api.put<IssueLog>(`/issue-logs/${id}`, payload);
  return data;
};

export const deleteIssue = async (id: string): Promise<void> => {
  await api.delete(`/issue-logs/${id}`);
};
