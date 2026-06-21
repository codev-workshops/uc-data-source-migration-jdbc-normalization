import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { getWeeklySummary } from '../../api/ai';
import { getRecruits } from '../../api/users';

const AISummary = () => {
  const recruitsQuery = useQuery({ queryKey: ['recruits'], queryFn: getRecruits });
  const [recruitId, setRecruitId] = useState('');
  const [week, setWeek] = useState(1);
  const [summary, setSummary] = useState('');
  const [loading, setLoading] = useState(false);

  const handleGenerate = async () => {
    if (!recruitId) {
      toast.error('Please select a recruit');
      return;
    }
    setLoading(true);
    try {
      const res = await getWeeklySummary(recruitId, week);
      setSummary(res.summary);
    } catch {
      toast.error('Failed to generate summary');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-3xl space-y-4">
      <h1 className="text-2xl font-bold text-slate-800">AI Weekly Summary</h1>
      <div className="space-y-4 rounded-xl bg-white p-6 shadow-sm">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Week</label>
            <input
              type="number"
              min={1}
              value={week}
              onChange={(e) => setWeek(Number(e.target.value))}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>
        <button
          onClick={handleGenerate}
          disabled={loading}
          className="rounded-md bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-60"
        >
          {loading ? 'Generating...' : 'Generate Summary'}
        </button>
      </div>

      {summary && (
        <div className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-2 text-lg font-semibold text-slate-800">Summary</h2>
          <p className="whitespace-pre-wrap text-sm text-slate-700">{summary}</p>
        </div>
      )}
    </div>
  );
};

export default AISummary;
