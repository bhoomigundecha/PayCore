import { clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** Join class names and let later Tailwind classes override earlier ones. */
export const cn = (...inputs) => twMerge(clsx(inputs))
