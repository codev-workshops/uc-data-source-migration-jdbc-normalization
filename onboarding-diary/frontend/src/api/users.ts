import api from './axios';
import type { UserSummary } from '../types';

export const getRecruits = async (): Promise<UserSummary[]> => {
  const { data } = await api.get<UserSummary[]>('/users/recruits');
  return data;
};
