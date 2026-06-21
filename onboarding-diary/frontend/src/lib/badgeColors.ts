import type { IssueSeverity, IssueStatus, Priority, TaskStatus } from '../types';

type Color = 'gray' | 'blue' | 'green' | 'yellow' | 'red' | 'purple';

export const taskStatusColor = (status: TaskStatus): Color => {
  switch (status) {
    case 'COMPLETED':
      return 'green';
    case 'IN_PROGRESS':
      return 'blue';
    default:
      return 'gray';
  }
};

export const priorityColor = (priority: Priority): Color => {
  switch (priority) {
    case 'HIGH':
      return 'red';
    case 'MEDIUM':
      return 'yellow';
    default:
      return 'gray';
  }
};

export const severityColor = (severity: IssueSeverity): Color => {
  switch (severity) {
    case 'CRITICAL':
      return 'purple';
    case 'HIGH':
      return 'red';
    case 'MEDIUM':
      return 'yellow';
    default:
      return 'gray';
  }
};

export const issueStatusColor = (status: IssueStatus): Color => {
  switch (status) {
    case 'RESOLVED':
      return 'green';
    case 'IN_PROGRESS':
      return 'blue';
    case 'CLOSED':
      return 'gray';
    default:
      return 'yellow';
  }
};
