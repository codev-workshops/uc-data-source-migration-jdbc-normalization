import api from './axios';
import type { AuthResponse, CurrentUser, LoginRequest, RegisterRequest } from '../types';

export const login = async (payload: LoginRequest): Promise<AuthResponse> => {
  const { data } = await api.post<AuthResponse>('/auth/login', payload);
  return data;
};

export const register = async (payload: RegisterRequest): Promise<AuthResponse> => {
  const { data } = await api.post<AuthResponse>('/auth/register', payload);
  return data;
};

export const getMe = async (): Promise<CurrentUser> => {
  const { data } = await api.get<CurrentUser>('/auth/me');
  return data;
};
