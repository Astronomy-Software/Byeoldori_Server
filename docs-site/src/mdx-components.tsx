import { openapi } from '@/lib/openapi';
import defaultComponents from 'fumadocs-ui/mdx';
import type { MDXComponents } from 'mdx/types';

export function useMDXComponents(components: MDXComponents): MDXComponents {
  return {
    ...defaultComponents,
    ...openapi.getComponents(),
    ...components,
  };
}
