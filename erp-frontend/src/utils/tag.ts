import type { TagProps } from 'element-plus'

export function normalizeTagType(value: unknown): TagProps['type'] {
  switch (value) {
    case 'success':
    case 'info':
    case 'warning':
    case 'danger':
      return value
    default:
      return 'primary'
  }
}
