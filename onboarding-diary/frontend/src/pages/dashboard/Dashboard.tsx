import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { getSummary, getWeeklyStats } from '../../api/dashboard';
import { getRecruits } from '../../api/users';
import { useAuth } from '../../context/AuthContext';

interface StatCardProps {
  label: string;
  value: number;
  icon: string;
  accent: string;
}

const StatCard = ({ label, value, icon, accent }: StatCardProps) => (
  <div className="rounded-xl bg-white p-5 shadow-sm">
    <div className="flex items-center justify-between">
      <span className="text-sm font-medium text-slate-500">{label}</span>
      <span className={`flex h-9 w-9 items-center justify-center rounded-lg text-lg ${accent}`}>
        {icon}
      </span>
    </div>
    <div className="mt-3 text-3xl font-bold text-slate-800">{value}</div>
  </div>
);

const Dashboard = () => {
  const { role } = useAuth();
  const isManager = role === 'MANAGER' || role === 'ADMIN';

  const summaryQuery = useQuery({ queryKey: ['dashboard-summary'], queryFn: getSummary });
  const weeklyQuery = useQuery({ queryKey: ['dashboard-weekly'], queryFn: getWeeklyStats });
  const recruitsQuery = useQuery({
    queryKey: ['recruits'],
    queryFn: getRecruits,
    enabled: isManager,
  });

  const summary = summaryQuery.data;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">Dashboard</h1>
        {isManager && (
          <select className="rounded-md border border-slate-300 px-3 py-2 text-sm">
            <option value="">All recruits</option>
            {recruitsQuery.data?.map((r) => (
              <option key={r.id} value={r.id}>
                {r.fullName || r.username}
              </option>
            ))}
          </select>
        )}
      </div>

      {summaryQuery.isLoading ? (
        <p className="text-slate-500">Loading summary...</p>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <StatCard label="Total Tasks" value={summary?.totalTasks ?? 0} icon="📋" accent="bg-blue-100" />
          <StatCard label="Completed Tasks" value={summary?.completedTasks ?? 0} icon="✅" accent="bg-green-100" />
          <StatCard label="Open Issues" value={summary?.openIssues ?? 0} icon="🐞" accent="bg-yellow-100" />
          <StatCard label="Resolved Issues" value={summary?.resolvedIssues ?? 0} icon="🛠️" accent="bg-purple-100" />
          <StatCard label="Feedback Notes" value={summary?.feedbackCount ?? 0} icon="💬" accent="bg-pink-100" />
          <StatCard label="Additional Notes" value={summary?.notesCount ?? 0} icon="🗒️" accent="bg-slate-100" />
        </div>
      )}

      <div className="rounded-xl bg-white p-5 shadow-sm">
        <h2 className="mb-4 text-lg font-semibold text-slate-800">Weekly Activity</h2>
        {weeklyQuery.isLoading ? (
          <p className="text-slate-500">Loading chart...</p>
        ) : (weeklyQuery.data?.length ?? 0) === 0 ? (
          <p className="text-slate-500">No activity data yet.</p>
        ) : (
          <ResponsiveContainer width="100%" height={320}>
            <BarChart data={weeklyQuery.data}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="week" fontSize={12} />
              <YAxis allowDecimals={false} fontSize={12} />
              <Tooltip />
              <Legend />
              <Bar dataKey="tasks" fill="#3b82f6" name="Tasks" />
              <Bar dataKey="issues" fill="#f59e0b" name="Issues" />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
};

export default Dashboard;
