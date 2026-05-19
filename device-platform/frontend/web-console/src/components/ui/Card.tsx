import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/cn";

export function Card({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("card", className)} {...rest} />;
}

export function CardHeader({
  title, description, action, className,
}: { title: ReactNode; description?: ReactNode; action?: ReactNode; className?: string }) {
  return (
    <div className={cn("flex items-start justify-between gap-4 px-5 py-4 border-b border-surface-border", className)}>
      <div className="min-w-0">
        <div className="text-sm font-semibold text-ink-primary">{title}</div>
        {description && <div className="text-xs text-ink-muted mt-0.5">{description}</div>}
      </div>
      {action}
    </div>
  );
}

export function CardBody({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("px-5 py-4", className)} {...rest} />;
}

export function CardFooter({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("px-5 py-3 border-t border-surface-border flex items-center justify-between gap-3", className)} {...rest} />;
}
