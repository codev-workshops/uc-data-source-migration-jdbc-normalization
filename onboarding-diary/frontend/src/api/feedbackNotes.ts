import api from './axios';
import type { FeedbackNote, FeedbackNoteRequest } from '../types';

export const getFeedback = async (): Promise<FeedbackNote[]> => {
  const { data } = await api.get<FeedbackNote[]>('/feedback-notes');
  return data;
};

export const getFeedbackByRecruit = async (recruitId: string): Promise<FeedbackNote[]> => {
  const { data } = await api.get<FeedbackNote[]>('/feedback-notes', { params: { recruitId } });
  return data;
};

export const createFeedback = async (payload: FeedbackNoteRequest): Promise<FeedbackNote> => {
  const { data } = await api.post<FeedbackNote>('/feedback-notes', payload);
  return data;
};

export const updateFeedback = async (id: string, payload: FeedbackNoteRequest): Promise<FeedbackNote> => {
  const { data } = await api.put<FeedbackNote>(`/feedback-notes/${id}`, payload);
  return data;
};

export const deleteFeedback = async (id: string): Promise<void> => {
  await api.delete(`/feedback-notes/${id}`);
};
