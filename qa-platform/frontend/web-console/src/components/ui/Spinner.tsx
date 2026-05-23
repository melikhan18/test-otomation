import { cn } from "@/lib/cn";

export function Spinner({ className, size = 16 }: { className?: string; size?: number }) {
  return (
    <span
      role="status"
      aria-label="loading"
      style={{ width: size, height: size }}
      className={cn("inline-block rounded-full border-2 border-current border-r-transparent animate-spin", className)}
    />
  );
}
