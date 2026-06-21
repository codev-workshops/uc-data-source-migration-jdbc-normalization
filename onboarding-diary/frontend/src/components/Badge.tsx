interface BadgeProps {
  label: string;
  color?: 'gray' | 'blue' | 'green' | 'yellow' | 'red' | 'purple';
}

const colorMap: Record<NonNullable<BadgeProps['color']>, string> = {
  gray: 'bg-slate-100 text-slate-700',
  blue: 'bg-blue-100 text-blue-700',
  green: 'bg-green-100 text-green-700',
  yellow: 'bg-yellow-100 text-yellow-800',
  red: 'bg-red-100 text-red-700',
  purple: 'bg-purple-100 text-purple-700',
};

const Badge = ({ label, color = 'gray' }: BadgeProps) => (
  <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${colorMap[color]}`}>
    {label}
  </span>
);

export default Badge;
