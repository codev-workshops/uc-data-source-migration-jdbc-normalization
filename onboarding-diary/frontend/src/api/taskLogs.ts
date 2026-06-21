import api from './axios';
import type { TaskLog, TaskLogRequest } from '../types';

export const getTasks = async (): Promise<TaskLog[]> => {
  const { data } = await api.get<TaskLog[]>('/task-logs');
  return data;
};

export const getTask = async (id: string): Promise<TaskLog> => {
  const { data } = await api.get<TaskLog>(`/task-logs/${id}`);
  return data;
};

export const createTask = async (payload: TaskLogRequest): Promise<TaskLog> => {
  const { data } = await api.post<TaskLog>('/task-logs', payload);
  return data;
};

export const updateTask = async (id: string, payload: TaskLogRequest): Promise<TaskLog> => {
  const { data } = await api.put<TaskLog>(`/task-logs/${id}`, payload);
  return data;
};

export const deleteTask = async (id: string): Promise<void> => {
  await api.delete(`/task-logs/${id}`);
};
