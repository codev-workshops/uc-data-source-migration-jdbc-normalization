import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import * as authApi from '../api/auth';
import type { CurrentUser, LoginRequest, RegisterRequest, Role } from '../types';

interface AuthContextValue {
  user: CurrentUser | null;
  role: Role | null;
  token: string | null;
  loading: boolean;
  login: (payload: LoginRequest) => Promise<void>;
  register: (payload: RegisterRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [token, setToken] = useState<string | null>(localStorage.getItem('token'));
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const restore = async () => {
      const stored = localStorage.getItem('token');
      if (!stored) {
        setLoading(false);
        return;
      }
      try {
        const me = await authApi.getMe();
        setUser(me);
        setToken(stored);
      } catch {
        localStorage.removeItem('token');
        setToken(null);
        setUser(null);
      } finally {
        setLoading(false);
      }
    };
    restore();
  }, []);

  const login = async (payload: LoginRequest) => {
    const res = await authApi.login(payload);
    localStorage.setItem('token', res.token);
    setToken(res.token);
    const me = await authApi.getMe();
    setUser(me);
  };

  const register = async (payload: RegisterRequest) => {
    const res = await authApi.register(payload);
    localStorage.setItem('token', res.token);
    setToken(res.token);
    const me = await authApi.getMe();
    setUser(me);
  };

  const logout = () => {
    localStorage.removeItem('token');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{ user, role: user?.role ?? null, token, loading, login, register, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = (): AuthContextValue => {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
};
