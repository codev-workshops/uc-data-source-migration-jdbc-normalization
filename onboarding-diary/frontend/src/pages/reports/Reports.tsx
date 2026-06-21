import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { downloadCsv, downloadPdf } from '../../api/reports';
import { getRecruits } from '../../api/users';
import { useAuth } from '../../context/AuthContext';

const Reports = () => {
  const { user, role } = useAuth();
  const isManager = role === 'MANAGER' || role === 'ADMIN';
  const recruitsQuery = useQuery({
    queryKey: ['recruits'],
    queryFn: getRecruits,
    enabled: isManager,
  });

  const [format, setFormat] = useState<'pdf' | 'csv'>('pdf');
  const [csvType, setCsvType] = useState('tasks');
  const [recruitId, setRecruitId] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [loading, setLoading] = useState(false);

  const resolvedRecruitId = isManager ? recruitId : user?.id ?? '';

  const handleGenerate = async () => {
    if (!resolvedRecruitId) {
      toast.error('Please select a recruit');
      return;
    }
    setLoading(true);
    try {
      if (format === 'pdf') {
        await downloadPdf(resolvedRecruitId, startDate || undefined, endDate || undefined);
      } else {
        await downloadCsv(resolvedRecruitId, csvType, startDate || undefined, endDate || undefined);
      }
      toast.success('Report generated');
    } catch {
      toast.error('Failed to generate report');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-2xl space-y-4">
      <h1 className="text-2xl font-bold text-slate-800">Reports</h1>
      <div className="space-y-4 rounded-xl bg-white p-6 shadow-sm">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Format</label>
            <select
              value={format}
              onChange={(e) => setFormat(e.target.value as 'pdf' | 'csv')}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="pdf">PDF</option>
              <option value="csv">CSV</option>
            </select>
          </div>
          {format === 'csv' && (
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Data scope</label>
              <select
                value={csvType}
                onChange={(e) => setCsvType(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="tasks">Tasks</option>
                <option value="issues">Issues</option>
                <option value="feedback">Feedback</option>
                <option value="notes">Notes</option>
              </select>
            </div>
          )}
        </div>

        {isManager && (
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Recruit</label>
            <select
              value={recruitId}
              onChange={(e) => setRecruitId(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">Select recruit...</option>
              {recruitsQuery.data?.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.fullName || r.username}
                </option>
              ))}
            </select>
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Start date</label>
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">End date</label>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>

        <button
          onClick={handleGenerate}
          disabled={loading}
          className="rounded-md bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-60"
        >
          {loading ? 'Generating...' : 'Generate & Download'}
        </button>
      </div>
    </div>
  );
};

export default Reports;
