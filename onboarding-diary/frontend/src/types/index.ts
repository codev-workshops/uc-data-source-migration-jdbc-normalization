export type Role = 'RECRUIT' | 'MANAGER' | 'ADMIN';
export type TaskStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH';
export type IssueSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type IssueStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type SourceType = 'TASK' | 'ISSUE' | 'FEEDBACK' | 'NOTE';

export interface AuthResponse {
  token: string;
  username: string;
  role: Role;
  fullName: string;
}

export interface CurrentUser {
  id: string;
  username: string;
  email: string;
  role: Role;
  fullName: string;
}

export interface UserSummary {
  id: string;
  username: string;
  email: string;
  role: Role;
  fullName: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  fullName?: string;
}

export interface TaskLog {
  id: string;
  userId: string;
  title: string;
  description?: string;
  status: TaskStatus;
  priority: Priority;
  dueDate?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TaskLogRequest {
  title: string;
  description?: string;
  status?: TaskStatus;
  priority?: Priority;
  dueDate?: string;
}

export interface IssueLog {
  id: string;
  userId: string;
  title: string;
  description?: string;
  severity: IssueSeverity;
  status: IssueStatus;
  resolution?: string;
  createdAt: string;
  updatedAt: string;
}

export interface IssueLogRequest {
  title: string;
  description?: string;
  severity?: IssueSeverity;
  status?: IssueStatus;
  resolution?: string;
}

export interface FeedbackNote {
  id: string;
  recruitId: string;
  recruitName?: string;
  managerId: string;
  managerName?: string;
  content: string;
  week?: number;
  createdAt: string;
  updatedAt: string;
}

export interface FeedbackNoteRequest {
  recruitId: string;
  content: string;
  week?: number;
}

export interface AdditionalNote {
  id: string;
  userId: string;
  title?: string;
  content?: string;
  category?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdditionalNoteRequest {
  title?: string;
  content?: string;
  category?: string;
}

export interface DashboardSummary {
  totalTasks: number;
  completedTasks: number;
  openIssues: number;
  resolvedIssues: number;
  feedbackCount: number;
  notesCount: number;
}

export interface WeeklyStat {
  week: string;
  tasks: number;
  issues: number;
}

export interface SearchRequest {
  query: string;
  sourceTypes: SourceType[];
}

export interface SearchResult {
  sourceType: SourceType;
  sourceId: string;
  content: string;
  score: number;
}

export interface WeeklySummaryResponse {
  recruitId: string;
  week: number;
  summary: string;
}
