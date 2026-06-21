import api from './axios';
import type { AdditionalNote, AdditionalNoteRequest } from '../types';

export const getNotes = async (): Promise<AdditionalNote[]> => {
  const { data } = await api.get<AdditionalNote[]>('/notes');
  return data;
};

export const getNote = async (id: string): Promise<AdditionalNote> => {
  const { data } = await api.get<AdditionalNote>(`/notes/${id}`);
  return data;
};

export const createNote = async (payload: AdditionalNoteRequest): Promise<AdditionalNote> => {
  const { data } = await api.post<AdditionalNote>('/notes', payload);
  return data;
};

export const updateNote = async (id: string, payload: AdditionalNoteRequest): Promise<AdditionalNote> => {
  const { data } = await api.put<AdditionalNote>(`/notes/${id}`, payload);
  return data;
};

export const deleteNote = async (id: string): Promise<void> => {
  await api.delete(`/notes/${id}`);
};
