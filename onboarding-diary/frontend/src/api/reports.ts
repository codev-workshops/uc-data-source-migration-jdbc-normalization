import api from './axios';

const triggerDownload = (data: Blob, filename: string) => {
  const url = window.URL.createObjectURL(data);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

export const downloadPdf = async (
  recruitId: string,
  startDate?: string,
  endDate?: string,
): Promise<void> => {
  const { data } = await api.get('/reports/pdf', {
    params: { recruitId, startDate, endDate },
    responseType: 'blob',
  });
  triggerDownload(data, 'onboarding-report.pdf');
};

export const downloadCsv = async (
  recruitId: string,
  type: string,
  startDate?: string,
  endDate?: string,
): Promise<void> => {
  const { data } = await api.get('/reports/csv', {
    params: { recruitId, type, startDate, endDate },
    responseType: 'blob',
  });
  triggerDownload(data, `onboarding-${type}.csv`);
};
