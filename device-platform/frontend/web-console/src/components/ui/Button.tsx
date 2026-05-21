import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/lib/cn";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  leftIcon?: ReactNode;
  rightIcon?: ReactNode;
  loading?: boolean;
}

const variants: Record<Variant, string> = {
  primary:
    "bg-brand-500 hover:bg-brand-400 active:bg-brand-600 text-white border-brand-500 " +
    "shadow-[0_1px_0_0_rgba(255,255,255,0.15)_inset]",
  secondary:
    "bg-surface-raised hover:bg-surface-muted text-ink-primary border-surface-border",
  ghost:
    "bg-transparent hover:bg-surface-muted text-ink-secondary hover:text-ink-primary border-transparent",
  danger:
    "bg-danger-500/15 hover:bg-danger-500/25 text-danger-500 border-danger-500/30",
};

const sizes: Record<Size, string> = {
  sm: "h-8 px-3 text-xs gap-1.5",
  md: "h-9 px-4 text-sm gap-2",
  lg: "h-11 px-5 text-sm gap-2",
};

export const Button = forwardRef<HTMLButtonElement, Props>(function Button(
  { variant = "secondary", size = "md", leftIcon, rightIcon, loading, className, children, disabled, ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={cn(
        "inline-flex items-center justify-center font-medium rounded-md border",
        // Smooth color + subtle press feedback. Scale stays GPU-cheap.
        "transition-[background-color,border-color,color,transform,box-shadow] duration-150 ease-out",
        "active:scale-[0.97] motion-reduce:active:scale-100",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/40",
        "disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100",
        variants[variant],
        sizes[size],
        className,
      )}
      {...rest}
    >
      {loading ? (
        <span className="h-3 w-3 rounded-full border-2 border-current border-r-transparent animate-spin" />
      ) : leftIcon}
      {children && <span>{children}</span>}
      {!loading && rightIcon}
    </button>
  );
});
