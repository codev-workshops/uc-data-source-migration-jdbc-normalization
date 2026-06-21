import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import type { Role } from '../types';

interface NavItem {
  to: string;
  label: string;
  roles?: Role[];
}

const navItems: NavItem[] = [
  { to: '/', label: 'Dashboard' },
  { to: '/tasks', label: 'Tasks' },
  { to: '/issues', label: 'Issues' },
  { to: '/feedback', label: 'Feedback' },
  { to: '/notes', label: 'Notes' },
  { to: '/reports', label: 'Reports', roles: ['MANAGER', 'ADMIN'] },
  { to: '/ai-summary', label: 'AI Summary', roles: ['MANAGER', 'ADMIN'] },
  { to: '/search', label: 'Search' },
];

const Sidebar = () => {
  const { role } = useAuth();

  const visibleItems = navItems.filter(
    (item) => !item.roles || (role && item.roles.includes(role)),
  );

  return (
    <aside className="flex w-60 flex-col bg-slate-900 text-slate-100">
      <div className="px-6 py-5 text-lg font-bold tracking-tight">Onboarding Diary</div>
      <nav className="flex-1 space-y-1 px-3">
        {visibleItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              `block rounded-md px-3 py-2 text-sm font-medium transition ${
                isActive ? 'bg-slate-700 text-white' : 'text-slate-300 hover:bg-slate-800'
              }`
            }
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
};

export default Sidebar;
