import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const TopBar = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
      <div className="text-sm text-slate-500">Welcome back</div>
      <div className="flex items-center gap-4">
        <div className="text-right">
          <div className="text-sm font-semibold text-slate-800">
            {user?.fullName || user?.username}
          </div>
          <div className="text-xs uppercase tracking-wide text-slate-400">{user?.role}</div>
        </div>
        <button
          onClick={handleLogout}
          className="rounded-md bg-slate-800 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700"
        >
          Logout
        </button>
      </div>
    </header>
  );
};

export default TopBar;
