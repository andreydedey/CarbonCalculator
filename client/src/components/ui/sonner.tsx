import {
  CircleCheckIcon,
  InfoIcon,
  Loader2Icon,
  OctagonXIcon,
  TriangleAlertIcon,
} from 'lucide-react'
import { Toaster as Sonner, type ToasterProps } from 'sonner'

const Toaster = ({ ...props }: ToasterProps) => (
  <Sonner
    theme="light"
    className="toaster group"
    icons={{
      success: <CircleCheckIcon className="size-4" />,
      info: <InfoIcon className="size-4" />,
      warning: <TriangleAlertIcon className="size-4" />,
      error: <OctagonXIcon className="size-4" />,
      loading: <Loader2Icon className="size-4 animate-spin" />,
    }}
    style={
      {
        '--normal-bg': 'var(--popover)',
        '--normal-text': 'var(--popover-foreground)',
        '--normal-border': 'var(--border)',
        '--border-radius': 'var(--radius)',
        '--success-bg': 'color-mix(in oklab, var(--primary) 12%, white)',
        '--success-text': 'var(--primary)',
        '--success-border': 'color-mix(in oklab, var(--primary) 25%, transparent)',
        '--error-bg': 'color-mix(in oklab, var(--destructive) 12%, white)',
        '--error-text': 'var(--destructive)',
        '--error-border': 'color-mix(in oklab, var(--destructive) 25%, transparent)',
        '--warning-bg': 'color-mix(in oklab, var(--warning) 12%, white)',
        '--warning-text': 'var(--warning)',
        '--warning-border': 'color-mix(in oklab, var(--warning) 25%, transparent)',
      } as React.CSSProperties
    }
    toastOptions={{
      classNames: {
        toast: 'cn-toast',
      },
    }}
    {...props}
  />
)

export { Toaster }
